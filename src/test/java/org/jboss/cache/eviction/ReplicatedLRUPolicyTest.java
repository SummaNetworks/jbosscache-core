package org.jboss.cache.eviction;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeEvicted;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import org.jboss.cache.util.internals.EvictionWatcher;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * @author Ben Wang, Feb 11, 2004
 */
@Test(groups = {"functional"}, testName = "eviction.ReplicatedLRUPolicyTest")
public class ReplicatedLRUPolicyTest extends EvictionTestsBase
{
   CacheSPI<Object, Object> cache1, cache2;
   EvictionListener listener = new EvictionListener();

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC, true), false, getClass());
      cache1.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache1.getConfiguration().setUseRegionBasedMarshalling(true);
      cache1.getConfiguration().getEvictionConfig().setWakeupInterval(0);

      cache1.start();
      cache1.getNotifier().addCacheListener(listener);
      listener.resetCounter();

      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(cache1.getConfiguration().clone(), false, getClass());
      cache2.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache2.getConfiguration().setUseRegionBasedMarshalling(true);
      cache2.getConfiguration().getEvictionConfig().setWakeupInterval(0);
      cache2.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   /**
    * Test local eviction policy that failed for eviction event.
    */
   public void testBasic() throws Exception
   {
      String rootStr = "/org/jboss/test/data/";
      LRUAlgorithmConfig cfg = (LRUAlgorithmConfig) cache1.getConfiguration().getEvictionConfig().getEvictionRegionConfig(rootStr).getEvictionAlgorithmConfig();
      cfg.setMaxAge(0);
      cfg.setTimeToLive(0);

      cfg = (LRUAlgorithmConfig) cache2.getConfiguration().getEvictionConfig().getEvictionRegionConfig(rootStr).getEvictionAlgorithmConfig();
      cfg.setMaxAge(-1, TimeUnit.SECONDS);
      cfg.setTimeToLive(-1, TimeUnit.SECONDS);
      cfg.setMaxNodes(200);

      String str = rootStr + "0";
      Fqn fqn = Fqn.fromString(str);
      cache1.put(str, str, str);

      new EvictionController(cache1).startEviction();
      Object node = cache1.peek(fqn, false);
      assertNull("Node should be evicted already ", node);
      assertEquals("Eviction counter ", 1, listener.getCounter());
      String val = (String) cache2.get(str, str);
      assertNotNull("DataNode should not be evicted here ", val);
      assertEquals("Eviction counter ", 1, listener.getCounter());
   }

   public void testEviction() throws Exception
   {
      String rootStr = "/org/jboss/test/data/";
      LRUAlgorithmConfig cfg = (LRUAlgorithmConfig) cache2.getConfiguration().getEvictionConfig().getEvictionRegionConfig(rootStr).getEvictionAlgorithmConfig();
      cfg.setMaxAge(60, TimeUnit.SECONDS);
      cfg.setTimeToLive(360, TimeUnit.SECONDS);
      cfg.setMaxNodes(200);
      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         cache1.put(fqn, str, str);
      }

      new EvictionController(cache1).startEviction();

      String val = (String) cache1.get(rootStr + "3", rootStr + "3");
      assertNull("DataNode should be evicted already ", val);
      val = (String) cache2.get(rootStr + "3", rootStr + "3");
      assertNotNull("DataNode should not be evicted here ", val);
   }

   public void testEvictionReplication() throws Exception
   {
      String rootStr = "/org/jboss/test/data/";
      LRUAlgorithmConfig cfg = (LRUAlgorithmConfig) cache2.getConfiguration().getEvictionConfig().getEvictionRegionConfig(rootStr).getEvictionAlgorithmConfig();
      cfg.setMaxAge(60, TimeUnit.SECONDS);
      cfg.setTimeToLive(360, TimeUnit.SECONDS);

      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         cache1.put(fqn, str, str);
      }

      String str = rootStr + "7";
      Fqn fqn = Fqn.fromString(str);
      cache1.get(fqn, str);

      new EvictionController(cache1).startEviction();

      String val = (String) cache1.get(rootStr + "3", rootStr + "3");
      assertNull("DataNode should be empty ", val);
      val = (String) cache2.get(rootStr + "7", rootStr + "7");
      assertNotNull("DataNode should not be null", val);
   }

   @CacheListener
   public class EvictionListener
   {
      int counter = 0;

      public int getCounter()
      {
         return counter;
      }

      public void resetCounter()
      {
         counter = 0;
      }

      @NodeEvicted
      public void nodeEvicted(Event e)
      {
         if (e.isPre()) counter++;
      }
   }
}
