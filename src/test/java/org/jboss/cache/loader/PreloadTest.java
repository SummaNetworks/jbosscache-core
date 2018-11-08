package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

@Test(groups = "functional", sequential = true, testName = "loader.PreloadTest")
public class PreloadTest
{
   CacheSPI<Object, Object> cache;
   Fqn fqn = Fqn.fromString("/a/b/c");
   Object key = "key", value = "value";

   @AfterMethod
   public void tearDown()
   {
      if (cache != null) TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testPreload() throws Exception
   {
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.LOCAL);
      String props = "bin=" + getClass().getName();
      c.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "/a", DummySharedInMemoryCacheLoader.class.getName(),
            props, false, false, false, false, false));
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), getClass());
      cache.put(fqn, key, value);
      assertExists();

      cache.destroy();

      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), getClass());
      assertExists();
   }

   public void testPreloadMultiRegions() throws Exception
   {
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.LOCAL);
      String props = "bin=" + getClass().getName();
      c.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "/a", DummySharedInMemoryCacheLoader.class.getName(),
            props, false, false, false, false, false));
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), getClass());
      cache.put(fqn, key, value);
      assertExists();

      cache.destroy();

      c.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "/c,/a,/b", DummySharedInMemoryCacheLoader.class.getName(),
            props, false, false, false, false, false));
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), getClass());
      assertExists();

      c.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "/c, /a, /b", DummySharedInMemoryCacheLoader.class.getName(),
            props, false, false, false, false, false));
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), getClass());
      assertExists();

      c.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "  /c,   /a,   /b", DummySharedInMemoryCacheLoader.class.getName(),
            props, false, false, false, false, false));
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), getClass());
      assertExists();
   }

   private void assertExists() throws Exception
   {
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      assert loader.get(fqn).get(key).equals(value);
      assert cache.peek(fqn, false).getDataDirect().get(key).equals(value);
   }
}
