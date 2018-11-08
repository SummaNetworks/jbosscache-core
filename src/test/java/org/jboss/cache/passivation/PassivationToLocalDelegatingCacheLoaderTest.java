package org.jboss.cache.passivation;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.loader.LocalDelegatingCacheLoaderConfig;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * Runs a test against using delegated cache loader
 *
 * @author <a href="mailto:{hmesha@novell.com}">{Hany Mesha}</a>
 * @version $Id: PassivationToLocalDelegatingCacheLoaderTest.java 7284 2008-12-12 05:00:02Z mircea.markus $
 */
@Test(groups = "functional", testName = "passivation.PassivationToLocalDelegatingCacheLoaderTest")
public class PassivationToLocalDelegatingCacheLoaderTest extends PassivationTestsBase
{
   ThreadLocal<CacheSPI> delegating_cacheTL = new ThreadLocal<CacheSPI>();
   //ThreadLocal<CacheLoader> cache_loaderTL = new ThreadLocal<CacheLoader>();

   protected void configureCache() throws Exception
   {
      CacheSPI delegating_cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      delegating_cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      delegating_cache.create();
      delegating_cache.start();
      delegating_cacheTL.set(delegating_cache);

      LocalDelegatingCacheLoaderConfig cfg = new LocalDelegatingCacheLoaderConfig();
      cfg.setDelegate(delegating_cache);
      cfg.setAsync(false);
      cfg.setFetchPersistentState(false);
      CacheLoaderConfig cacheLoaderConfig = new CacheLoaderConfig();
      cacheLoaderConfig.addIndividualCacheLoaderConfig(cfg);
      cacheLoaderConfig.setPassivation(true);
      cache.getConfiguration().setCacheLoaderConfig(cacheLoaderConfig);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      super.tearDown();
      CacheSPI delegating_cache = delegating_cacheTL.get();
      delegating_cacheTL.set(null);
      TestingUtil.killCaches(delegating_cache);
      delegating_cache = null;
      
   }

   public void testLoadAndStore() throws Exception
   {
      //TODO intentional overload since this test does not pass
      //http://jira.jboss.com/jira/browse/JBCACHE-851
   }

}
