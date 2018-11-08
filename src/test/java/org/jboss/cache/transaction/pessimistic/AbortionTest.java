/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.transaction.pessimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.interceptors.OrderedSynchronizationHandler;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.transaction.NotifyingTransactionManager;
import org.jboss.cache.transaction.NotifyingTransactionManager.Notification;
import org.jboss.cache.transaction.TransactionContext;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, testName = "transaction.pessimistic.AbortionTest")
public class AbortionTest
{
   private CacheSPI cache1, cache2, cache3;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache1 = initCache(false);
      cache2 = initCache(false);
      cache3 = initCache(true);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache3, cache2, cache1);
      cache1 = null;
      cache2 = null;
      cache3 = null;
   }

   private CacheSPI initCache(boolean notifying) throws Exception           
   {      
      Configuration conf = UnitTestConfigurationFactory.getEmptyConfiguration();
      conf.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      conf.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      conf.setFetchInMemoryState(false);
      CacheSPI c = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(conf, false, getClass());
      if (!notifying)
      {
         c.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      }
      else
      {
         c.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.NotifyingTransactionManager");
      }
      c.start();
      return c;
   }

   public void testSyncCaches() throws Exception
   {
      performTest(false, false);
   }

   public void testSyncCachesSyncCommitRollback() throws Exception
   {
      performTest(true, false);
   }

   /**
    * Note that this tests a *remote* beforeCompletion abort - which is a part of the calling instance's afterCompletion.
    *
    * @throws Exception
    */
   public void testAbortBeforeCompletion() throws Exception
   {
      performTest(true, true);
   }

   private void performTest(boolean syncCommitRollback, boolean abortBeforeCompletion) throws Exception
   {
      cache1.getConfiguration().setSyncCommitPhase(syncCommitRollback);
      cache1.getConfiguration().setSyncRollbackPhase(syncCommitRollback);
      cache2.getConfiguration().setSyncCommitPhase(syncCommitRollback);
      cache2.getConfiguration().setSyncRollbackPhase(syncCommitRollback);
      cache3.getConfiguration().setSyncCommitPhase(syncCommitRollback);
      cache3.getConfiguration().setSyncRollbackPhase(syncCommitRollback);

      TransactionManager mgr1 = cache1.getTransactionManager();
      TransactionManager mgr2 = cache2.getTransactionManager();
      assertTrue(cache3.getTransactionManager() instanceof NotifyingTransactionManager);
      NotifyingTransactionManager mgr3 = (NotifyingTransactionManager) cache3.getTransactionManager();
      mgr3.setCache(cache3);

      assertSame(mgr1, mgr2);
      assertNotSame(mgr1, mgr3);
      assertNotSame(mgr2, mgr3);

      assertTrue(mgr1 instanceof DummyTransactionManager);
      assertTrue(mgr2 instanceof DummyTransactionManager);

      ReplicationListener cacheLister2 = ReplicationListener.getReplicationListener(cache2);
      ReplicationListener cacheLister3 = ReplicationListener.getReplicationListener(cache3);

      cacheLister2.expect(PutKeyValueCommand.class);
      cacheLister3.expect(PutKeyValueCommand.class);

      cache1.put("/test", "key", "value");
      cacheLister2.waitForReplicationToOccur();
      cacheLister3.waitForReplicationToOccur();

      assertEquals("value", cache1.get("/test", "key"));
      assertEquals("value", cache2.get("/test", "key"));
      assertEquals("value", cache3.get("/test", "key"));

      mgr3.setNotification(new TestNotification(abortBeforeCompletion));

      cacheLister2.expectWithTx(PutKeyValueCommand.class);
      cacheLister3.expectWithTx(PutKeyValueCommand.class);
      mgr1.begin();
      cache1.put("/test", "key", "value2");
      mgr1.commit();
      cacheLister2.waitForReplicationToOccur();
      cacheLister3.waitForReplicationToOccur();

      // only test cache1 and cache2.  Assume cache3 has crashed out.
      assertEquals(0, cache1.getNumberOfLocksHeld());
      assertEquals(0, cache2.getNumberOfLocksHeld());
      assertEquals("put in transaction should NOT have been rolled back", "value2", cache1.get("/test", "key"));
      assertEquals("put in transaction should NOT have been rolled back", "value2", cache2.get("/test", "key"));

   }

   class TestNotification implements Notification
   {
      boolean abortBeforeCompletion;

      public TestNotification(boolean abortBeforeCompletion)
      {
         this.abortBeforeCompletion = abortBeforeCompletion;
      }

      public void notify(Transaction tx, TransactionContext transactionContext) throws SystemException, RollbackException
      {
         OrderedSynchronizationHandler osh = transactionContext.getOrderedSynchronizationHandler();

         final Transaction finalTx = tx;
         // add an aborting sync handler.
         Synchronization abort = new Synchronization()
         {

            public void beforeCompletion()
            {
               if (abortBeforeCompletion)
               {
                  cache3.getConfiguration().getRuntimeConfig().getChannel().close();
                  try
                  {
                     finalTx.setRollbackOnly();
                  }
                  catch (SystemException e)
                  {
                     throw new RuntimeException("Unable to set rollback", e);
                  }
                  throw new RuntimeException("Dummy exception");
               }
            }

            public void afterCompletion(int i)
            {
               if (!abortBeforeCompletion)
               {
                  cache3.getConfiguration().getRuntimeConfig().getChannel().close();
                  throw new RuntimeException("Dummy exception");
               }
            }
         };

         osh.registerAtHead(abort);
      }

   }
}
