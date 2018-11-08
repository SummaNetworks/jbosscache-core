/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.statetransfer;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.RegionImpl;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.marshall.InactiveRegionException;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import org.jboss.cache.util.internals.ReplicationQueueNotifier;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Abstract superclass of "StateTransferVersion"-specific tests
 * of CacheSPI's state transfer capability.
 * <p/>
 *
 * @author <a href="mailto://brian.stansberry@jboss.com">Brian Stansberry</a>
 */
@Test(groups = "functional", testName = "statetransfer.StateTransferConcurrencyTest")
public class StateTransferConcurrencyTest extends StateTransferTestBase
{
   protected String getReplicationVersion()
   {
      return "3.0.0.GA";
   }

   /**
    * Tests concurrent activation of the same subtree by multiple nodes in a
    * REPL_ASYNC environment.  The idea is to see what would happen with a
    * farmed deployment. See <code>concurrentActivationTest</code> for details.
    *
    * @throws Exception
    */
   public void testConcurrentActivationAsync() throws Exception
   {
      concurrentActivationTest(false);
   }

   /**
    * //todo - create a mvn profile and allow tests to run on more than 2 caches
    * Starts 2 caches and then concurrently activates the same region under
    * all 2, causing each to attempt a partial state transfer from the other.
    * As soon as each cache has activated its region, it does a put to a node
    * in the region, thus complicating the lives of the other cache trying
    * to get partial state.
    * <p/>
    * Failure condition is if any node sees an exception or if the final state
    * of all caches is not consistent.
    */
   private void concurrentActivationTest(boolean sync)
   {
      String[] names = {"A", "B"};
      int count = names.length;
      CacheActivator[] activators = new CacheActivator[count];

      long start = System.currentTimeMillis();
      try
      {
         // Create a semaphore and take all its tickets
         Semaphore semaphore = new Semaphore(count);
         semaphore.acquire(count);

         // Create activation threads that will block on the semaphore
         CacheSPI[] caches = new CacheSPI[count];
         for (int i = 0; i < count; i++)
         {
            activators[i] = new CacheActivator(semaphore, names[i], sync);
            caches[i] = activators[i].getCacheSPI();
            activators[i].start();
         }

         // Make sure everyone is in sync
         TestingUtil.blockUntilViewsReceived(caches, 60000);

         // Release the semaphore to allow the threads to start work
         semaphore.release(count);

         // Sleep to ensure the threads get all the semaphore tickets
         while (semaphore.availablePermits() != 0) TestingUtil.sleepThread(100);

         // Reacquire the semaphore tickets; when we have them all
         // we know the threads are done
         for (int i = 0; i < count; i++)
         {
            boolean acquired = semaphore.tryAcquire(60, TimeUnit.SECONDS);
            if (!acquired) fail("failed to acquire semaphore " + i);
         }

         // allow any async calls to clear
         if (!sync)
         {
            waitTillAllReplicationsFinish(count, caches);
         }

         System.out.println("System.currentTimeMillis()-st = " + (System.currentTimeMillis()-start));

         // Ensure the caches held by the activators see all the values
         for (int i = 0; i < count; i++)
         {
            Exception aException = activators[i].getException();
            boolean gotUnexpectedException = aException != null
                  && !(aException instanceof InactiveRegionException ||
                  aException.getCause() instanceof InactiveRegionException);
            if (gotUnexpectedException)
            {
               fail("Activator " + names[i] + " caught an exception " + aException);
            }

            for (int j = 0; j < count; j++)
            {
               Fqn fqn = Fqn.fromRelativeElements(A_B, names[j]);
               assertEquals("Incorrect value for " + fqn + " on activator " + names[i],
                     "VALUE", activators[i].getCacheValue(fqn));
            }
         }
      }
      catch (Exception ex)
      {
         ex.printStackTrace();
         fail(ex.getLocalizedMessage());
      }
      finally
      {
         for (int i = 0; i < count; i++)
         {
            activators[i].cleanup();
         }
      }

   }

   private void waitTillAllReplicationsFinish(int count, CacheSPI[] caches)
         throws Exception
   {
      for (int i = 0; i < count; i++)
      {
         new ReplicationQueueNotifier(caches[i]).waitUntillAllReplicated(5000);
      }
   }

