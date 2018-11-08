package org.jboss.cache.passivation;

import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.testng.annotations.Test;

@Test(groups = {"functional"}, testName = "passivation.PassivationToDummyInMemoryCacheLoaderTest")
public class PassivationToDummyInMemoryCacheLoaderTest extends PassivationTestsBase
{
   protected void configureCache() throws Exception
   {
      CacheLoaderConfig clc = new CacheLoaderConfig();
      cache.getConfiguration().setCacheLoaderConfig(clc);
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      clc.setPassivation(true);
      clc.addIndividualCacheLoaderConfig(iclc);
      iclc.setClassName(DummyInMemoryCacheLoader.class.getName());
      iclc.setProperties("debug=true");
   }
}
