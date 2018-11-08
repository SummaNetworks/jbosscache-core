package org.jboss.cache.api.nodevalidity;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;

import java.util.Properties;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "pessimistic"}, testName = "api.nodevalidity.InvalidatedPessNodeValidityTest")
public class InvalidatedPessNodeValidityTest extends NodeValidityTestBase
{
   protected DummySharedInMemoryCacheLoader loader;

   public InvalidatedPessNodeValidityTest()
   {
      invalidation = true;
      nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;
   }

   protected Cache<String, String> createObserver()
   {
      return newCache();
   }

   protected Cache<String, String> createModifier()
   {
      return newCache();
   }

   @AfterClass
   public void emptyCacheLoader()
   {
      loader.wipeBin();
   }

   protected Cache<String, String> newCache()
   {
      UnitTestCacheFactory<String, String> f = new UnitTestCacheFactory<String, String>();
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.INVALIDATION_SYNC);
      Cache<String, String> cache = f.createCache(c, false, getClass());
      nodeLockingSchemeSpecificSetup(cache.getConfiguration());

      // need a cache loader as a shared data source between the 2 instances
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      iclc.setClassName(DummySharedInMemoryCacheLoader.class.getName());
      Properties props = new Properties();
      props.setProperty("bin", "bin-" + Thread.currentThread().getName());
      iclc.setProperties(props);
      CacheLoaderConfig clc = new CacheLoaderConfig();      
      clc.addIndividualCacheLoaderConfig(iclc);      
      cache.getConfiguration().setCacheLoaderConfig(clc);
      // disable state transfer!!
      cache.getConfiguration().setFetchInMemoryState(false);

      cache.start();

      CacheSPI spi = (CacheSPI) cache;

      loader = (DummySharedInMemoryCacheLoader) spi.getCacheLoaderManager().getCacheLoader();

      return cache;
   }
}
