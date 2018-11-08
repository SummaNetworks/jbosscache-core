package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.testng.annotations.Test;

/**
 * Odd that we need a test for a test class, but if we intend to use the {@link org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader} as a cache
 * loader stub then we need to make sure it behaves as a valid cache loader.
 */
@Test(groups = {"functional"}, testName = "loader.DummyInMemoryCacheLoaderTest")
public class DummyInMemoryCacheLoaderTest extends CacheLoaderTestsBase
{

   DummySharedInMemoryCacheLoader dimCl;

   protected void configureCache(CacheSPI cache) throws Exception
   {
      // use the shared variation of the DIMCL so that state is persisted in a static variable in memory rather than an
      // instance one.            
      String bin = "DummyInMemoryCacheLoader-" + Thread.currentThread().getName();
      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummySharedInMemoryCacheLoader.class.getName(),
            "bin=" + bin, false, true, false, false, false);
      cache.getConfiguration().setCacheLoaderConfig(clc);
   }

   protected void postConfigure()
   {
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      if (loader instanceof AbstractDelegatingCacheLoader)
      {
         dimCl = (DummySharedInMemoryCacheLoader) ((ReadOnlyDelegatingCacheLoader)loader).getCacheLoader();
      }
      else
      {
         dimCl = (DummySharedInMemoryCacheLoader) loader;
      }
   }

   @Override
   protected void cleanup() throws Exception 
   {
      if (dimCl != null) {
         dimCl.remove(Fqn.ROOT);
      }
   }

}
