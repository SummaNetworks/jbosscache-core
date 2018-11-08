package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

/**
 * Odd that we need a test for a test class, but if we intend to use the {@link org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader} as a cache
 * loader stub then we need to make sure it behaves as a valid cache loader.
 */
@Test(groups = {"functional"}, testName = "loader.DummyInMemoryCacheLoaderPessimisticTest")
public class DummyInMemoryCacheLoaderPessimisticTest extends CacheLoaderTestsBase
{
   protected void configureCache(CacheSPI cache) throws Exception
   {
      // use the shared variation of the DIMCL so that state is persisted in a static variable in memory rather than an
      // instance one.
      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummySharedInMemoryCacheLoader.class.getName(),
            "debug=true \n bin=" + getClass().getName(), false, true, false, false, false);
      cache.getConfiguration().setCacheLoaderConfig(clc);
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
   }
}
