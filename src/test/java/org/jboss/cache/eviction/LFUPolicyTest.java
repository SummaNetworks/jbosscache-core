/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests for LFU Policy.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7439 $
 */
@Test(groups = {"functional"}, testName = "eviction.LFUPolicyTest")
public class LFUPolicyTest extends EvictionTestsBase
{
   CacheSPI<Object, Object> cache;
   final String ROOT_STR = "/test";
   volatile Throwable t1_ex, t2_ex;
   volatile boolean isTrue;
   int maxNodesDefault = 500, minNodesDefault = 10, maxNodesR1 = 200, minNodesR1 = 100, maxNodesR2 = -1, minNodesR2 = 5;
   private EvictionController evController;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      initCaches();
      t1_ex = t2_ex = null;
      isTrue = true;
   }

   void initCaches() throws Exception
   {
      Configuration config = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      EvictionConfig evConfig = new EvictionConfig(new EvictionRegionConfig(Fqn.ROOT, new LFUAlgorithmConfig(maxNodesDefault, minNodesDefault), 200000), 0);
      evConfig.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/org/jboss/data"), new LFUAlgorithmConfig(maxNodesR1, minNodesR1)));
      evConfig.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/org/jboss/test/data"), new LFUAlgorithmConfig(maxNodesR2, minNodesR2)));
      config.setEvictionConfig(evConfig);
      config.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      config.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(config, true, getClass());
      evController = new EvictionController(cache);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   /* THIS TEST NEEDS REWRITING


   public void testEviction() throws Exception
   {
      int numNodes = (int) ((maxNodesR1 * 2.6) - 1);
      String rootStr = "/org/jboss/data/";
      List<Fqn> fqns = new ArrayList<Fqn>();
      for (int i = 0; i < numNodes; i += 2) fqns.add(Fqn.fromString(rootStr + i));
      EvictionWatcher ew = new EvictionWatcher(cache, fqns);
      List<Fqn> toRevisit = new ArrayList<Fqn>();
      for (int i = 0; i < numNodes; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         try
         {
            cache.put(fqn, str, str);
         }
         catch (Exception e)
         {
            fail("Failed to insert data" + e);
            e.printStackTrace();
         }

         // visit odd numbered nodes an extra time to make them get evicted last.
         if (i % 2 != 0) toRevisit.add(fqn);
         revisit(toRevisit);
      }

      assert ew.waitForEviction(30, TimeUnit.SECONDS);

      for (int i = 0; i < numNodes; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);

         if (i % 2 == 0)
         {
            assertNull(cache.get(fqn, str));
         }
         else
         {
            assertNotNull(cache.get(fqn, str));
         }
      }
   }

   private void revisit(List<Fqn> fqns)
   {
      for (Fqn fqn : fqns) cache.getNode(fqn);
   }*/

   public void testNodeVisited() throws InterruptedException
   {
      String rootStr = "/org/jboss/test/data/";

      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         try
         {
            cache.put(fqn, str, str);
         }
         catch (Exception e)
         {
            fail("Failed to insert data" + e);
            e.printStackTrace();
         }
      }

      evController.startEviction();

      try
      {
         for (int i = 0; i < 5; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            assertNull("Fqn " + fqn + " should be null", cache.get(fqn, str));
         }
         for (int i = 5; i < 10; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            assertNotNull(cache.get(fqn, str));
         }

         // since min is 5 the cache won't deplete past 5 nodes left in the cache.
         for (int i = 5; i < 10; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            assertNotNull(cache.get(fqn, str));
         }

         // now we add some more nodes and we selectively visit some older nodes but not all. they should not get
         // evicted when the thread next runs.
         for (int i = 5; i < 7; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            cache.get(fqn, str);
         }

         // add 2 more to push the limit to 5 so we cause the old unvisited nodes to get evicted.
         for (int i = 10; i < 13; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            cache.put(fqn, str, str);

            // now bring up their hit count for LFU purposes (cache hit count based eviction).
            for (int k = 0; k < 10; k++)
            {
               cache.get(fqn, str);
            }
         }

         evController.startEviction();

         // now make sure we still only have 5 nodes and they are the ones we expect based on LFU
         for (int i = 5; i < 7; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            assertNotNull(cache.get(fqn, str));
         }

         for (int i = 7; i < 10; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            assertNull(cache.get(fqn, str));
         }

         for (int i = 10; i < 13; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            assertNotNull(cache.get(fqn, str));
         }

      }
      catch (Exception e)
      {
         e.printStackTrace();
         fail("Failed to evict" + e);
      }
   }

   public void testNodeRemoved() throws Exception
   {
      String rootStr = "/org/jboss/data/";
      for (int i = 0; i < maxNodesR1; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         try
         {
            cache.put(fqn, str, str);
         }
         catch (Exception e)
         {
            fail("Failed to insert data" + e);
            e.printStackTrace();
         }
      }

      evController.startEviction();

      for (int i = 0; i < (maxNodesR1 - minNodesR1); i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         assertNull(cache.get(fqn, str));
      }

      for (int i = (maxNodesR1 - minNodesR1); i < maxNodesR1; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         assertNotNull(cache.get(fqn, str));
      }

      for (int i = (maxNodesR1 - minNodesR1); i < maxNodesR1; i++)
      {
         if (i % 2 == 0)
         {
            String str = rootStr + i;
            Fqn fqn = Fqn.fromString(str);
            cache.removeNode(fqn);
         }
      }

      evController.startEviction();


      for (int i = (maxNodesR1 - minNodesR1); i < maxNodesR1; i++)
      {
         if (i % 2 == 0)
         {
            String str = rootStr + i;
            Fqn fqn = Fqn.fromString(str);
            assertNull(cache.getNode(fqn));
         }
      }
   }


   class MyPutter extends Thread
   {

      public MyPutter(String name)
      {
         super(name);
      }

      public void run()
      {
         int i = 0;
         final String myName = ROOT_STR + "/test1/node" + getName();
         while (isTrue)
         {
            try
            {
               cache.put(myName + i++, "value", i);
               sleep(1);
            }
            catch (Throwable e)
            {
//               e.printStackTrace();
               System.out.println("Got exception:" + e);
               if (t1_ex == null)
               {
                  isTrue = false;
                  t1_ex = e;
               }
            }
         }
      }
   }
   //todo mmarkus create a test profile to use greater values for thread count below
   public void testConcurrentPutAndEvict() throws Exception
   {
      cache.stop();
      cache.destroy();
      cache.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      cache.start();
      cache.put(ROOT_STR + "/concurrentPutAndEvict", "value", 1);

      for (int i = 0; i < 5; i++)
      {
         new MyPutter("LFUPolicyTestPutter" + i).start();
      }

      int counter = 0;
      while (true)
      {
         counter++;
         if (t1_ex != null)
         {
            isTrue = false;
            fail("Exception generated in put() " + t1_ex);
         }
         TestingUtil.sleepThread(250);
         if (counter > 5)
         {// run for 5 seconds
            isTrue = false;
            break;
         }
      }
   }
}
