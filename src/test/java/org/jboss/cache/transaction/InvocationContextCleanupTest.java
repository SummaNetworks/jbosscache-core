/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.transaction;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.interceptors.OrderedSynchronizationHandler;
import org.jboss.cache.util.CachePrinter;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * Tests cleaning of invocation contexts on completion of txs
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "transaction"}, sequential = true, testName = "transaction.InvocationContextCleanupTest")
public class InvocationContextCleanupTest
{
   private CacheSPI[] caches;

   private CacheSPI<?, ?> createCache(boolean optimistic)
   {
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      if (optimistic) c.setNodeLockingScheme("OPTIMISTIC");
      c.setClusterName("InvocationContextCleanupTest");
      c.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      c.setLockAcquisitionTimeout(2000);
      
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      cache.start();
      return cache;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (caches != null)
      {
         for (int i = 0; i < caches.length; i++)
         {
            if (caches[i] != null)
            {
               TestingUtil.killCaches(caches[i]);
               caches[i] = null;
            }
         }
         caches = null;
      }
   }

   public void testInvocationContextCleanupPessimistic() throws Exception
   {
      test2CachesSync(false);
   }

   private void test2CachesSync(boolean optimistic) throws Exception
   {
      caches = new CacheSPI[2];
      CacheSPI cache0 = createCache(optimistic);
      CacheSPI cache1 = createCache(optimistic);
      caches[0] = cache0;
      caches[1] = cache1;

      TestingUtil.blockUntilViewsReceived(caches, 2000);

      TransactionManager mgr = caches[0].getTransactionManager();

      mgr.begin();

      cache0.put("/test", "x", "y");

      GlobalTransaction gtx = cache0.getTransactionTable().get(mgr.getTransaction());
      OrderedSynchronizationHandler orderedHandler = cache0.getTransactionTable().get(gtx).getOrderedSynchronizationHandler();
//      OrderedSynchronizationHandler orderedHandler = OrderedSynchronizationHandler.getInstance(mgr.getTransaction());
      orderedHandler.registerAtTail(new DummySynchronization(cache0, mgr));

      try
      {
         mgr.commit();
      }
      finally
      {
      }

      assertEquals("y", cache0.get("/test", "x"));
      assertEquals("y", cache0.get("/test", "x"));
   }

   public static class DummySynchronization implements Synchronization
   {
      private CacheSPI cache;
      private TransactionManager mgr;

      public DummySynchronization(CacheSPI<?, ?> cache, TransactionManager mgr)
      {
         this.cache = cache;
         this.mgr = mgr;
      }

      public void beforeCompletion()
      {
         // before returning, do a put (non-tx) on the cache!!
         Transaction tx = null;
         try
         {
            tx = mgr.suspend();
         }
         catch (SystemException e)
         {
            throw new RuntimeException("Unable to sustend transaction! " + e.getMessage());
         }

         try
         {
            cache.put("/test", "blah", "blahblah");
            assertTrue("Should fail with a lock exception!", false);
         }
         catch (Exception e)
         {
            assertTrue("Should fail!", true);
         }
         finally
         {
            if (tx != null)
            {
               try
               {
                  mgr.resume(tx);
               }
               catch (Exception e)
               {
               }
            }
         }
      }

      public void afterCompletion(int i)
      {
         // do nothing
      }
   }
}
