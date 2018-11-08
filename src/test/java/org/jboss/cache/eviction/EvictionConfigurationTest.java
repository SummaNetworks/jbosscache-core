/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.jboss.cache.*;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

/**
 * Unit test to test Eviction configuration types.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7314 $
 */
@Test(groups = "unit", sequential = false, testName = "eviction.EvictionConfigurationTest")
public class EvictionConfigurationTest
{
   public void testPolicyPerRegion() throws Exception
   {
      CacheSPI<Object, Object> cache = null;
      RegionManager regionManager = null;
      try
      {
         cache = setupCache("configs/policyPerRegion-eviction.xml");
         regionManager = cache.getRegionManager();
         assertEquals(5000, cache.getConfiguration().getEvictionConfig().getWakeupInterval());

         Region region = regionManager.getRegion("/org/jboss/data", true);
         EvictionRegionConfig evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/org/jboss/data"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LFUAlgorithmConfig);
         assertEquals(5000, ((LFUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(1000, ((LFUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMinNodes());

         region = regionManager.getRegion("/org/jboss/test/data", true);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/org/jboss/test/data"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof FIFOAlgorithmConfig);
         assertEquals(5, ((FIFOAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());

         region = regionManager.getRegion("/test", true);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/test"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof MRUAlgorithmConfig);
         assertEquals(10000, ((MRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());

         region = regionManager.getRegion("/maxAgeTest", true);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/maxAgeTest"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig);
         assertEquals(10000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(8000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive());
         assertEquals(10000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxAge());

         // test the default region. use a region name that isn't defined explicitly in conf file.
         region = regionManager.getRegion("/a/b/c", false);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.ROOT, region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig);
         assertEquals(5000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(1000000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive());
         assertEquals(-1, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxAge());

      }
      finally
      {
         TestingUtil.killCaches(cache);
      }
   }

   public void testMixedPolicies() throws Exception
   {
      CacheSPI<Object, Object> cache = null;
      RegionManager regionManager = null;

      try
      {
         cache = setupCache("configs/mixedPolicy-eviction.xml");
         regionManager = cache.getRegionManager();
         assertEquals(5000, cache.getConfiguration().getEvictionConfig().getWakeupInterval());

         Region region = regionManager.getRegion("/org/jboss/data", true);
         EvictionRegionConfig evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/org/jboss/data/"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof FIFOAlgorithmConfig);
         assertEquals(5000, ((FIFOAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());

         region = regionManager.getRegion("/test", true);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/test/"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof MRUAlgorithmConfig);
         assertEquals(10000, ((MRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());

         // test the default region. use a region name that isn't defined explicitly in conf file.
         region = regionManager.getRegion("/a/b/c", false);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.ROOT, region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig);
         assertEquals(5000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(1000000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive());
         assertEquals(-1, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxAge());

         region = regionManager.getRegion("/maxAgeTest", false);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/maxAgeTest/"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig);
         assertEquals(10000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(8000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive());
         assertEquals(10000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxAge());

      }
      finally
      {
         TestingUtil.killCaches(cache);
      }
   }

   public void testLegacyPolicyConfiguration() throws Exception
   {
      CacheSPI<Object, Object> cache = null;
      RegionManager regionManager = null;

      try
      {
         cache = setupCache("configs/local-lru-eviction.xml");
         regionManager = cache.getRegionManager();
         assertEquals(5000, cache.getConfiguration().getEvictionConfig().getWakeupInterval());

         Region region = regionManager.getRegion("/org/jboss/data", false);
         EvictionRegionConfig evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/org/jboss/data/"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig);
         assertEquals(5000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(1000000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive());

         region = regionManager.getRegion("/org/jboss/test/data", false);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/org/jboss/test/data/"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig);
         assertEquals(5, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(4000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive());

         region = regionManager.getRegion("/test", true);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/test/"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig);
         assertEquals(10000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(4000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive());

         region = regionManager.getRegion("/maxAgeTest", true);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.fromString("/maxAgeTest/"), region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig);
         assertEquals(10000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(8000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive());
         assertEquals(10000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxAge());

         // test the default region. use a region name that isn't defined explicitly in conf file.
         region = regionManager.getRegion("/a/b/c", false);
         evictionRegionConfig = region.getEvictionRegionConfig();
         assertEquals(Fqn.ROOT, region.getFqn());
         assertTrue(evictionRegionConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig);
         assertEquals(5000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxNodes());
         assertEquals(1000000, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive());
         assertEquals(-1, ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getMaxAge());

      }
      finally
      {
         TestingUtil.killCaches(cache);
      }
   }

   public void testTwoCacheInstanceConfiguration() throws Exception
   {
      this.setupCache("configs/local-lru-eviction.xml");
      this.setupCache("configs/local-lru-eviction.xml");
   }

   public void testNoEviction() throws Exception
   {
      CacheSPI<Object, Object> cache = null;
      RegionManager regionManager = null;

      try
      {
         cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(getClass());
         regionManager = cache.getRegionManager();
         assertEquals(0, regionManager.getAllRegions(Region.Type.ANY).size());
      }
      finally
      {
         TestingUtil.killCaches(cache);
      }
   }

   private CacheSPI<Object, Object> setupCache(String configurationName)
   {
      UnitTestCacheFactory<Object, Object> testCacheFactory = new UnitTestCacheFactory<Object, Object>();
      Configuration config = testCacheFactory.getConfigurationFromFile(configurationName);
      config.setCacheMode(Configuration.CacheMode.LOCAL);
      config.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      config.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      return (CacheSPI<Object, Object>) testCacheFactory.createCache(config, getClass());
   }
}
