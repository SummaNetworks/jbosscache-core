package org.jboss.cache.loader;

import org.jboss.cache.AbstractMultipleCachesTest;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoaderConfig;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

@Test(groups = {"functional"}, sequential = true, testName = "loader.ChainedClusteredCacheLoaderTest")
public class ChainedClusteredCacheLoaderTest extends AbstractMultipleCachesTest
{
   private CacheSPI<Object, Object> cache1, cache2;
   private CacheLoader loader1, loader2;
   private Fqn fqn = Fqn.fromString("/a");
   private Fqn fqn2 = Fqn.fromString("/a/b");
   private String key = "key";   

   protected void createCaches() throws Throwable {
      Configuration c1 = new Configuration();
      Configuration c2 = new Configuration();
      c1.setStateRetrievalTimeout(2000);
      c2.setStateRetrievalTimeout(2000);
      c1.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c2.setCacheMode(Configuration.CacheMode.REPL_SYNC);

      c1.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.ClusteredCacheLoader",
            "timeout=5000", false, false, false, false, false));
      c2.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.ClusteredCacheLoader",
            "timeout=5000", false, false, false, false, false));
      DummySharedInMemoryCacheLoaderConfig cfg = new DummySharedInMemoryCacheLoaderConfig("cache-2");
      c2.getCacheLoaderConfig().addIndividualCacheLoaderConfig(cfg);

      c1.setUseRegionBasedMarshalling(false);
      c2.setUseRegionBasedMarshalling(false);


      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c1, false, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c2, false, getClass());
      cache1.getConfiguration().setSerializationExecutorPoolSize(0);
      cache2.getConfiguration().setSerializationExecutorPoolSize(0);


      cache1.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache2.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);

      cache1.start();
      cache2.start();

      loader1 = cache1.getCacheLoaderManager().getCacheLoader();
      loader2 = cache2.getCacheLoaderManager().getCacheLoader();
      registerCaches(cache1, cache2);
   }

   public void testClusteredGetFromSecondLoader() throws Exception {
      cache1.put(fqn, key, "value");

      assert loader1 instanceof ClusteredCacheLoader;
      assert loader2 instanceof ChainingCacheLoader;
      DummySharedInMemoryCacheLoader dummyLoader2 = (DummySharedInMemoryCacheLoader) ((ChainingCacheLoader) loader2).getCacheLoaders().get(1);

      assert loader1.get(fqn).containsKey(key);
      assert loader2.get(fqn).containsKey(key);
      assert dummyLoader2.get(fqn).containsKey(key);

      // evict from memory on all caches
      cache1.evict(fqn);
      cache2.evict(fqn);

      assert dummyLoader2.get(fqn).containsKey(key);

      assert "value".equals(cache1.get(fqn, key));
   }

   public void testClusteredGetChildrenNamesFromSecondLoader() throws Exception {
      cache1.put(fqn, key, "value");
      cache1.put(fqn2, key, "value");

      assert loader1 instanceof ClusteredCacheLoader;
      assert loader2 instanceof ChainingCacheLoader;
      DummySharedInMemoryCacheLoader dummyLoader2 = (DummySharedInMemoryCacheLoader) ((ChainingCacheLoader) loader2).getCacheLoaders().get(1);

      assert loader1.get(fqn).containsKey(key);
      assert loader2.get(fqn).containsKey(key);
      assert dummyLoader2.get(fqn).containsKey(key);

      // evict from memory on all caches
      cache1.evict(fqn, true);
      cache2.evict(fqn, true);

      assert dummyLoader2.get(fqn).containsKey(key);

      Set s = new HashSet();
      s.add("b");
      assert s.equals(loader1.getChildrenNames(fqn));
   }
}
