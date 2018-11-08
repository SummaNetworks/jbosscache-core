/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.internals.ViewChangeListener;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.util.TestingUtil;

/**
 * Unit test class for SingletonStoreCacheLoader
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
@Test(groups = "functional", sequential = true, testName = "loader.SingletonStoreCacheLoaderTest")
public class SingletonStoreCacheLoaderTest
{
   private static final Log log = LogFactory.getLog(SingletonStoreCacheLoaderTest.class);

   private CacheSPI<Object, Object> cache1, cache2, cache3;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());
      cache3 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());

      cache1.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      cache2.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      cache3.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
   }

   public void testPutCacheLoaderWithNoPush() throws Exception
   {
      initSingletonNonPushCache(cache1);
      initSingletonNonPushCache(cache2);
      initSingletonNonPushCache(cache3);

      createCaches();
      statCaches();

      cache1.put(fqn("/test1"), "key", "value");
      cache2.put(fqn("/test2"), "key", "value");
      cache3.put(fqn("/test3"), "key", "value");

      CacheLoader cl1 = getDelegatingCacheLoader(cache1);
      CacheLoader cl2 = getDelegatingCacheLoader(cache2);
      CacheLoader cl3 = getDelegatingCacheLoader(cache3);

      assertTrue("/test1 should have been entered in cl1", cl1.exists(fqn("/test1")));
      assertTrue("/test2 should have been entered in cl1", cl1.exists(fqn("/test2")));
      assertTrue("/test3 should have been entered in cl1", cl1.exists(fqn("/test3")));

      assertFalse("/test1 should not be in cl2", cl2.exists(fqn("/test1")));
      assertFalse("/test2 should not be in cl2", cl2.exists(fqn("/test2")));
      assertFalse("/test3 should not be in cl2", cl2.exists(fqn("/test3")));

      assertFalse("/test1 should not be in cl3", cl3.exists(fqn("/test1")));
      assertFalse("/test2 should not be in cl3", cl3.exists(fqn("/test2")));
      assertFalse("/test3 should not be in cl3", cl3.exists(fqn("/test3")));

      stopCache1();

      cache2.put(fqn("/test4"), "key", "value");
      cache3.put(fqn("/test5"), "key", "value");

      assertTrue("/test4 should have been entered in cl2", cl2.exists(fqn("/test4")));
      assertTrue("/test5 should have been entered in cl2", cl2.exists(fqn("/test5")));

      assertFalse("/test4 should not be in cl3", cl3.exists(fqn("/test4")));
      assertFalse("/test5 should not be in cl3", cl3.exists(fqn("/test5")));

      stopCache2();

      cache3.put(fqn("/test6"), "key", "value");
      assertTrue("/test5 should have been entered in cl3", cl3.exists(Fqn.fromString("/test6")));
   }

   public void testPutCacheLoaderWithPush() throws Exception
   {
      initSingletonWithPushCache(cache1);
      initSingletonWithPushCache(cache2);
      initSingletonWithPushCache(cache3);

      createCaches();
      statCaches();

      cache1.put(fqn("/a"), "a-key", "a-value");
      cache1.put(fqn("/a"), "aa-key", "aa-value");
      cache1.put(fqn("/a/b"), "b-key", "b-value");
      cache1.put(fqn("/a/b"), "bb-key", "bb-value");
      cache1.put(fqn("/a/b/c"), "c-key", "c-value");
      cache1.put(fqn("/a/b/d"), "d-key", "d-value");
      cache1.put(fqn("/e"), "e-key", "e-value");
      cache1.put(fqn("/e/f/g"), "g-key", "g-value");

      CacheLoader cl1 = getDelegatingCacheLoader(cache1);
      CacheLoader cl2 = getDelegatingCacheLoader(cache2);
      CacheLoader cl3 = getDelegatingCacheLoader(cache3);

      assertTrue(cl1.get(fqn("/a")).containsKey("a-key"));
      assertTrue(cl1.get(fqn("/a")).containsKey("aa-key"));
      assertTrue(cl1.get(fqn("/a/b")).containsKey("b-key"));
      assertTrue(cl1.get(fqn("/a/b")).containsKey("bb-key"));
      assertTrue(cl1.get(fqn("/a/b/c")).containsKey("c-key"));
      assertTrue(cl1.get(fqn("/a/b/d")).containsKey("d-key"));
      assertTrue(cl1.get(fqn("/e")).containsKey("e-key"));
      assertTrue(cl1.get(fqn("/e/f/g")).containsKey("g-key"));

      assertFalse(cl2.exists(fqn("/a")));
      assertFalse(cl2.exists(fqn("/a")));
      assertFalse(cl2.exists(fqn("/a/b")));
      assertFalse(cl2.exists(fqn("/a/b")));
      assertFalse(cl2.exists(fqn("/a/b/c")));
      assertFalse(cl2.exists(fqn("/a/b/d")));
      assertFalse(cl2.exists(fqn("/e")));
      assertFalse(cl2.exists(fqn("/e/f/g")));

      assertFalse(cl3.exists(fqn("/a")));
      assertFalse(cl3.exists(fqn("/a")));
      assertFalse(cl3.exists(fqn("/a/b")));
      assertFalse(cl3.exists(fqn("/a/b")));
      assertFalse(cl3.exists(fqn("/a/b/c")));
      assertFalse(cl3.exists(fqn("/a/b/d")));
      assertFalse(cl3.exists(fqn("/e")));
      assertFalse(cl3.exists(fqn("/e/f/g")));

      ViewChangeListener viewChangeListener = new ViewChangeListener(cache2);

      stopCache1();
      viewChangeListener.waitForViewChange(60, TimeUnit.SECONDS);

      SingletonStoreCacheLoader scl2 = (SingletonStoreCacheLoader) cache2.getCacheLoaderManager().getCacheLoader();
      waitForPushStateCompletion(scl2.getPushStateFuture());

      assertTrue(cl2.get(fqn("/a")).containsKey("a-key"));
      assertTrue(cl2.get(fqn("/a")).containsKey("aa-key"));
      assertTrue(cl2.get(fqn("/a/b")).containsKey("b-key"));
      assertTrue(cl2.get(fqn("/a/b")).containsKey("bb-key"));
      assertTrue(cl2.get(fqn("/a/b/c")).containsKey("c-key"));
      assertTrue(cl2.get(fqn("/a/b/d")).containsKey("d-key"));
      assertTrue(cl2.get(fqn("/e")).containsKey("e-key"));
      assertTrue(cl2.get(fqn("/e/f/g")).containsKey("g-key"));

      cache2.put(fqn("/e/f/h"), "h-key", "h-value");
      cache3.put(fqn("/i"), "i-key", "i-value");

      assertTrue(cl2.get(fqn("/e/f/h")).containsKey("h-key"));
      assertTrue(cl2.get(fqn("/i")).containsKey("i-key"));

      assertFalse(cl3.exists(fqn("/a")));
      assertFalse(cl3.exists(fqn("/a")));
      assertFalse(cl3.exists(fqn("/a/b")));
      assertFalse(cl3.exists(fqn("/a/b")));
      assertFalse(cl3.exists(fqn("/a/b/c")));
      assertFalse(cl3.exists(fqn("/a/b/d")));
      assertFalse(cl3.exists(fqn("/e")));
      assertFalse(cl3.exists(fqn("/e/f/g")));
      assertFalse(cl3.exists(fqn("/e/f/h")));
      assertFalse(cl3.exists(fqn("/i")));

      viewChangeListener = new ViewChangeListener(cache3);
      stopCache2();
      viewChangeListener.waitForViewChange(60, TimeUnit.SECONDS);

      SingletonStoreCacheLoader scl3 = (SingletonStoreCacheLoader) cache3.getCacheLoaderManager().getCacheLoader();
      waitForPushStateCompletion(scl3.getPushStateFuture());

      assertTrue(cl3.get(fqn("/a")).containsKey("a-key"));
      assertTrue(cl3.get(fqn("/a")).containsKey("aa-key"));
      assertTrue(cl3.get(fqn("/a/b")).containsKey("b-key"));
      assertTrue(cl3.get(fqn("/a/b")).containsKey("bb-key"));
      assertTrue(cl3.get(fqn("/a/b/c")).containsKey("c-key"));
      assertTrue(cl3.get(fqn("/a/b/d")).containsKey("d-key"));
      assertTrue(cl3.get(fqn("/e")).containsKey("e-key"));
      assertTrue(cl3.get(fqn("/e/f/g")).containsKey("g-key"));
      assertTrue(cl3.get(fqn("/e/f/h")).containsKey("h-key"));
      assertTrue(cl3.get(fqn("/i")).containsKey("i-key"));

      cache3.put(fqn("/a"), "aaa-key", "aaa-value");

      assertTrue(cl3.get(fqn("/a")).containsKey("aaa-key"));

      stopCache3();
   }

   public void testAvoidConcurrentStatePush() throws Exception
   {
      final ExecutorService executor = Executors.newFixedThreadPool(2);
      final CountDownLatch pushStateCanFinish = new CountDownLatch(1);
      final CountDownLatch secondActiveStatusChangerCanStart = new CountDownLatch(1);
      final MockSingletonStoreCacheLoader mscl = new MockSingletonStoreCacheLoader(pushStateCanFinish, secondActiveStatusChangerCanStart, new SingletonStoreDefaultConfig());

      Future f1 = executor.submit(createActiveStatusChanger(mscl));
      secondActiveStatusChangerCanStart.await();

      Future f2 = executor.submit(createActiveStatusChanger(mscl));

      f1.get();
      f2.get();

      assertEquals(1, mscl.getNumberCreatedTasks());
   }

   public void testPushStateTimedOut() throws Exception
   {
      final CountDownLatch pushStateCanFinish = new CountDownLatch(1);
      SingletonStoreDefaultConfig ssdc = new SingletonStoreDefaultConfig();
      ssdc.setPushStateWhenCoordinatorTimeout(1000);
      final MockSingletonStoreCacheLoader mscl = new MockSingletonStoreCacheLoader(pushStateCanFinish, null, ssdc);

      Future f = Executors.newSingleThreadExecutor().submit(createActiveStatusChanger(mscl));
      pushStateCanFinish.await(2000, TimeUnit.MILLISECONDS);
      pushStateCanFinish.countDown();

      try
      {
         f.get();
         fail("Should have timed out");
      }
      catch (ExecutionException e)
      {
         Throwable t = e.getCause().getCause().getCause();
         assertTrue(t + " should have been TimeoutException", t instanceof TimeoutException);
      }

   }

   private void createCaches()
   {
      cache1.create();
      cache2.create();
      cache3.create();
   }

   private void statCaches()
   {
      cache1.start();
      cache2.start();
      cache3.start();
   }

   private void waitForPushStateCompletion(Future pushThreadFuture) throws Exception
   {
      if (pushThreadFuture != null)
      {
         pushThreadFuture.get();
      }
   }

   private Callable<?> createActiveStatusChanger(SingletonStoreCacheLoader mscl)
   {
      return new ActiveStatusModifier(mscl);
   }

   protected CacheLoaderConfig getSingletonStoreCacheLoaderConfig(String cacheloaderClass) throws Exception
   {
      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, null, cacheloaderClass, "", false, false, false, false, false);
      CacheLoaderConfig.IndividualCacheLoaderConfig.SingletonStoreConfig sc = new CacheLoaderConfig.IndividualCacheLoaderConfig.SingletonStoreConfig();
      sc.setSingletonStoreEnabled(true);
      sc.setProperties("pushStateWhenCoordinator = true\n pushStateWhenCoordinatorTimeout = 5000\n");
      clc.getFirstCacheLoaderConfig().setSingletonStoreConfig(sc);
      return clc;
   }

   private void initSingletonNonPushCache(CacheSPI cache) throws Exception
   {
      cache.getConfiguration().setCacheLoaderConfig(getSingletonStoreCacheLoaderConfig(
            DummyInMemoryCacheLoader.class.getName()));
   }

   private void initSingletonWithPushCache(CacheSPI cache) throws Exception
   {
      cache.getConfiguration().setCacheLoaderConfig(getSingletonStoreCacheLoaderConfig(
            DummyInMemoryCacheLoader.class.getName()));
   }

   private CacheLoader getDelegatingCacheLoader(CacheSPI cache)
   {
      AbstractDelegatingCacheLoader acl = (AbstractDelegatingCacheLoader) cache.getCacheLoaderManager().getCacheLoader();
      return acl.getCacheLoader();
   }

   private Fqn fqn(String fqn)
   {
      return Fqn.fromString(fqn);
   }

   private void stopCache1()
   {
      if (cache1 != null)
      {
         TestingUtil.killCaches(cache1);
      }

      cache1 = null;
   }

   private void stopCache2()
   {
      if (cache2 != null)
      {
         TestingUtil.killCaches(cache2);
      }

      cache2 = null;
   }

   private void stopCache3()
   {
      if (cache3 != null)
      {
         TestingUtil.killCaches(cache3);
      }

      cache3 = null;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      stopCache1();
      stopCache2();
      stopCache3();
   }

   class MockSingletonStoreCacheLoader extends SingletonStoreCacheLoader
   {
      private int numberCreatedTasks = 0;
      private CountDownLatch pushStateCanFinish;
      private CountDownLatch secondActiveStatusChangerCanStart;

      public MockSingletonStoreCacheLoader(CountDownLatch pushStateCanFinish, CountDownLatch secondActiveStatusChangerCanStart, SingletonStoreDefaultConfig config)
      {
         super(config);
         this.pushStateCanFinish = pushStateCanFinish;
         this.secondActiveStatusChangerCanStart = secondActiveStatusChangerCanStart;
      }

      public int getNumberCreatedTasks()
      {
         return numberCreatedTasks;
      }

      public void setNumberCreatedTasks(int numberCreatedTasks)
      {
         this.numberCreatedTasks = numberCreatedTasks;
      }

      @Override
      protected Callable<?> createPushStateTask()
      {
         return new Callable()
         {
            public Object call() throws Exception
            {
               numberCreatedTasks++;
               try
               {
                  if (secondActiveStatusChangerCanStart != null)
                  {
                     secondActiveStatusChangerCanStart.countDown();
                  }
                  pushStateCanFinish.await();
               }
               catch (InterruptedException e)
               {
                  fail("ActiveStatusModifier interrupted");
               }
               return null;
            }
         };
      }


      @Override
      protected void awaitForPushToFinish(Future future, int timeout, TimeUnit unit)
      {
         pushStateCanFinish.countDown();
         super.awaitForPushToFinish(future, timeout, unit);
      }
   }

   class ActiveStatusModifier implements Callable
   {
      private SingletonStoreCacheLoader scl;

      public ActiveStatusModifier(SingletonStoreCacheLoader singleton)
      {
         scl = singleton;
      }

      public Object call() throws Exception
      {
         log.debug("active status modifier started");
         scl.activeStatusChanged(true);
         scl.getPushStateFuture().get();

         return null;
      }
   }
}
