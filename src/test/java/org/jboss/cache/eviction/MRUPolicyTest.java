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
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests for MRUPolicy.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7439 $
 */
@Test(groups = {"functional"}, testName = "eviction.MRUPolicyTest")
public class MRUPolicyTest
{
   CacheSPI<Object, Object> cache;
   long wakeupIntervalMillis = 0;
   final String ROOT_STR = "/test";
   volatile Throwable t1_ex, t2_ex;
   volatile boolean isTrue;

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

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   private void initCaches()
   {
      Configuration config = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      EvictionConfig evConfig = config.getEvictionConfig();
      evConfig.setWakeupInterval(200);
      // root ERC
      evConfig.setDefaultEvictionRegionConfig(new EvictionRegionConfig(Fqn.ROOT, new MRUAlgorithmConfig(100), 200000));
      // new region ERC
      evConfig.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/org/jboss/test/data"), new MRUAlgorithmConfig(6)));


      config.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      config.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(config, getClass());
   }

   public void testEviction() throws Exception
   {
      cache.put("/org/jboss/test/data/a", "/org/jboss/test/data/a", "/org/jboss/test/data/a");
      cache.put("/org/jboss/test/data/b", "/org/jboss/test/data/b", "/org/jboss/test/data/b");
      cache.put("/org/jboss/test/data/c", "/org/jboss/test/data/c", "/org/jboss/test/data/c");
      cache.put("/org/jboss/test/data/d", "/org/jboss/test/data/d", "/org/jboss/test/data/d");
      cache.put("/org/jboss/test/data/e", "/org/jboss/test/data/e", "/org/jboss/test/data/e");

      EvictionController ec = new EvictionController(cache);
      ec.startEviction();

      cache.put("/org/jboss/test/data/f", "/org/jboss/test/data/f", "/org/jboss/test/data/f");
      cache.put("/org/jboss/test/data/g", "/org/jboss/test/data/g", "/org/jboss/test/data/g");
      cache.put("/org/jboss/test/data/h", "/org/jboss/test/data/h", "/org/jboss/test/data/h");
      assertNotNull(cache.get("/org/jboss/test/data/a", "/org/jboss/test/data/a"));
      assertNotNull(cache.get("/org/jboss/test/data/b", "/org/jboss/test/data/b"));

      ec.startEviction();

      assertNull(cache.get("/org/jboss/test/data/a", "/org/jboss/test/data/a"));
      assertNull(cache.get("/org/jboss/test/data/b", "/org/jboss/test/data/b"));
   }

   public void testNodeRemoved() throws Exception
   {
      cache.put("/org/jboss/test/data/a", "/org/jboss/test/data/a", "/org/jboss/test/data/a");
      cache.put("/org/jboss/test/data/b", "/org/jboss/test/data/b", "/org/jboss/test/data/b");
      cache.put("/org/jboss/test/data/c", "/org/jboss/test/data/c", "/org/jboss/test/data/c");
      cache.put("/org/jboss/test/data/d", "/org/jboss/test/data/d", "/org/jboss/test/data/d");
      cache.put("/org/jboss/test/data/e", "/org/jboss/test/data/e", "/org/jboss/test/data/e");

      EvictionController ec = new EvictionController(cache);
      ec.startEviction();


      cache.removeNode("/org/jboss/test/data/d");
      cache.removeNode("/org/jboss/test/data/e");
      cache.put("/org/jboss/test/data/f", "/org/jboss/test/data/f", "/org/jboss/test/data/f");
      cache.put("/org/jboss/test/data/g", "/org/jboss/test/data/g", "/org/jboss/test/data/g");

      ec.startEviction();

      assertNull(cache.get("/org/jboss/test/data/d", "/org/jboss/test/data/d"));
      assertNull(cache.get("/org/jboss/test/data/e", "/org/jboss/test/data/e"));

      assertNotNull(cache.get("/org/jboss/test/data/f", "/org/jboss/test/data/f"));
      assertNotNull(cache.get("/org/jboss/test/data/g", "/org/jboss/test/data/g"));
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
               TestingUtil.sleepThread(1);
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
      cache.stop();
      cache.destroy();
      cache.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      cache.start();
      cache.put(ROOT_STR + "/concurrentPutAndEvict", "value", 1);

      for (int i = 0; i < 5; i++)
      {
         new MyPutter("MRUPolicyTestPutter" + i).start();
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
   }
}
