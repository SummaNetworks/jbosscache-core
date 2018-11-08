package org.jboss.cache.eviction;


import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import org.jboss.cache.util.internals.EvictionWatcher;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Unit tests for LRU Policy.
 *
 * @author Ben Wang, Feb 11, 2004
 * @author Daniel Huang - dhuang@jboss.org
 * @version $Revision: 7439 $
 */
@Test(groups = "functional", testName = "eviction.LRUPolicyTest")
public class LRUPolicyTest extends EvictionTestsBase
{
   CacheSPI<Object, Object> cache;
   long wakeupIntervalMillis = 1000;
   long dataRegionTTLMillis = 1000;
   long testRegionTTLMillis = 1000;
   private int baseRegionMaxNodes = 10;
   private int baseRegionTTLMillis = 1000;

   final String ROOT_STR = "/test";
   volatile Throwable t1_ex, t2_ex;
   volatile boolean isTrue;


   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      EvictionConfig evConfig = conf.getEvictionConfig();
      evConfig.setWakeupInterval(wakeupIntervalMillis);
      List<EvictionRegionConfig> regionConfigs = new ArrayList<EvictionRegionConfig>();
      regionConfigs.add(new EvictionRegionConfig(Fqn.fromString("/org/jboss/test/data"), new LRUAlgorithmConfig(dataRegionTTLMillis, -1, 5)));
      regionConfigs.add(new EvictionRegionConfig(Fqn.fromString("/test"), new LRUAlgorithmConfig(testRegionTTLMillis, 10000)));
      regionConfigs.add(new EvictionRegionConfig(Fqn.fromString("/base"), new LRUAlgorithmConfig(baseRegionTTLMillis, -1, baseRegionMaxNodes)));
      evConfig.setEvictionRegionConfigs(regionConfigs);
      conf.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      conf.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(conf, getClass());

      wakeupIntervalMillis = cache.getConfiguration().getEvictionConfig().getWakeupInterval();
      if (wakeupIntervalMillis < 0)
      {
         fail("testEviction(): eviction thread wake up interval is illegal " + wakeupIntervalMillis);
      }

      t1_ex = t2_ex = null;
      isTrue = true;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testInUseEviction() throws Exception
   {
      String rootStr = "/org/jboss/test/data/inuse/";
      Fqn fqn;

      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i;
         fqn = Fqn.fromString(str);
         cache.put(fqn, str, str);
      }

      cache.getRegionManager().getRegion(Fqn.fromString(rootStr + 5), false).markNodeCurrentlyInUse(Fqn.fromString(rootStr + 5), 0);

      for (int i = 10; i < 15; i++)
      {
         String str = rootStr + i;
         fqn = Fqn.fromString(str);
         cache.put(fqn, str, str);
      }

      new EvictionController(cache).startEviction();

      for (int i = 0; i < 5; i++)
      {
         Fqn f = Fqn.fromString(rootStr + i);
         assert null == cache.getNode(f) : f + " should be null";
      }

      assertNotNull(cache.getNode(Fqn.fromString(rootStr + 5)));

