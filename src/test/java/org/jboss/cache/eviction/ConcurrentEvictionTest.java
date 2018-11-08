/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.eviction;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

import org.jboss.cache.UnitTestCacheFactory;

/**
 * Tests cache behavior in the presence of concurrent passivation.
 *
 * @author Brian Stansberry
 * @version $Revision: 7422 $
 */
@Test(groups = {"functional"}, testName = "eviction.ConcurrentEvictionTest")
public class ConcurrentEvictionTest
{
   private Cache<Integer, String> cache;
   private long wakeupIntervalMillis = 0;
   private String tmpDir = TestingUtil.TEST_FILES;
   private String cacheLoaderDir = "JBossCacheFileCacheLoader";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      initCaches();
      wakeupIntervalMillis = cache.getConfiguration().getEvictionConfig().getWakeupInterval();
      if (wakeupIntervalMillis < 0)
      {
         fail("testEviction(): eviction thread wake up interval is illegal " + wakeupIntervalMillis);
      }
   }

   void initCaches() throws Exception
   {
      UnitTestCacheFactory<Integer, String> factory = new UnitTestCacheFactory<Integer, String>();
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      conf.setEvictionConfig(buildEvictionConfig());
      conf.setCacheLoaderConfig(buildCacheLoaderConfig());
      conf.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache = factory.createCache(conf, true, getClass());// read in generic local xml
   }

   private CacheLoaderConfig buildCacheLoaderConfig()
         throws IOException
   {
      CacheLoaderConfig cacheLoaderConfig = new CacheLoaderConfig();
      cacheLoaderConfig.setPassivation(false);
      cacheLoaderConfig.setPreload("/");
      cacheLoaderConfig.setShared(false);
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      iclc.setClassName("org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader");
      iclc.setAsync(false);
      iclc.setFetchPersistentState(true);
      iclc.setIgnoreModifications(false);
      cacheLoaderConfig.addIndividualCacheLoaderConfig(iclc);
      return cacheLoaderConfig;
   }

   private EvictionConfig buildEvictionConfig()
   {
      return new EvictionConfig(
            new EvictionRegionConfig(
                  Fqn.ROOT,
                  new LRUAlgorithmConfig(1000000, 5000)
            ),
            200);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
   }

   public void testConcurrentEviction() throws Exception
   {
      Fqn base = Fqn.fromString("/org/jboss/test/data/concurrent/eviction");

      // Create a bunch of nodes; more than the /org/jboss/test/data
      // region's maxNodes so we know eviction will kick in
      for (int i = 0; i < 1000; i++)
      {
         cache.put(Fqn.fromRelativeElements(base, i / 100), i, "value");
      }

      // Loop for long enough to have 5 runs of the eviction thread
      long loopDone = System.currentTimeMillis() + (5 * wakeupIntervalMillis);
      while (System.currentTimeMillis() < loopDone)
      {
         // If any get returns null, that's a failure
         for (int i = 0; i < 1000; i++)
         {
            Fqn fqn = Fqn.fromRelativeElements(base, i / 100);
            assertNotNull("found value under Fqn " + fqn + " and key " + i,
                  cache.get(fqn, i));
         }
      }
   }
}
