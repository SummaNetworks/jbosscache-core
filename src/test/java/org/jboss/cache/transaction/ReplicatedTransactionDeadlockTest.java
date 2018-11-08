package org.jboss.cache.transaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.UserTransaction;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * A test created to simulate an unexpected TimeoutException in JBossCache
 * 1.3.0SP1 (not relevant for 1.2.4 and earlier releases). The error has been
 * initially observed in a production environment, the test has been created to
 * simplify the analysis without the complexity of hundreds concurrent
 * transactions.<br>
 * This test is relevant for REPL_SYNC mode, (default) isolation level
 * REPEATABLE_READ and SyncCommitPhase.<br>
 * The error scenario is:
 * <ul>
 * <li>Two caches: SrcCache is the one where we put the modifications, DstCache
 * only receives the replicated modifications.</li>
 * <li>Two threads (resp. transactions) A and B modifying the same node X
 * concurrently on SrcCache.</li>
 * <li>Let's assume the transaction A is faster in aquiring the lock on X.</li>
 * <li>Transaction A modifies the node X and starts committing: local prepare,
 * remote prepare, local commit. The remote prepare will lock the X on DstCache
 * and it will stay locked until the remote commit releases it later. Note that
 * in JBossCache 1.3.0SP1 the transaction will release the lock on SrcCache
 * immediately after the local commit step and before the remote (sync.) commit.
 * It seems that this have changed between 1.2.4 and 1.3.0 releases.</li>
 * <li>As soon as the lock on SrcCache is released by A, transaction B aquires
 * the lock immediately and starts local modifications.<b>Note that in some
 * cases B is fast enough to do the local prepare and send a remote prepare
 * message before the remote commit message of A. </b>B is able to do this
 * because the lock is released by A before its remote commit call.</li>
 * <li>Now, we have the X locked by A on DstCache waiting for the remote commit
 * of A to release it. The next messages from SrcCache are in the following
 * order coming up the JGROUPS stack of the DstCache: remote prepare for B,
 * remote commit for A.</li>
 * <li>The remote prepare of B blocks on DstCache, trying to acquire the lock
 * still held by A.</li>
 * <li>The remote commit of A waits in the UP queue of the STATE_TRANSFER,
 * waiting for the previous message (which is the remote prepare of B) to be
 * processed.</li>
 * <li>So A cannot be committed because it's blocked by B, which cannot be
 * prepared, because it's blocked by A, which cannot be committed. Of course the
 * result is a TimeoutException and too many rolled back transactions.</li>
 * </ul>
 *
 * @author Marian Nikolov
 * @author $Author: mircea.markus $
 * @version $Date: 2008-11-06 19:07:10 -0600 (Thu, 06 Nov 2008) $
 */
@Test(groups = {"functional", "transaction"}, testName = "transaction.ReplicatedTransactionDeadlockTest")
public class ReplicatedTransactionDeadlockTest
{

   // Number of worker threads
   private static final int NUM_WORKERS = 2;

   // The number of test runs to perform.
   private static final int NUM_RUNS = 100;

   // useful to increase this to get thread dumps, etc.  Typically should be set to 10,000 though.
   private static final long LOCK_ACQUISITION_TIMEOUT = 10000;

   /**
    * Exception recorded if any of the worker threads fails.
    */
   private static volatile Exception exception = null;

   /**
    * The source cache where we put modifications.
    */
   private CacheSPI<Boolean, Boolean> srcCache = null;
   /**
    * The target cache where we replicate modifications.
    */
   private CacheSPI dstCache = null;

   private Log log = LogFactory.getLog(ReplicatedTransactionDeadlockTest.class);

   /**
    * Constructor.
    *
    * @param name The test name.
    */

   /**
    * {@inheritDoc}
    */
   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      exception = null;
      UnitTestCacheFactory<Boolean, Boolean> instance = new UnitTestCacheFactory<Boolean, Boolean>();
      // setup and start the source cache
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);
      c.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setSyncCommitPhase(true);
      c.setLockAcquisitionTimeout(LOCK_ACQUISITION_TIMEOUT);      
      srcCache = (CacheSPI<Boolean, Boolean>) instance.createCache(c, false, getClass());
      srcCache.create();
      srcCache.start();

      // setup and start the destination cache
      c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setSyncCommitPhase(true);
      c.setLockAcquisitionTimeout(LOCK_ACQUISITION_TIMEOUT);
      dstCache = (CacheSPI) instance.createCache(c, false, getClass());
      dstCache.create();
      dstCache.start();
   }

   /**
    * {@inheritDoc}
    */
   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(srcCache, dstCache);
      srcCache = null;
      dstCache = null;
      // We just can't kill DummyTransactionManager. We are sharing single instance in more tests. 
      TestingUtil.killTransaction(TransactionSetup.getManager());      
   }

   /**
    * Test for a synchronously replicated cache with concurrent transactions on
    * the same node.<br>
    * This test fails very often with a TimeoutException.
    *
    * @throws Exception Any exception if thrown by the cache.
    */
   public void testConcurrentReplicatedTransaction() throws Exception
   {
      performTest();
   }

   /**
    * Perform a single test, using the pre-configured cache.
    *
    * @throws Exception Any exception if thrown by the cache.
    */
   private void performTest() throws Exception
   {
      // repeat the test several times since it's not always reproducible
      for (int i = 0; i < NUM_RUNS; i++)
      {
         if (exception != null)
         {
            // terminate the test on the first failed worker
            fail("Due to an exception: " + exception);
         }
         // start several worker threads to work with the same FQN
         Worker[] t = new Worker[NUM_WORKERS];
         for (int j = 0; j < t.length; j++)
         {
            t[j] = new Worker("worker " + i + ":" + j);
            t[j].start();
         }
         // wait for all workers to complete before repeating the test
         for (Worker aT : t)
            aT.join();
      }
   }

   /**
    * Returns a user transaction to be associated with the calling thread.
    *
    * @return A user transaction.
    * @throws Exception Any exception thrown by the context lookup.
    */
   private UserTransaction getTransaction()
   {
      return TransactionSetup.getUserTransaction();
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
       * Constructor.
       */
      public Worker(String name)
      {
         super(name);
      }

      /**
       * {@inheritDoc}
       */
      public void run()
      {
         try
         {
            UserTransaction tx = getTransaction();
            log.warn("begin");
            tx.begin();
            log.warn("put");
            srcCache.put("/Node", Boolean.FALSE, Boolean.TRUE);
            log.warn("commit");
            tx.commit();
            log.warn("leave");
         }
         catch (Exception e)
         {
            log.error("caught exception " + e, e);
            exception = e;
         }
      }
   }
}