      for (int i = 6; i < 11; i++)
      {
         Fqn f = Fqn.fromString(rootStr + i);
         assert null == cache.getNode(f) : f + " should be null";
      }
      for (int i = 11; i < 15; i++)
      {
         Fqn f = Fqn.fromString(rootStr + i);
         assert null != cache.getNode(f) : f + " should not be null";
      }
   }

   public void testEviction()
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
      TestingUtil.sleepThread(wakeupIntervalMillis + 500);
      String val = (String) cache.get(rootStr + "3", rootStr + "3");
      assertNull("Node should be empty ", val);
   }

   public void testNodeVisited() throws InterruptedException
   {
      String rootStr = "/org/jboss/test/data/";

      final Fqn fqn7 = Fqn.fromString(rootStr + "7");

      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         cache.put(fqn, str, str);

         // playing favourites here:
         cache.getNode(fqn7);// just to keep it fresh
      }

      Thread retrieverThread = new Thread()
      {
         @Override
         public void run()
         {
            try
            {
               while (true)
               {
                  cache.getNode(fqn7);// just to keep it fresh
                  sleep(50);
               }
            }
            catch (Exception ie)
            {
            }
         }
      };

      retrieverThread.setDaemon(true);
      retrieverThread.start();
      assert waitForEviction(cache, 30, TimeUnit.SECONDS, Fqn.fromString(rootStr + 3));
      String val = (String) cache.get(rootStr + "3", rootStr + "3");
      assertNull("Node should be empty ", val);
      Node n = cache.getNode(fqn7);
      assert n != null;
      val = (String) n.get(rootStr + "7");
      assertNotNull("Node should not be empty ", val);
      retrieverThread.interrupt();
      retrieverThread.join();
      new EvictionController(cache).startEviction(true);
      assert waitForEviction(cache, 30, TimeUnit.SECONDS, Fqn.fromString(rootStr + 7));
      val = (String) cache.get(rootStr + "7", rootStr + "7");
      assertNull("Node should be empty ", val);
   }

   public void testNodeRemoved()
   {
      String rootStr = "/org/jboss/test/data/";
      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i + "/" + i;
         Fqn fqn = Fqn.fromString(str);
         cache.put(fqn, str, str);
      }

      String str1 = rootStr + "7";
      Fqn fqn1 = Fqn.fromString(str1);
      String str2 = rootStr + "7/7";
      Fqn fqn2 = Fqn.fromString(str2);
      cache.get(fqn1, str1);// just to keep it fresh
      cache.get(fqn2, str2);// just to keep it fresh
      new EvictionController(cache).startEviction();
      cache.get(fqn1, str1);// just to keep it fresh
      cache.get(fqn2, str2);// just to keep it fresh
      new EvictionController(cache).startEviction();
      String val = (String) cache.get(rootStr + "7/7", rootStr + "7/7");
      assertNotNull("Node should not be empty ", val);
      cache.removeNode(fqn1);
      TestingUtil.sleepThread(wakeupIntervalMillis + 500);
      new EvictionController(cache).startEviction();
      val = (String) cache.get(rootStr + "7/7", rootStr + "7/7");
      assertNull("Node should be empty ", val);
   }

   public void testCompleteRemoval() throws Exception
   {
      String rootStr = "/test/";

      // Add a parent, then a child. LRU will evict the parent,
      // then the child, leaving behind an empty parent
      Fqn parent = Fqn.fromString(rootStr + "parent");
      cache.put(parent, "key", "value");
      cache.put(Fqn.fromRelativeElements(parent, "child"), "key", "value");

      // Give eviction time to run a few times, then confirm parent
      // is completely gone
      long period = (wakeupIntervalMillis + testRegionTTLMillis) * 2;
      TestingUtil.sleepThread(period);
      assertFalse("Parent not completely removed", cache.getRoot().hasChild(parent));
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
            catch (IllegalStateException ise)
            {
               // this will happen since some threads are bound to continue running while the test stops.
               // do nothing.
            }
            catch (Throwable e)
            {
               System.out.println("Got exception: " + e);
               if (t1_ex == null)
               {
                  isTrue = false;
                  t1_ex = e;
               }
            }
         }
      }
   }


   public void testConcurrentPutAndEvict() throws Exception
   {
      cache.stop();
      cache.destroy();
      cache.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      cache.create();
      cache.start();
      cache.put(ROOT_STR + "/concurrentPutAndEvict", "value", 1);

      for (int i = 0; i < 10; i++)
      {
         new MyPutter("LRUPolicyTestPutter" + i).start();
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
         TestingUtil.sleepThread(1000);
         if (counter > 5)
         {// run for 5 seconds
            isTrue = false;
            break;
         }
      }
   }

   public void testForEvictionInternalError()
   {
      String rootStr = "/test/testdata";

      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         cache.put(fqn, str, str);
      }

      // wait for an eviction
      TestingUtil.sleepThread(2 * (wakeupIntervalMillis + testRegionTTLMillis));

      String val = (String) cache.get(rootStr + "3", rootStr + "3");
      assertNull("Node should be empty ", val);

      // reinsert the elements
      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         cache.put(fqn, str, str);
      }

      // clear the root
      cache.removeNode(Fqn.ROOT);

      // wait for an eviction
      TestingUtil.sleepThread(2 * wakeupIntervalMillis + 1000);

      val = (String) cache.get(rootStr + "3", rootStr + "3");
      assertNull("Node should be empty ", val);
   }

   public void testOvereviction() throws Exception
   {
      Node node = cache.getRoot().addChild(Fqn.fromString("/base/"));
      node.setResident(true);
      cache.getRoot().setResident(true);

      EvictionWatcher ew = new EvictionWatcher(cache, Fqn.fromString("/base/1"));

      for (int i = 1; i < baseRegionMaxNodes + 2; i++)
      {
         Fqn f = Fqn.fromString("/base/" + i);
         cache.put(f, "key", "base" + i);
      }

      new EvictionController(cache).startEviction();
      assert ew.waitForEviction(30, TimeUnit.SECONDS);

      assertEquals(baseRegionMaxNodes, cache.getRoot().getChild(Fqn.fromString("/base")).getChildren().size());

   }
}
