package org.jboss.cache.statetransfer;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.util.TestingUtil;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.util.Properties;

/**
 * Tests state transfer while the other node keeps sending transactional, synchronous method calls
 *
 * @author Bela Ban
 * @version $Id: StateTransferUnderLoadTest.java 7646 2009-02-04 23:37:41Z mircea.markus $
 */
@Test(groups = {"functional"}, enabled = false, description = "Disabled because this test depends on JBCACHE-315 being resolved.", testName = "statetransfer.StateTransferUnderLoadTest")
public class StateTransferUnderLoadTest
{
   Cache<Object, Object> cache1, cache2;
   Properties p = null;
   //String old_factory = null;
   static final String FACTORY = "org.jboss.cache.transaction.DummyContextFactory";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      //old_factory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
      DummyTransactionManager.getInstance();
      if (p == null)
      {
         p = new Properties();
         p.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
      }
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache2 != null)
      {
         TestingUtil.killCaches(cache2);
         cache2 = null;
      }
      if (cache1 != null)
      {
         TestingUtil.killCaches(cache1);
         cache1 = null;
      }

      // We just can't kill DummyTransactionManager. We are sharing single instance in more tests.
      TestingUtil.killTransaction(DummyTransactionManager.getInstance());
      /*
      if (old_factory != null)
      {
         System.setProperty(Context.INITIAL_CONTEXT_FACTORY, old_factory);
         old_factory = null;
      }
       */
   }

   public void testStateTransferDeadlocksPessimistic() throws Exception
   {
      runTest(false);
   }

   public void testStateTransferDeadlocksOptimistic() throws Exception
   {
      runTest(true);
   }


   private void runTest(boolean optimistic) throws Exception
   {
      Writer writer;
      Configuration cfg1, cfg2;
      cfg1 = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC);
      cfg2 = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC);
      cfg1.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      cfg2.setCacheMode(Configuration.CacheMode.REPL_SYNC);

      if (optimistic)
      {
         cfg1.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
         cfg2.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      }

      cfg1.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cfg2.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");

      cache1 = new UnitTestCacheFactory<Object, Object>().createCache(cfg1, true, getClass());
      cache2 = new UnitTestCacheFactory<Object, Object>().createCache(cfg2, false, getClass());
      UserTransaction tx1 = (UserTransaction) new InitialContext(p).lookup("UserTransaction");
      writer = new Writer(cache1, tx1);
      try
      {
         writer.start();


         cache2.create();
         for (int i = 0; i < 100; i++)
         {

            cache2.start();
            // gets state

            // check if state was retrieved successfully
            int num_nodes = ((CacheSPI<Object, Object>) cache2).getNumberOfNodes();
            AssertJUnit.assertTrue(num_nodes >= 1);

            TestingUtil.sleepThread(100);
            cache2.stop();
         }


      }
      finally
      {
         writer.stop();
      }
   }


   static class Writer implements Runnable
   {
      Thread thread;
      Cache<Object, Object> cache;
      boolean running = false;
      UserTransaction tx;


      public Writer(Cache<Object, Object> cache, UserTransaction tx)
      {
         this.cache = cache;
         this.tx = tx;
      }

      public void start()
      {
         thread = new Thread(this, "cache writer");
         running = true;
         thread.start();
      }

      public void stop()
      {
         running = false;
      }

      public void run()
      {
         Fqn fqn = Fqn.fromString("/a/b/c");
         while (running)
         {
            try
            {
               tx.begin();
               cache.put(fqn, "key", "value");
               tx.commit();
            }
            catch (Exception e)
            {
               e.printStackTrace();
               try
               {
                  tx.rollback();
               }
               catch (SystemException e1)
               {
               }
            }
            finally
            {
               TestingUtil.sleepRandom(100);
            }
         }
      }

   }
}