   /**
    * Starts two caches where each cache has N regions. We put some data in each of the regions.
    * We run two threads where each thread creates a cache then goes into a loop where it
    * activates the N regions, with a 1 sec pause between activations.
    * <p/>
    * Threads are started with 10 sec difference.
    * <p/>
    * This test simulates a 10 sec staggered start of 2 servers in a cluster, with each server
    * then deploying webapps.
    * <p/>
    * <p/>
    * <p/>
    * Failure condition is if any node sees an exception or if the final state
    * of all caches is not consistent.
    *
    * @param sync use REPL_SYNC or REPL_ASYNC
    * @throws Exception
    */
   private void concurrentActivationTest2(boolean sync)
   {
      String[] names = {"A", "B"};
      int cacheCount = names.length;
      int regionsToActivate = 3;
      int sleepTimeBetweenNodeStarts = 1000;
      StaggeredWebDeployerActivator[] activators = new StaggeredWebDeployerActivator[cacheCount];
      try
      {
         // Create a semaphore and take all its tickets
         Semaphore semaphore = new Semaphore(cacheCount);
         semaphore.acquire(cacheCount);

         // Create activation threads that will block on the semaphore
         CacheSPI[] caches = new CacheSPI[cacheCount];
         for (int i = 0; i < cacheCount; i++)
         {
            activators[i] = new StaggeredWebDeployerActivator(semaphore, names[i], sync, regionsToActivate);
            caches[i] = activators[i].getCacheSPI();

            // Release the semaphore to allow the thread to start working
            semaphore.release(1);

            activators[i].start();
            TestingUtil.sleepThread(sleepTimeBetweenNodeStarts);
         }

         // Make sure everyone is in sync
         TestingUtil.blockUntilViewsReceived(caches, 60000);

         // Sleep to ensure the threads get all the semaphore tickets
         TestingUtil.sleepThread(1000);

         // Reacquire the semaphore tickets; when we have them all
         // we know the threads are done
         for (int i = 0; i < cacheCount; i++)
         {
            boolean acquired = semaphore.tryAcquire(60, TimeUnit.SECONDS);
            if (!acquired)
            {
               fail("failed to acquire semaphore " + i);
            }
         }

         // Sleep to allow any async calls to clear
         if (!sync)
         {
            waitTillAllReplicationsFinish(cacheCount, caches);
         }

         // Ensure the caches held by the activators see all the values
         for (int i = 0; i < cacheCount; i++)
         {
            Exception aException = activators[i].getException();
            boolean gotUnexpectedException = aException != null
                  && !(aException instanceof InactiveRegionException ||
                  aException.getCause() instanceof InactiveRegionException);
            if (gotUnexpectedException)
            {
               fail("Activator " + names[i] + " caught an exception " + aException);
            }

            for (int j = 0; j < regionsToActivate; j++)
            {
               Fqn fqn = Fqn.fromString("/a/" + i + "/" + names[i]);
               assertEquals("Incorrect value for " + fqn + " on activator " + names[i],
                     "VALUE", activators[i].getCacheValue(fqn));
            }
         }
      }
      catch (Exception ex)
      {
         fail(ex.getLocalizedMessage());
      }
      finally
      {
         for (int i = 0; i < cacheCount; i++)
         {
            activators[i].cleanup();
         }
      }

   }

   /**
    * Starts two caches where each cache has N regions. We put some data in each of the regions.
    * We run two threads where each thread creates a cache then goes into a loop where it
    * activates the N regions, with a 1 sec pause between activations.
    * <p/>
    * Threads are started with 10 sec difference.
    * <p/>
    * This test simulates a 10 sec staggered start of 2 servers in a cluster, with each server
    * then deploying webapps.
    * <p/>
    * <p/>
    * <p/>
    * Failure condition is if any node sees an exception or if the final state
    * of all caches is not consistent.
    */
   public void testConcurrentStartupActivationAsync() throws Exception
   {
      concurrentActivationTest2(false);
   }

   /**
    * Starts two caches where each cache has N regions. We put some data in each of the regions.
    * We run two threads where each thread creates a cache then goes into a loop where it
    * activates the N regions, with a 1 sec pause between activations.
    * <p/>
    * Threads are started with 10 sec difference.
    * <p/>
    * This test simulates a 10 sec staggered start of 2 servers in a cluster, with each server
    * then deploying webapps.
    * <p/>
    * <p/>
    * <p/>
    * Failure condition is if any node sees an exception or if the final state
    * of all caches is not consistent.
    */
   public void testConcurrentStartupActivationSync() throws Exception
   {
      concurrentActivationTest2(true);
   }

   /**
    * Tests partial state transfer under heavy concurrent load and REPL_SYNC.
    * See <code>concurrentUseTest</code> for details.
    *
    * @throws Exception
    */
   public void testConcurrentUseSync() throws Exception
   {
      concurrentUseTest(true);
   }

