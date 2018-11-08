/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.config.Option;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeInvalidated;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.annotation.NodeRemoved;
import org.jboss.cache.notifications.event.NodeEvent;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.concurrent.CountDownLatch;

/**
 * Tests functionality of {@link Option#setForceAsynchronous(boolean)} and
 * {@link Option#setForceSynchronous(boolean)}.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, sequential = true, enabled = false, testName = "options.ForceCacheModeTest")
public class ForceCacheModeTest
{
   private static final Log log = LogFactory.getLog(ForceCacheModeTest.class);

   private static final Fqn FQNA = Fqn.fromString("/A");
   private static final String KEY = "key";
   private static final String VALUE1 = "value1";
   private static final String VALUE2 = "value2";

   private CacheSPI<Object, Object> cache1, cache2;
   private Option asyncOption;
   private Option syncOption;
   private static CountDownLatch latch;
   private BlockingListener listener;

   private void createCaches(NodeLockingScheme scheme, CacheMode mode)
   {

      Configuration c = new Configuration();
      c.setNodeLockingScheme(scheme);
      c.setCacheMode(mode);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");

      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      c = new Configuration();
      c.setNodeLockingScheme(scheme);
      c.setCacheMode(mode);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      cache1.start();
      cache2.start();

      asyncOption = new Option();
      asyncOption.setForceAsynchronous(true);

      syncOption = new Option();
      syncOption.setForceSynchronous(true);

      Option local = new Option();
      local.setCacheModeLocal(true);

      cache1.getInvocationContext().setOptionOverrides(local);
      cache1.put(FQNA, KEY, VALUE1);

      assertEquals("Cache1 correct", VALUE1, cache1.get(FQNA, KEY));

      local = new Option();
      local.setCacheModeLocal(true);
      cache2.getInvocationContext().setOptionOverrides(local);
      cache2.put(FQNA, KEY, VALUE1);

      // Validate data is as expected
      assertEquals("Cache1 correct", VALUE1, cache1.get(FQNA, KEY));
      assertEquals("Cache2 correct", VALUE1, cache2.get(FQNA, KEY));

      listener = new BlockingListener();
      cache2.addCacheListener(listener);
   }

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      latch = new CountDownLatch(1);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (cache1 != null)
      {
         TestingUtil.killCaches(cache1);
         cache1 = null;
      }

      if (cache2 != null)
      {
         if (listener != null)
            cache2.removeCacheListener(listener);
         TestingUtil.killCaches(cache2);
         cache2 = null;
      }

      latch.countDown();
   }

   public void testPessimisticReplicationPutForceAsync() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.REPL_SYNC);

      checkNoBlocking(null, asyncOption, false);
   }

   public void testPessimisticReplicationRemoveForceAsync() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.REPL_SYNC);

      checkNoBlocking(null, asyncOption, true);
   }

   public void testPessimisticReplicationPutForceAsyncWithTx() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.REPL_SYNC);

      checkBlocking(cache1.getTransactionManager(), asyncOption, false);
   }

   public void testPessimisticInvalidationPutForceAsync() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.INVALIDATION_SYNC);

      checkNoBlocking(null, asyncOption, false);
   }

   public void testPessimisticInvalidationRemoveForceAsync() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.INVALIDATION_SYNC);

      checkNoBlocking(null, asyncOption, true);
   }

   public void testPessimisticInvalidationPutForceAsyncWithTx() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.INVALIDATION_SYNC);

      checkBlocking(cache1.getTransactionManager(), asyncOption, false);
   }

   public void testPessimisticReplicationPutForceSync() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.REPL_ASYNC);

      checkBlocking(null, syncOption, false);
   }

   public void testPessimisticReplicationRemoveForceSync() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.REPL_ASYNC);

      checkBlocking(null, syncOption, true);
   }

   public void testPessimisticReplicationPutForceSyncWithTx() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.REPL_ASYNC);

      checkNoBlocking(cache1.getTransactionManager(), syncOption, false);
   }

   public void testPessimisticInvalidationPutForceSync() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.INVALIDATION_ASYNC);

      checkBlocking(null, syncOption, false);
   }

   public void testPessimisticInvalidationRemoveForceSync() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.INVALIDATION_ASYNC);

      checkBlocking(null, syncOption, true);
   }

   public void testPessimisticInvalidationPutForceSyncWithTx() throws Exception
   {
      createCaches(NodeLockingScheme.PESSIMISTIC, CacheMode.INVALIDATION_ASYNC);

      checkNoBlocking(cache1.getTransactionManager(), syncOption, false);
   }

   public void testOptimisticReplicationPutForceAsync() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.REPL_SYNC);

      checkNoBlocking(null, asyncOption, false);
   }

   public void testOptimisticReplicationRemoveForceAsync() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.REPL_SYNC);

      checkNoBlocking(null, asyncOption, true);
   }

   public void testOptimisticReplicationPutForceAsyncWithTx() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.REPL_SYNC);

      checkBlocking(cache1.getTransactionManager(), asyncOption, false);
   }

   public void testOptimisticInvalidationPutForceAsync() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.INVALIDATION_SYNC);

      checkNoBlocking(null, asyncOption, false);
   }

   public void testOptimisticInvalidationRemoveForceAsync() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.INVALIDATION_SYNC);

      checkNoBlocking(null, asyncOption, true);
   }

   public void testOptimisticInvalidationPutForceAsyncWithTx() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.INVALIDATION_SYNC);

      checkBlocking(cache1.getTransactionManager(), asyncOption, false);
   }

   public void testOptimisticReplicationPutForceSync() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.REPL_ASYNC);

      checkBlocking(null, syncOption, false);
   }

   public void testOptimisticReplicationRemoveForceSync() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.REPL_ASYNC);

      checkBlocking(null, syncOption, true);
   }

   public void testOptimisticReplicationPutForceSyncWithTx() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.REPL_ASYNC);

      checkNoBlocking(cache1.getTransactionManager(), syncOption, false);
   }

   public void testOptimisticInvalidationPutForceSync() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.INVALIDATION_ASYNC);

      checkBlocking(null, syncOption, false);
   }

   public void testOptimisticInvalidationRemoveForceSync() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.INVALIDATION_ASYNC);

      checkBlocking(null, syncOption, true);
   }

   public void testOptimisticInvalidationPutForceSyncWithTx() throws Exception
   {
      createCaches(NodeLockingScheme.OPTIMISTIC, CacheMode.INVALIDATION_ASYNC);

      checkNoBlocking(cache1.getTransactionManager(), syncOption, false);
   }

   /**
    * Confirms the updater is not blocked and that the cache state is as
    * expected at the end.
    *
    * @param tm         transction manager Updater should use. For non-transactional
    *                   tests, should be <code>null</code>
    * @param option     Option to set before doing put
    * @param removeTest true if we're testing a remove; false if a put
    * @throws InterruptedException
    * @throws CacheException
    */
   private void checkNoBlocking(TransactionManager tm, Option option, boolean removeTest) throws InterruptedException, CacheException
   {
      Updater updater = new Updater(tm, option, removeTest);
      updater.start();

      updater.join(250);
      assertTrue("Updater didn't finish", updater.finished);
      assertNull("Updater failed", updater.failure);

      for (int i = 0; i < 50; i++)
      {
         if (listener.blocked)
            break;
         TestingUtil.sleepThread(10);
      }

      assertTrue("Listener should have blocked!", listener.blocked);
      assertEquals("Cache1 state incorrect!", removeTest ? null : VALUE2, cache1.get(FQNA, KEY));

      latch.countDown();

      for (int i = 0; i < 50; i++)
      {
         if (!listener.blocked)
            break;
         TestingUtil.sleepThread(10);
      }

      // sleep a bit more to ensure the cache2 thread completes
      TestingUtil.sleepThread(5);
      CacheMode mode = cache2.getConfiguration().getCacheMode();
      boolean expectNull = (removeTest || mode == CacheMode.INVALIDATION_ASYNC || mode == CacheMode.INVALIDATION_SYNC);
      assertEquals("Cache2 state incorrect!", expectNull ? null : VALUE2, cache2.get(FQNA, KEY));
   }

   /**
    * Confirms the updater is blocked and that the cache state is as
    * expected at the end.
    *
    * @param tm         transction manager Updater should use. For non-transactional
    *                   tests, should be <code>null</code>
    * @param option     Option to set before doing put
    * @param removeTest true if we're testing a remove; false if a put
    * @throws InterruptedException
    * @throws CacheException
    */
   private void checkBlocking(TransactionManager tm, Option option, boolean removeTest) throws InterruptedException, CacheException
   {
      Updater updater = new Updater(tm, option, removeTest);
      updater.start();

      updater.join(250);
      assertFalse("Updater should have blocked!", updater.finished);

      for (int i = 0; i < 50; i++)
      {
         if (listener.blocked)
            break;
         TestingUtil.sleepThread(10);
      }

      assertTrue("Listener should have blocked", listener.blocked);

      latch.countDown();

      for (int i = 0; i < 50; i++)
      {
         if (updater.finished && !listener.blocked)
            break;
         TestingUtil.sleepThread(10);
      }

      assertTrue("Updater should have finished", updater.finished);
      assertFalse("Listener should have blocked", listener.blocked);
      assertNull("Updater should have succeeded", updater.failure);

      assertEquals("Cache1 state incorrect", removeTest ? null : VALUE2, cache1.get(FQNA, KEY));

      // sleep a bit more to ensure the cache2 thread completes
      TestingUtil.sleepThread(500);

      CacheMode mode = cache2.getConfiguration().getCacheMode();
      boolean expectNull = (removeTest || mode == CacheMode.INVALIDATION_ASYNC || mode == CacheMode.INVALIDATION_SYNC);
      assertEquals("Cache2 state incorrect", expectNull ? null : VALUE2, cache2.get(FQNA, KEY));
   }

   class Updater extends Thread
   {
      TransactionManager tm;
      Option option;
      boolean remove;
      Throwable failure;
      boolean finished;

      Updater(TransactionManager tm, Option option)
      {
         this(tm, option, false);
      }

      Updater(TransactionManager tm, Option option, boolean remove)
      {
         this.tm = tm;
         this.option = option;
         this.remove = remove;
      }

      public void run()
      {
         try
         {

            try
            {
               if (tm != null)
               {
                  tm.begin();
               }

               cache1.getInvocationContext().setOptionOverrides(option);
               if (remove)
                  cache1.remove(FQNA, KEY);
               else
                  cache1.put(FQNA, KEY, VALUE2);
            }
            catch (Exception e)
            {
               if (tm != null)
                  tm.setRollbackOnly();
               throw e;
            }
            finally
            {
               if (tm != null)
               {
                  tm.commit();
               }
               finished = true;
            }
         }
         catch (Throwable t)
         {
            failure = t;
         }
      }
   }

   @CacheListener
   public static class BlockingListener
   {
      boolean blocked;

      @NodeModified
      @NodeRemoved
      @NodeInvalidated
      public void block(NodeEvent event)
      {
         log.error("Received event notification " + event);
         if (!event.isPre() && FQNA.equals(event.getFqn()))
         {
            blocked = true;
            try
            {
               latch.await();
            }
            catch (InterruptedException e)
            {
            }

            blocked = false;
         }
      }
   }

}
