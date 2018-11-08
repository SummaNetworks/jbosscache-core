package org.jboss.cache.eviction.minttl;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.EvictionAlgorithmConfigBase;
import org.jboss.cache.eviction.EvictionTestsBase;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This test exercises the minimum time to live for any element in the cache
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional"}, sequential = true)
public abstract class MinTTLTestBase extends EvictionTestsBase
{
   // this should ideally be in an eviction test base class so all eviction policies can be tested

   protected Cache<Object, Object> cache;
   protected Fqn region = Fqn.fromString("/test-region");
   protected Fqn fqn = Fqn.fromRelativeElements(region, "a");
   // allows the test methods to notify any support threads in subclasses that data is in the cache and the test is about to begin
   protected volatile CountDownLatch cacheInitialisedLatch;

   protected abstract EvictionAlgorithmConfigBase getEvictionAlgorithmConfig();

   @BeforeMethod
   public void setUp()
   {
      cacheInitialisedLatch = new CountDownLatch(1);

      // the LRU policy cfg
      EvictionAlgorithmConfigBase cfg = getEvictionAlgorithmConfig();

      // the region configuration
      EvictionRegionConfig regionCfg = new EvictionRegionConfig();
      regionCfg.setRegionFqn(region);
      regionCfg.setRegionName(region.toString());
      regionCfg.setEvictionAlgorithmConfig(cfg);
      // cache-wide
      EvictionConfig ec = new EvictionConfig();
      ec.setWakeupInterval(200);
      ec.addEvictionRegionConfig(regionCfg);

      cache = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setEvictionConfig(ec);
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   @Test(invocationCount = 5, successPercentage = 80)
   public void testNoMinimumTTL() throws InterruptedException
   {
      cache.start();
      cache.put(fqn, "k", "v");
      // in case any waiting threads in subclasses are waiting for the cache to be initialised
      cacheInitialisedLatch.countDown();

      assert cache.get(fqn, "k") != null : "Node should be in the cache";

      assert waitForEviction(cache, 10, TimeUnit.SECONDS, fqn);

      assert cache.get(fqn, "k") == null : "Node should have been evicted";
   }

   public void testWithMinimumTTL() throws InterruptedException
   {
      ((EvictionAlgorithmConfigBase) cache.getConfiguration().getEvictionConfig().getEvictionRegionConfigs().get(0).getEvictionAlgorithmConfig()).setMinTimeToLive(500);

      cache.start();
      cache.put(fqn, "k", "v");
      // in case any waiting threads in subclasses are waiting for the cache to be initialised
      cacheInitialisedLatch.countDown();

      assert cache.get(fqn, "k") != null : "Node should be in the cache";

      new EvictionController(cache).startEviction();

      assert cache.get(fqn, "k") != null : "Node should still be in cache due to a minTTL of 3 secs";

      // the last cache.get() would have updated the last modified tstamp so we need to wait at least 3 secs (+1 sec maybe for the eviction thread)
      // to make sure this is evicted.
      new EvictionController(cache).startEviction(true);
      assert waitForEviction(cache, 5, TimeUnit.SECONDS, fqn);

      assert cache.get(fqn, "k") == null : "Node should have been evicted";
   }

}