   /**
    * Tests partial state transfer under heavy concurrent load and REPL_ASYNC.
    * See <code>concurrentUseTest</code> for details.
    *
    * @throws Exception
    */
   public void testConcurrentUseAsync() throws Exception
   {
      concurrentUseTest(false);
   }

   /**
    * Initiates 5 caches, 4 with active trees and one with an inactive tree.
    * Each of the active caches begins rapidly generating puts against nodes
    * in a subtree for which it is responsible. The 5th cache activates
    * each subtree, and at the end confirms no node saw any exceptions and
    * that each node has consistent state.
    *
    * @param sync whether to use REPL_SYNC or REPL_ASYNCE
    * @throws Exception
    */
   private void concurrentUseTest(boolean sync) throws Exception
   {
      String[] names = {"B"};
      int count = names.length;
      CacheStressor[] stressors = new CacheStressor[count];

      try
      {

         // The first cache we create is inactivated.
         CacheSPI<Object, Object> cacheA = createCache(sync, true, false);

         CacheSPI[] caches = new CacheSPI[count + 1];
         caches[0] = cacheA;

         // Create a semaphore and take all its tickets
         Semaphore semaphore = new Semaphore(count);
         semaphore.acquire(count);

         // Create stressor threads that will block on the semaphore

         for (int i = 0; i < count; i++)
         {
            stressors[i] = new CacheStressor(semaphore, names[i], sync);
            caches[i + 1] = stressors[i].getCacheSPI();
            stressors[i].start();
         }

         // Make sure everyone's views are in sync
         TestingUtil.blockUntilViewsReceived(caches, 60000);

         // Repeat the basic test four times
         //for (int x = 0; x < 4; x++)
         for (int x = 0; x < 1; x++)
         {

            // Reset things by inactivating the region
            // and enabling the stressors
            for (int i = 0; i < count; i++)
            {
               Region r = cacheA.getRegion(Fqn.fromString("/" + names[i]), true);
               r.registerContextClassLoader(getClass().getClassLoader());
               r.deactivate();
               stressors[i].startPuts();
            }

            // Release the semaphore to allow the threads to start work
            semaphore.release(count);

            // Sleep to ensure the threads get all the semaphore tickets
            // and to ensure puts are actively in progress
            TestingUtil.sleepThread((long) 1000);

            // Activate cacheA
            for (CacheStressor stressor : stressors)
            {
               cacheA.getRegion(Fqn.fromString("/" + stressor.getName()), true).activate();
               stressor.stopPuts();
               // Reacquire one semaphore ticket
               boolean acquired = semaphore.tryAcquire(60, TimeUnit.SECONDS);
               if (!acquired)
               {
                  fail("failed to acquire semaphore " + stressor.getName());
               }

               // Pause to allow other work to proceed
               TestingUtil.sleepThread(100);
            }

            // Sleep to allow any async calls to clear
            if (!sync)
            {
               waitTillAllReplicationsFinish(count, caches);
            }

            // Ensure the stressors saw no exceptions
            for (int i = 0; i < count; i++)
            {
               if (stressors[i].getException() != null && !(stressors[i].getException() instanceof InactiveRegionException))
               {
                  fail("Stressor " + names[i] + " caught an exception " + stressors[i].getException());
               }

            }

            // Compare cache contents
            for (int i = 0; i < count; i++)
            {
               for (int j = 0; j < SUBTREE_SIZE; j++)
               {
                  Fqn fqn = Fqn.fromString("/" + names[i] + "/" + j);
                  assertEquals("/A/" + j + " matches " + fqn,
                        cacheA.get(fqn, "KEY"),
                        stressors[i].getCacheSPI().get(fqn, "KEY"));
               }
            }
         }

         for (int i = 0; i < count; i++)
         {
            stressors[i].stopThread();
         }

      }
      finally
      {
         for (int i = 0; i < count; i++)
         {
            if (stressors[i] != null)
            {
               stressors[i].cleanup();
            }
         }
      }
   }

   /**
    * Test for JBCACHE-913
    */
   public void testEvictionSeesStateTransfer() throws Exception
   {
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC, false);
      additionalConfiguration(c);
      Cache<Object, Object> cache1 = new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
      caches.put("evict1", cache1);
      cache1.put(Fqn.fromString("/a/b/c"), "key", "value");

