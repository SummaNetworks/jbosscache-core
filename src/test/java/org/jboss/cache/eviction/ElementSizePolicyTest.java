/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.jboss.cache.*;
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

/**
 * @author Daniel Huang
 * @version $Revison: $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.ElementSizePolicyTest")
public class ElementSizePolicyTest extends EvictionTestsBase
{
   CacheSPI<Object, Object> cache;
   long wakeupIntervalMillis = 0;
   final String ROOT_STR = "/test";
   Throwable t1_ex, t2_ex;
   final long DURATION = 10000;
   boolean isTrue;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      initCaches();
      wakeupIntervalMillis = cache.getConfiguration().getEvictionConfig().getWakeupInterval();
      t1_ex = t2_ex = null;
      isTrue = true;
   }

   void initCaches() throws Exception
   {
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      EvictionConfig evConfig = new EvictionConfig(new EvictionRegionConfig(Fqn.ROOT, new ElementSizeAlgorithmConfig(5000, 100), 200000), -1);
      evConfig.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/org/jboss/data"), new ElementSizeAlgorithmConfig(10, 20)));
      evConfig.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/org/jboss/test/data"), new ElementSizeAlgorithmConfig(-1, 5)));
      evConfig.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/test/"), new ElementSizeAlgorithmConfig(5000, 1)));
      conf.setEvictionConfig(evConfig);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(conf, false, getClass());
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setIsolationLevel(IsolationLevel.SERIALIZABLE);
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testEviction() throws Exception
   {
      String rootStr = "/org/jboss/test/data/";
      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         try
         {
            cache.put(fqn, str, str);
            if (i % 2 == 0)
            {
               for (int k = 0; k < i; k++)
               {
                  cache.put(fqn, k, Integer.toString(k));
               }
            }
         }
         catch (Exception e)
         {
            fail("Failed to insert data" + e);
            e.printStackTrace();
         }
      }

      EvictionController evController = new EvictionController(cache);
      evController.startEviction();

      TestingUtil.sleepThread(200); // small grace period

      for (int i = 0; i < 10; i++)
      {
         String f = "/org/jboss/test/data/" + i;
         Node node = cache.getNode(f);
         if (i % 2 == 0)
         {
            if (i < 6)
            {
               int numElements = ((NodeSPI) node).getDataDirect().size();
               assertEquals(i + 1, numElements);
            }
            else
            {
               assertNull(node);
            }
         }
         else
         {
            assertEquals(1, ((NodeSPI) node).getDataDirect().size());
         }
      }
   }

   public void testEviction2() throws Exception
   {
      List<Fqn> fqnsThatShouldBeEvicted = new ArrayList<Fqn>();
      for (int i = 10; i < 20; i++) fqnsThatShouldBeEvicted.add(Fqn.fromString("/org/jboss/data/" + i));

      EvictionController evictionController = new EvictionController(cache);
      String rootStr = "/org/jboss/data/";
      for (int i = 0; i < 20; i++)
      {
         String str = rootStr + Integer.toString(i);
         Fqn fqn = Fqn.fromString(str);//"/org/jboss/data/i";
         cache.put(fqn, i, str);
         for (int k = 0; k < i; k++)
         {
            cache.put(fqn, k, str);
         }
      }

      evictionController.startEviction();

      for (int i = 0; i < 20; i++)
      {
         String str = rootStr + Integer.toString(i);
         Fqn fqn = Fqn.fromString(str);
         Node node = cache.getNode(fqn);
         if (i > 9)
         {
            assertNull("Testing at " + i, node);
         }
         else
         {
            assertEquals(1 + i, node.getData().size());
         }
      }

      for (int i = 0; i < 17; i++)
      {
         cache.put("/org/jboss/data/3", 100 + i, "value");
      }

      Node node = cache.getNode("/org/jboss/data/3");
      assertEquals(21, node.getData().size());

      evictionController.startEviction();
      TestingUtil.sleepThread(200); // small grace period

      assertNull(cache.getNode("/org/jboss/data/3"));
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
            }
            catch (Throwable e)
            {
               e.printStackTrace();
               if (t1_ex == null)
               {
                  t1_ex = e;
               }
            }
         }
      }
   }
}
