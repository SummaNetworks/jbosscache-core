package org.jboss.cache.loader;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "loader.RootChildrenLoadedTest")
public class RootChildrenLoadedTest
{
   Cache<String, String> cache;
   Fqn fqn = Fqn.fromElements("a", "a");
   String key = "key";

   @BeforeTest
   public void setUp() throws Exception
   {
      CacheLoaderConfig cacheLoaderConfig = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyInMemoryCacheLoader.class.getName(), "", false, true, false, false, false);
      // assign the cache loader explicitly so it will stick between restarts
      cacheLoaderConfig.getFirstCacheLoaderConfig().setCacheLoader(new DummyInMemoryCacheLoader());
      Configuration cfg = new Configuration();
      cfg.setCacheLoaderConfig(cacheLoaderConfig);
      cache = new UnitTestCacheFactory().createCache(cfg, getClass());
      cache.put(fqn, key, "value");

      // flush the cache and start with totally clean state
      cache.stop();
      cache.start();
   }

   @AfterTest
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
   }

   public void doTest() throws Exception
   {
      // the workaround:
      // NodeInvocationDelegate<String, String> root = (NodeInvocationDelegate<String, String>) cache.getRoot();
      // root.setChildrenLoaded(false);

      assert cache.getNode(Fqn.ROOT).getChildrenNames().size() == 1;
   }
}
