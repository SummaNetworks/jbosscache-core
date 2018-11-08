package org.jboss.cache.eviction;


import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.internals.EvictionWatcher;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

@Test(groups = {"functional"}, sequential = true, testName = "eviction.NullEvictionPolicyTest")
public class NullEvictionPolicyTest extends EvictionTestsBase
{
   CacheSPI<Object, Object> cache;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = null;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }


   /**
    * Builds a cache was null eviction by default and in "/test" region,
    * LRU in "/lru" region.  Does a mix of puts/reads/removes, wakes for
    * eviction thread to kick in, checks that nothing was evicted from the
    * null policy regions but was from lru region.
    */
   public void testEviction() throws InterruptedException
   {
      Configuration config = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      EvictionConfig evConfig = new EvictionConfig(new EvictionRegionConfig(Fqn.ROOT, new NullEvictionAlgorithmConfig(), 200000), 200);
      evConfig.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/test"), new NullEvictionAlgorithmConfig()));
      evConfig.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/lru"), new LRUAlgorithmConfig(1000, 10000)));
      config.setEvictionConfig(evConfig);
      config.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      config.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(config, getClass());

      String dfltRootStr = "/a/";
      String testRootStr = "/test/";
      String lruRootStr = "/lru/";

      List<Fqn> toBeEvicted = new ArrayList<Fqn>();
      for (int i = 0; i < 20; i++) toBeEvicted.add(Fqn.fromString(lruRootStr + i));
      EvictionWatcher watcher = new EvictionWatcher(cache, toBeEvicted);
      for (int i = 0; i < 20; i++)
      {
         Fqn dflt = Fqn.fromString(dfltRootStr + i);
         Fqn test = Fqn.fromString(testRootStr + i);
         Fqn lru = Fqn.fromString(lruRootStr + i);
         cache.put(dflt, "key", "value");
         cache.put(test, "key", "value");
         cache.put(lru, "key", "value");
      }

      assert watcher.waitForEviction(30, TimeUnit.SECONDS);

      for (int i = 0; i < 20; i++)
      {
         Fqn dflt = Fqn.fromString(dfltRootStr + i);
         Fqn test = Fqn.fromString(testRootStr + i);
         Fqn lru = Fqn.fromString(lruRootStr + i);

         assertEquals("value", cache.get(dflt, "key"));
         assertEquals("value", cache.get(test, "key"));
         assertNull(cache.get(lru, "key"));
      }
   }
}