      c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC, true);
      additionalConfiguration(c);
      c.getEvictionConfig().setWakeupInterval(-1);
      Cache<Object, Object> cache2 = new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
      caches.put("evict2", cache2);

      RegionImpl region = (RegionImpl) cache2.getRegion(Fqn.ROOT, false);
      // We expect a VISIT event for / and ADD events for /a, /a/b and /a/b/c
      int nodeEventQueueSize = region.getEvictionEventQueue().size();
      assertTrue("Saw the expected number of node events", nodeEventQueueSize >= 4); //one event happens on read root
   }

   /**
    * Further test for JBCACHE-913
    */
   public void testEvictionAfterStateTransfer() throws Exception
   {
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC, false);
      additionalConfiguration(c);
      Cache<Object, Object> cache1 = new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
      caches.put("evict1", cache1);

      for (int i = 0; i < 10; i++)
      {
         cache1.put(Fqn.fromString("/org/jboss/test/data/" + i), "key", "data" + i);
      }

      assert cache1.getRoot().getChild(Fqn.fromString("/org/jboss/test/data/")).getChildren().size() == 10;

      c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC, true);
      c.getEvictionConfig().setWakeupInterval(-1);
      EvictionRegionConfig evictionRegionConfig = c.getEvictionConfig().getEvictionRegionConfig("/org/jboss/test/data");
      LRUAlgorithmConfig evictionAlgorithmConfig = (LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig();
      evictionAlgorithmConfig.setTimeToLive(-1);
      additionalConfiguration(c);
      final Cache<Object, Object> cache2 = new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
      EvictionController ec2 = new EvictionController(cache2);
      caches.put("evict2", cache2);

      assert cache2.getRoot().getChild(Fqn.fromString("/org/jboss/test/data/")).getChildren().size() == 10;
      ec2.startEviction();
      assert cache2.getRoot().getChild(Fqn.fromString("/org/jboss/test/data/")).getChildren().size() == 5;
   }

   private class CacheActivator extends CacheUser
   {
      CacheActivator(Semaphore semaphore, String name, boolean sync) throws Exception
      {
         super(semaphore, name, sync, false, 120000);
      }

      @SuppressWarnings("unchecked")
      void useCache() throws Exception
      {
         TestingUtil.sleepRandom(500);
         createAndActivateRegion(cache, A_B);
         Fqn childFqn = Fqn.fromRelativeElements(A_B, name);
         cache.put(childFqn, "KEY", "VALUE");
      }

      public Object getCacheValue(Fqn fqn) throws CacheException
      {
         return cache.get(fqn, "KEY");
      }
   }

   private class StaggeredWebDeployerActivator extends CacheUser
   {

      int regionCount = 15;

      StaggeredWebDeployerActivator(Semaphore semaphore, String name, boolean sync, int regionCount) throws Exception
      {
         super(semaphore, name, sync, false);
         this.regionCount = regionCount;
      }

      void useCache() throws Exception
      {
         for (int i = 0; i < regionCount; i++)
         {
            createAndActivateRegion(cache, Fqn.fromString("/a/" + i));
            Fqn childFqn = Fqn.fromString("/a/" + i + "/" + name);
            cache.put(childFqn, "KEY", "VALUE");
         }
      }

      public Object getCacheValue(Fqn fqn) throws CacheException
      {
         return cache.get(fqn, "KEY");
      }
   }

   private class CacheStressor extends CacheUser
   {
      private Random random = new Random(System.currentTimeMillis());
      private boolean putsStopped = false;
      private boolean stopped = false;

      CacheStressor(Semaphore semaphore,
                    String name,
                    boolean sync)
            throws Exception
      {
         super(semaphore, name, sync, true);
      }

      void useCache() throws Exception
      {
         // Do continuous puts into the cache.  Use our own nodes,
         // as we're not testing conflicts between writer nodes,
         // just whether activation causes problems
         int factor = 0;
         int i = 0;
         Fqn fqn = null;

         boolean acquired;
         while (!stopped)
         {
            if (i > 0)
            {
               acquired = semaphore.tryAcquire(60, TimeUnit.SECONDS);
               if (!acquired)
               {
                  throw new Exception(name + " cannot acquire semaphore");
               }
            }

            while (!putsStopped)
            {
               factor = random.nextInt(50);

               fqn = Fqn.fromString("/" + name + "/" + String.valueOf(factor % SUBTREE_SIZE));
               Integer value = factor / SUBTREE_SIZE;
               cache.put(fqn, "KEY", value);

               TestingUtil.sleepThread((long) factor);

               i++;
            }

            semaphore.release();

            // Go to sleep until directed otherwise
            while (!stopped && putsStopped)
            {
               TestingUtil.sleepThread((long) 100);
            }
         }
      }

      public void stopPuts()
      {
         putsStopped = true;
      }

      public void startPuts()
      {
         putsStopped = false;
      }

      public void stopThread()
      {
         stopped = true;
         if (thread.isAlive())
         {
            thread.interrupt();
         }
      }


   }
}
