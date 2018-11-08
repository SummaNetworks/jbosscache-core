/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.eviction;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

@Test(groups = { "functional" }, testName = "eviction.RecursiveRootEvictionTest")
public class RecursiveRootEvictionTest
{
   private CacheSPI<String, String> cache;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      UnitTestCacheFactory<String, String> factory = new UnitTestCacheFactory<String, String>();
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      conf.setEvictionConfig(new EvictionConfig(new EvictionRegionConfig(Fqn.ROOT, new LRUAlgorithmConfig(1000000, 5000)), 200));
      conf.setCacheLoaderConfig(buildCacheLoaderConfig());
      conf.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache = (CacheSPI<String, String>) factory.createCache(conf, true, getClass());
   }

   private CacheLoaderConfig buildCacheLoaderConfig() throws IOException
   {
      CacheLoaderConfig cacheLoaderConfig = new CacheLoaderConfig();
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      iclc.setClassName(DummyInMemoryCacheLoader.class.getName());
      cacheLoaderConfig.addIndividualCacheLoaderConfig(iclc);
      return cacheLoaderConfig;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
   }

   public void testNonrecursiveRootEviction()
   {
      cache.put(Fqn.fromElements("a", "a"), "x", "x");
      cache.put(Fqn.fromElements("a", "b"), "x", "x");
      cache.put(Fqn.fromElements("a", "c"), "x", "x");

      assert cache.getNumberOfNodes() == 4;

      cache.evict(Fqn.fromElements("a", "a"), false);
      assert cache.getNumberOfNodes() == 3;
      cache.evict(Fqn.fromElements("a", "b"), false);
      assert cache.getNumberOfNodes() == 2;
      cache.evict(Fqn.fromElements("a", "c"), false);
      assert cache.getNumberOfNodes() == 1;
      cache.evict(Fqn.fromElements("a"), false);

      assert cache.getNumberOfNodes() == 0;
   }

   public void testRecursiveNonRootEviction()
   {
      cache.put(Fqn.fromElements("a", "a"), "x", "x");
      cache.put(Fqn.fromElements("a", "b"), "x", "x");
      cache.put(Fqn.fromElements("a", "c"), "x", "x");

      cache.evict(Fqn.fromElements("a"), true);

      assert cache.getNumberOfNodes() == 0;
   }

   public void testRecursiveRootEviction()
   {
      cache.put(Fqn.fromElements("a", "a"), "x", "x");
      cache.put(Fqn.fromElements("a", "b"), "x", "x");
      cache.put(Fqn.fromElements("a", "c"), "x", "x");

      cache.evict(Fqn.ROOT, true);

      assert cache.getNumberOfNodes() == 0;
   }
}
