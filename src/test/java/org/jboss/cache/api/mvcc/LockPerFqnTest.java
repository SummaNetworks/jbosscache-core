package org.jboss.cache.api.mvcc;

import org.jboss.cache.Cache;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.concurrent.locks.PerElementLockContainer;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Test(groups = "functional", sequential = true, testName = "api.mvcc.LockPerFqnTest")
public class LockPerFqnTest
{
   Cache cache;

   @BeforeMethod
   public void setUp()
   {
      Configuration cfg = new Configuration();
      cfg.setUseLockStriping(false);
      cfg.setNodeLockingScheme(Configuration.NodeLockingScheme.MVCC);
      cache = new DefaultCacheFactory().createCache(cfg);
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
   }

   public void testLocksCleanedUp()
   {
      cache.put("/a/b/c", "k", "v");
      cache.put("/a/b/d", "k", "v");
      assertNoLocks();
   }

   public void testLocksConcurrency() throws Exception
   {
      final int NUM_THREADS = 10;
      final CountDownLatch l = new CountDownLatch(1);
      final int numLoops = 1000;
      final List<Exception> exceptions = new LinkedList<Exception>();

      Thread[] t = new Thread[NUM_THREADS];
      for (int i=0; i<NUM_THREADS; i++) t[i] = new Thread()
      {
         public void run()
         {
            try
            {
               l.await();
            }
            catch (Exception e)
            {
               // ignore
            }
            for (int i=0; i<numLoops; i++)
            {
               try
               {
                  switch (i % 2)
                  {
                     case 0:
                        cache.put("/a/fqn" + i, "k", "v");
                        break;
                     case 1:
                        cache.removeNode("/a/fqn" + i);
                        break;
                  }
               }
               catch (Exception e)
               {
                  exceptions.add(e);
               }
            }
         }
      };

      for (Thread th: t) th.start();
      l.countDown();
      for (Thread th: t) th.join();

      if (!exceptions.isEmpty()) throw exceptions.get(0);
      assertNoLocks();
   }

   private void assertNoLocks()
   {
      LockManager lm = TestingUtil.extractLockManager(cache);
      LockAssert.assertNoLocks(
            lm, TestingUtil.extractComponentRegistry(cache).getComponent(InvocationContextContainer.class)
      );

      PerElementLockContainer lc = (PerElementLockContainer) TestingUtil.extractField(lm, "lockContainer");
      assert lc.size() == 0;
   }
}
