package org.jboss.cache.loader;

import org.jboss.cache.AbstractSingleCacheTest;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Test(groups = "functional", testName = "loader.AddRemoveNodeDeadlockTest")
public class AddRemoveNodeDeadlockTest extends AbstractSingleCacheTest
{
   private static final Fqn<String> FQN = Fqn.fromElements("a");
   private CacheSPI<String, String> cache;
   private TransactionManager tm;

   public CacheSPI createCache() throws Exception
   {
      UnitTestCacheFactory<String, String> cf = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) cf.createCache("configs/local-tx.xml", false, getClass());
      cache.getConfiguration().setLockAcquisitionTimeout(4000);
      cache.getConfiguration().setEvictionConfig(null);
      // !
      cache.getConfiguration().setLockParentForChildInsertRemove(true);
      // !
      CacheLoaderConfig cacheLoaderConfig = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyInMemoryCacheLoader.class.getName(), "", false, true, false, false, false);
      cache.getConfiguration().setCacheLoaderConfig(cacheLoaderConfig);

      cache.start();
      tm = cache.getTransactionManager();

      return cache;
   }

   private void await(CountDownLatch latch, boolean expect, String message) throws InterruptedException, TimeoutException
   {
      assert latch.await(cache.getConfiguration().getLockAcquisitionTimeout() / 2, TimeUnit.MILLISECONDS) == expect
         : message;
   }

   public void testAdd() throws Exception
   {
      cache.put(FQN.getParent(), "x", "a");
      doTest(true);
   }

   public void testRemove() throws Exception
   {
      cache.put(FQN, "x", "a");
      doTest(false);
   }

   private void doTest(final boolean isAdd) throws Exception
   {
      ExecutorService exec = Executors.newFixedThreadPool(2);
      try
      {
         final CountDownLatch init = new CountDownLatch(1);
         final CountDownLatch parentLocked = new CountDownLatch(1);
         final CountDownLatch childLocked = new CountDownLatch(1);
         final CountDownLatch done = new CountDownLatch(1);

         /*
          * The procedure:
          * 1) t1 locks the parent node
          * 2) t2 tries to add/remove the child
          * Under normal circumstances t2 should block on parent before adding/removing the child.
          * If there's a deadlock condition, it will lock the child first and then proceed to lock the parent (and block).
          * 3) t1 adds/removes the child
          * Normally this should succeed as t1 already holds parent's lock.
          * Under deadlock condition it will be blocked by t2's lock on child.
          */

         // t1
         Future<?> f1 = exec.submit(new Callable<Void>()
            {
               public Void call() throws Exception
               {
                  tm.begin();
                  try
                  {
                     await(init, true, "init t1");
                     cache.put(FQN.getParent(), "x", "b");
                     parentLocked.countDown();

                     // this will time out because there's no one who can ping the latch, but that's expected
                     await(childLocked, false, "child was locked by t2");
                     cache.put(FQN, "x", "b");
                     done.countDown();

                     tm.commit();
                  }
                  finally
                  {
                     if (tm.getTransaction() != null)
                     {
                        tm.rollback();
                     }
                  }

                  return null;
               }
            });

         // t2
         Future<?> f2 = exec.submit(new Callable<Void>()
            {
               public Void call() throws Exception
               {
                  tm.begin();
                  try
                  {
                     init.countDown();
                     await(parentLocked, true, "t2 parent lock");
                     // uncomment following line to simulate proper locking order
//                     cache.put(FQN.getParent(), "x", "a");
                     if (isAdd)
                     {
                        // the deadlock exception would be here:
                        cache.put(FQN, "x", "a");
                     }
                     else
                     {
                        cache.removeNode(FQN);
                     }
                     childLocked.countDown();
                     await(done, true, "t2 done");

                     tm.commit();
                  }
                  finally
                  {
                     if (tm.getTransaction() != null)
                     {
                        tm.rollback();
                     }
                  }

                  return null;
               }
            });

         f1.get(cache.getConfiguration().getLockAcquisitionTimeout() * 3, TimeUnit.MILLISECONDS);
         f2.get(cache.getConfiguration().getLockAcquisitionTimeout() * 3, TimeUnit.MILLISECONDS);
      }
      finally
      {
         exec.shutdown();
      }
   }
}
