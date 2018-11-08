package org.jboss.cache.loader.deadlock;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.UserTransaction;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * <b>Test based on a contribution by Marian Nokolov/Paul Miodonski at Siemens AG.</b>
 * <p/>
 * This test has been created to simulate a unexpected TimeoutException that is
 * thrown by the JBossCache. The original scenario that has been observed: <br>
 * Cache in either LOCAL or REPL_SYNC mode with CacheLoader.
 * <ul>
 * <li>1. Concurrent threads A, B and C, each associated with a transaction.
 * Threads A and B try to modify FQN X, thread C tries to modify FQN Y.</li>
 * <li>2. Thread A locks X.</li>
 * <li>3. Thread B blocks on X (correct since A is the active writer).</li>
 * <li>4. Thread A tries to do multiple modifications and suddenly blocks on X
 * (although it is the active writer already) - this is the 1-st problem.</li>
 * <li>5. Thread C blocks somewhere as well, although it has nothing to do with
 * X and is the only one that works on Y - this is a 2-nd problem.</li>
 * <li>6. Thread B fails with TimeoutException and its transaction is rolled
 * back - this is correct given that A is still holding the lock on X.</li>
 * <li>7. Thread A continues its job and its transaction completes successfully
 * (after unexpected locking timeout delay).</li>
 * <li>8. Thread C continues its job and successfully commits the transaction
 * (with unexpected locking timeout delay).</li>
 * </ul>
 * <br>
 * There are two problems with this:
 * <ul>
 * <li>1. One or more concurrent transactions fail, although the pessimistic
 * locking should sequentialize them and guarantee that all should succeed.</li>
 * <li>2. Any other thread that tries to acquire a lock in the cache (even for
 * a different FQN!?) is blocked for the duration of the locking timeout, i.e.
 * the whole application is locked for few seconds. The active writer for the
 * corresponding FQN is blocked as well in the middle of the transaction!</li>
 * </ul>
 * <br>
 * At least until now, the error can be reproduced only if the following is
 * true:
 * <ul>
 * <li>Concurrent transactions forcing creation of the same FQN at the same
 * time.</li>
 * <li>More than one update per TX per FQN - trying to acquire lock on the same
 * FQN multiple times per TX.</li>
 * <li>Cache with CacheLoader - maybe it has something to do with the
 * CacheLoader/StoreInterceptor's...</li>
 * </ul>
 */
@Test(groups = {"functional"}, enabled = false, testName = "loader.deadlock.ConcurrentCreationDeadlockTest")
// Disabling since this has issues with ReadWriteWithUpgradeLock.  See JBCACHE-461
public class ConcurrentCreationDeadlockTest
{
   /**
    * The number of worker threads to start concurrently.
    */
   private static final int NUM_WORKERS = 10;
   /**
    * The number of test runs to perform.
    */
   private static final int NUM_RUNS = 100;
   /**
    * The number of FQN's per test run.
    */
   private static final int NUM_FQNS_PER_RUN = 10;

   /**
    * The initial context factory properties.
    */
   private static final Properties PROPERTIES;
   /**
    * The context factory to be used for the test.
    */
   private static final String CONTEXT_FACTORY =
         "org.jboss.cache.transaction.DummyContextFactory";
   /**
    * The original context factory to be restored after the test.
    */
   // private String m_contextFactory = null;

   /**
    * Exception recorded if any of the worker threads fails.
    */
   private static volatile Exception mcl_exception = null;

   /**
    * The cache under test.
    */
   private CacheSPI<Object, Object> cache = null;

   static
   {
      PROPERTIES = new Properties();
      PROPERTIES.put(Context.INITIAL_CONTEXT_FACTORY,
            "org.jboss.cache.transaction.DummyContextFactory");
   }

