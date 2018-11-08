package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test(groups = {"functional"}, sequential = true, testName = "loader.LocalDelegatingCacheLoaderTest")
public class LocalDelegatingCacheLoaderTest extends CacheLoaderTestsBase
{
   CacheSPI delegatingCache;

   protected void configureCache(CacheSPI cache) throws Exception
   {
      if (delegatingCache == null)
      {
         delegatingCache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
         delegatingCache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
         delegatingCache.create();
         delegatingCache.start();
      }

      LocalDelegatingCacheLoaderConfig cfg = new LocalDelegatingCacheLoaderConfig();
      cfg.setDelegate(delegatingCache);
      cfg.setAsync(false);
      cfg.setFetchPersistentState(false);
      CacheLoaderConfig cacheLoaderConfig = new CacheLoaderConfig();
      cacheLoaderConfig.addIndividualCacheLoaderConfig(cfg);
      cache.getConfiguration().setCacheLoaderConfig(cacheLoaderConfig);
   }

   @Test(groups = {"functional"}, enabled = false)
   public void testLoadAndStore() throws Exception
   {
      //TODO intentional overload since this test does not pass
      //http://jira.jboss.com/jira/browse/JBCACHE-851
   }

   public void testPartialLoadAndStore()
   {
      // do nothing
   }

   public void testBuddyBackupStore()
   {
      // do nothing
   }

   public void testCacheLoaderThreadSafety()
   {
      // do nothing
   }

   public void testCacheLoaderThreadSafetyMultipleFqns()
   {
      // do nothing
   }
}
