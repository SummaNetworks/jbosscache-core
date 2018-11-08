package org.jboss.cache.marshall;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.loader.JDBCCacheLoaderConfig;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.UnitTestDatabaseManager;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Properties;

/**
 * Tests marshalling/unmarshalling during cache loader operations involving types
 * not visible to the cache's default classloader.
 *
 * @author <a href="mailto:brian.stansberry@jboss.org">Brian Stansberry</a>
 * @since 2.1.0
 */
@Test(groups = "functional", testName = "marshall.CacheLoaderMarshallingJDBCTest")
public class CacheLoaderMarshallingJDBCTest extends RegionBasedMarshallingTestBase
{
   private static final String className = "org.jboss.cache.marshall.MyUUID";

   private Cache<Object, Object> cache;
   private Fqn fqn = Fqn.fromString("/a");


   @AfterMethod(alwaysRun = true)
   @Override
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      Properties props = cache.getConfiguration().getCacheLoaderConfig().getFirstCacheLoaderConfig().getProperties();
      UnitTestDatabaseManager.shutdownInMemoryDatabase(props);
      super.tearDown();
      cache = null;
   }

   @Override
   protected ClassLoader getClassLoader()
   {
      String[] includesClasses = {className};
      String[] excludesClasses = {};
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      return new SelectedClassnameClassLoader(includesClasses, excludesClasses, cl);
   }

   public void testCacheLoaderMarshalling() throws Exception
   {
      cacheLoaderMarshallingTest(false);
   }

   public void testCacheLoaderRegionBasedMarshalling() throws Exception
   {
      cacheLoaderMarshallingTest(true);
   }

   private void cacheLoaderMarshallingTest(boolean useRegionBased) throws Exception
   {
      cache = createCache(useRegionBased);
      cache.start();

      FooClassLoader loader = new FooClassLoader(originalClassLoaderTL.get());

      if (useRegionBased)
      {
         Region r = cache.getRegion(Fqn.ROOT, true);
         r.registerContextClassLoader(loader);
         r.activate();
      }

      Class clazz = loader.loadFoo();
      Object obj = clazz.newInstance();

      Thread.currentThread().setContextClassLoader(loader);
      cache.put(fqn, "key", obj);

      this.resetContextClassLoader();
      cache.evict(fqn);

      Thread.currentThread().setContextClassLoader(loader);
      assertEquals(obj, cache.get(fqn, "key"));
   }

   private Cache createCache(boolean useRegionBased) throws Exception
   {
      Properties prop = UnitTestDatabaseManager.getTestDbProperties();

      // ensure cleanup after each test
      prop.setProperty("cache.jdbc.table.drop", "true");

      Cache cache = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      Configuration config = cache.getConfiguration();
      config.setUseRegionBasedMarshalling(useRegionBased);
      config.setInactiveOnStartup(useRegionBased);

      int wakeupInterval = 1000000; // a long time; really disabled
      EvictionConfig ec = new EvictionConfig(
            new EvictionRegionConfig(
                  Fqn.ROOT,
                  new LRUAlgorithmConfig(1000000, 0, 1000)
            ),
            wakeupInterval
      );

      config.setEvictionConfig(ec);

      CacheLoaderConfig clc = new CacheLoaderConfig();
      clc.setPassivation(true);
      clc.setShared(false);
      JDBCCacheLoaderConfig jdbc_clc = new JDBCCacheLoaderConfig();
      jdbc_clc.setProperties(prop);

      clc.setIndividualCacheLoaderConfigs(Collections.<IndividualCacheLoaderConfig>singletonList(jdbc_clc));
      config.setCacheLoaderConfig(clc);

      return cache;
   }
}