   /**
    * {@inheritDoc}
    */
   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      mcl_exception = null;
      //m_contextFactory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, CONTEXT_FACTORY);

      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
   }

   /**
    * {@inheritDoc}
    */
   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {      
      // We just can't destroy DummyTransactionManager. We are sharing single instance in more tests. 
      TestingUtil.killTransaction(DummyTransactionManager.getInstance());
      TestingUtil.killCaches(cache);
      cache = null;
      /*
      if (m_contextFactory != null)
      {
         System.setProperty(Context.INITIAL_CONTEXT_FACTORY,
               m_contextFactory);
         m_contextFactory = null;
      }
      */
   }

   /**
    * Initializes and starts the cache.
    *
    * @param cacheMode        The cache mode.
    * @param cacheLoaderClass The name of the cache loader class.
    * @throws Exception Any exception if thrown by the cache.
    */
   private void startCache(Configuration.CacheMode cacheMode, String cacheLoaderClass)
         throws Exception
   {
      cache.getConfiguration().setCacheMode(cacheMode);
      if (cacheLoaderClass != null)
      {
         cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", cacheLoaderClass,
               "", false, false, false, false, false));
      }
      cache.getConfiguration().setLockAcquisitionTimeout(600000);
      cache.start();
   }


   /**
    * Test for a local cache with cache loader and two modifications per
    * transaction.<br>
    * This test does very often fail with a TimeoutException.
    *
    * @throws Exception Any exception if thrown by the cache.
    */
   public void testLocalCacheLoader2Modifications() throws Exception
   {
      startCache(Configuration.CacheMode.LOCAL, DummyInMemoryCacheLoader.class.getName());
      performTest(2);
   }

   /**
    * Test for a synchronously replicated cache with cache loader and two
    * modifications per transaction.<br>
    * This test fails very often with a TimeoutException.
    *
    * @throws Exception Any exception if thrown by the cache.
    */
   public void testReplSyncCacheLoader2Modifications()
         throws Exception
   {
      startCache(Configuration.CacheMode.REPL_SYNC, DummyInMemoryCacheLoader.class.getName());
      performTest(2);
   }

   /**
    * Perform a single test, using the pre-configured cache.
    *
    * @param modificationsPerTx The number of modifications per transaction.
    * @throws Exception Any exception if thrown by the cache.
    */
   private void performTest(int modificationsPerTx) throws Exception
   {
      for (int i = 0; i < NUM_RUNS; i++)
      {
         if (mcl_exception != null)
         {
            // terminate the test on the first failed worker
            throw mcl_exception;
         }
         // start several worker threads to work with the same set of FQN's
         Worker[] t = new Worker[NUM_WORKERS];
         CountDownLatch latch = new CountDownLatch(1);
         for (int j = 0; j < t.length; j++)
         {
            t[j] = new Worker(latch, NUM_FQNS_PER_RUN * i,
                  NUM_FQNS_PER_RUN, modificationsPerTx);
            t[j].start();
         }
         // fire the workers to start processing
         latch.countDown();
         // wait for all workers to complete
         for (Worker worker : t)
         {
            worker.join();
         }
      }
   }

   /**
    * Returns a user transaction to be associated with the calling thread.
    *
    * @return A user transaction.
    * @throws Exception Any exception thrown by the context lookup.
    */
   private UserTransaction getTransaction() throws Exception
   {
      return (UserTransaction) new InitialContext(PROPERTIES)
            .lookup("UserTransaction");
   }

   /**
    * A worker thread that applies the concurrent modifications.
    *
    * @author Marian Nikolov
    * @author $Author: mircea.markus $
    * @version $Date: 2008-11-06 19:07:10 -0600 (Thu, 06 Nov 2008) $
    */
   private class Worker extends Thread
   {
      /**
       * Used to fire all workers at the same time.
       */
      private final CountDownLatch m_latch;
      /**
       * The start id, used as part of the node FQN.
       */
      private final int m_start;
      /**
       * The number of nodes to create in a single run.
       */
      private final int m_count;
      /**
       * The number of modifications per single transaction.
       */
      private final int m_modificationsPerTx;

      /**
       * The state of the thread, used for logging.
       */
      private int m_state;

      /**
       * Constructor.
       *
       * @param latch              Used to synchronize the startup of all worker threads.
       * @param start              The start id.
       * @param count              The number of nodes to create in a single run.
       * @param modificationsPerTx The number of modifications per
       *                           transaction.
       */
      public Worker(CountDownLatch latch, int start, int count, int modificationsPerTx)
      {
         m_latch = latch;
         m_start = start;
         m_count = count;
         m_state = -1;
         m_modificationsPerTx = modificationsPerTx;
      }

      /**
       * {@inheritDoc}
       */
      public void run()
      {
         try
         {
            // the latch shall fire all workers at the same time
            m_latch.await();
            for (int i = m_start; i < m_start + m_count; i++)
            {
               m_state = -1;
               if (checkAndSetState())
               {
                  return;
               }
               long time = System.currentTimeMillis();
               UserTransaction tx = getTransaction();
               tx.begin();
               if (checkAndSetState())
               {
                  try
                  {
                     tx.rollback();
                  }
                  catch (Exception e)
                  {
                  }
                  return;
               }
               // the first worker would create a new node for the FQN
               // all the others would update the same node
               Fqn fqn = Fqn.fromString("/NODE/" + i);
               for (int m = 0; m < m_modificationsPerTx; m++)
               {
                  cache.put(fqn, m, i);
                  if (checkAndSetState())
                  {
                     try
                     {
                        tx.rollback();
                     }
                     catch (Exception e)
                     {
                     }
                     return;
                  }
               }
               tx.commit();
               if (checkAndSetState())
               {
                  return;
               }
               time = System.currentTimeMillis() - time;
            }
         }
         catch (Exception e)
         {
            e.printStackTrace();
            mcl_exception = e;
         }
      }

      /**
       * Checks the current thread and sets it state.
       *
       * @return True if the worker has to terminate, false otherwise.
       */
      private boolean checkAndSetState()
      {
         if (mcl_exception != null)
         {
            // another worker failed, terminate
            return true;
         }
         m_state++;
         return false;
      }
   }
}
