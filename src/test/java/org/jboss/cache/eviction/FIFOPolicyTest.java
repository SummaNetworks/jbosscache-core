/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import org.jboss.cache.UnitTestCacheFactory;

/**
 * Unit tests for FIFOPolicy.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7439 $
 */
@Test(groups = {"functional"}, testName = "eviction.FIFOPolicyTest")
public class FIFOPolicyTest extends EvictionTestsBase
{
   CacheSPI<Object, Object> cache;
   long wakeupIntervalMillis = 0;
   final String ROOT_STR = "/test";
   volatile Throwable t1_ex, t2_ex;
   volatile boolean isTrue;
   int maxNodes = 50;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      initCaches();
      wakeupIntervalMillis = cache.getConfiguration().getEvictionConfig().getWakeupInterval();
      if (wakeupIntervalMillis < 0)
      {
         fail("testEviction(): eviction thread wake up interval is illegal " + wakeupIntervalMillis);
      }

      t1_ex = t2_ex = null;
      isTrue = true;
   }

   void initCaches() throws Exception
   {
      Configuration config = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      EvictionConfig evConfig = new EvictionConfig(new EvictionRegionConfig(Fqn.ROOT, new FIFOAlgorithmConfig(maxNodes), 2000000), 0);
      evConfig.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/org/jboss/test/data"), new FIFOAlgorithmConfig(5)));
      config.setEvictionConfig(evConfig);
      config.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      config.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(config, true, getClass());// read in generic local xml
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
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
      EvictionController evictionController = new EvictionController(cache);
      evictionController.startEviction();

      try
      {
         String val = (String) cache.get(rootStr + "3", rootStr + "3");
         assertNull("DataNode should be empty ", val);
         assertNull(cache.get(rootStr + "1", rootStr + "1"));
         assertNull(cache.get(rootStr + "2", rootStr + "2"));
         assertNull(cache.get(rootStr + "0", rootStr + "0"));
         assertNull(cache.get(rootStr + "4", rootStr + "4"));

         assertNotNull(cache.get(rootStr + "5", rootStr + "5"));
      }
      catch (Exception e)
      {
         e.printStackTrace();
         fail("Failed to get" + e);
      }
   }

   public void testEviction2() throws Exception
   {
      String rootStr = "/org/jboss/data";
      for (int i = 0; i < maxNodes * 2; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         cache.put(fqn, str, str);
      }

      EvictionController evictionController = new EvictionController(cache);
      evictionController.startEviction();
      // wait a few secs for eviction to complete
//      TestingUtil.sleepThread(500);
      assertEquals("Number of nodes", maxNodes + 2, cache.getNumberOfNodes());
      for (int i = 0; i < maxNodes; i++)
      {
         String n = rootStr + i;
         assertNull("DataNode should be empty " + cache.getNode(n) + " " + n,
               cache.get(n, n));
      }

      for (int i = maxNodes; i < maxNodes * 2; i++)
      {
         assertNotNull(cache.get(rootStr + Integer.toString(i), rootStr + Integer.toString(i)));
      }

      // add another node to cache. this should push node 5000 out of cache.
      cache.put(rootStr + "a", rootStr + "a", rootStr + "a");
      for (int i = maxNodes + 1; i < maxNodes; i++)
      {
         assertNotNull(cache.get(rootStr + Integer.toString(i), rootStr + Integer.toString(i)));
      }

      assertNotNull(cache.get(rootStr + "a", rootStr + "a"));
   }

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

      EvictionController evictionController = new EvictionController(cache);
      evictionController.startEviction();
      try
      {
         for (int i = 0; i < 5; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            assertNull(cache.get(fqn, str));
         }
         for (int i = 5; i < 10; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            assertNotNull(cache.get(fqn, str));
         }

         // since it is FIFO if we leave it alone and revisit, cache should remain the same.
         for (int i = 5; i < 10; i++)
         {
            String str = rootStr + Integer.toString(i);
            Fqn fqn = Fqn.fromString(str);
            cache.get(fqn, str);// just to keep it fresh
         }
      }
      catch (Exception e)
      {
         e.printStackTrace();
         fail("Failed to evict" + e);
      }
   }

   public void testNodeRemoved()
   {
      String rootStr = "/org/jboss/test";
      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i + "/" + i;
         Fqn fqn = Fqn.fromString(str);
         String str2 = rootStr + i;
         Fqn fqn2 = Fqn.fromString(str2);
         try
         {
            cache.put(fqn, str, str);
            cache.put(fqn2, str2, str2);
         }
         catch (Exception e)
         {
            fail("Failed to insert data" + e);
            e.printStackTrace();
         }
      }

      EvictionController evictionController = new EvictionController(cache);
      evictionController.startEviction();

      String str1 = rootStr + "7";
      Fqn fqn1 = Fqn.fromString(str1);
      String str2 = rootStr + "7/7";
      Fqn fqn2 = Fqn.fromString(str2);
      try
      {
         assertNotNull(cache.get(fqn2, str2));
         assertNotNull(cache.get(fqn1, str1));
         cache.removeNode(fqn2);
         evictionController.startEviction();
         assertNull(cache.get(fqn2, str2));
         assertNotNull(cache.get(fqn1, str1));
         cache.removeNode(fqn1);
         evictionController.startEviction();
         assertNull(cache.get(fqn1, str1));
         assertNull(cache.get(fqn2, str2));

         String str3 = rootStr + "5/5";
         String str4 = rootStr + "5";
         Fqn fqn3 = Fqn.fromString(str3);
         Fqn fqn4 = Fqn.fromString(str4);
         assertNotNull(cache.get(fqn3, str3));
         assertNotNull(cache.get(fqn4, str4));

         evictionController.startEviction();

         // remove the node above fqn4 /org/jboss/test/5 will cascade the delete into /org/jboss/test/5/5
         cache.removeNode(fqn4);

         evictionController.startEviction();
         assertNull(cache.get(fqn3, str3));
         assertNull(cache.get(fqn4, str4));
      }
      catch (Exception e)
      {
         e.printStackTrace();
         fail("Failed to evict" + e);
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
               TestingUtil.sleepThread(2);
            }
            catch (Throwable e)
            {
               e.printStackTrace();
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
      cache.put(ROOT_STR + "/concurrentPutAndEvict", "value", 1);
      List<MyPutter> putters = new ArrayList<MyPutter>();
      for (int i = 0; i < 5; i++)
      {
         MyPutter p = new MyPutter("FifoPolicyTestPutter" + i);
         putters.add(p);
         p.start();
      }

      int counter = 0;
      while (true)
      {
         counter++;
         if (t1_ex != null)
         {
            fail("Exception generated in put() " + t1_ex);
         }
         TestingUtil.sleepThread(250);
         if (counter > 5)
         {// run for 5 seconds
            isTrue = false;
            break;
         }
      }

      for (MyPutter p : putters) p.join();
   }
}
