package org.jboss.cache.marshall;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.Region;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.loader.FileCacheLoaderConfig;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collections;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Tests marshalling/unmarshalling during cache loader operations involving types
 * not visible to the cache's default classloader.
 *
 * @author <a href="mailto:brian.stansberry@jboss.org">Brian Stansberry</a>
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 * @since 2.1.0
 */
@Test(groups = "functional", testName = "marshall.CacheLoaderMarshallingTest")
public class CacheLoaderMarshallingTest extends RegionBasedMarshallingTestBase
{
   private static final String tmpDir = TestingUtil.TEST_FILES + File.separatorChar + "CacheLoaderMarshallingTest";

   private Cache<Object, Object> cache;
   private Fqn fqn = Fqn.fromString("/a");


   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception           
   {
      TestingUtil.killCaches(cache);
      cache = null;
      File f = new File(tmpDir);
      if (f.exists())
         if (!f.delete())
            f.deleteOnExit();
      super.tearDown();
   }

   public void testCacheLoaderMarshalling() throws Exception
   {
      cacheLoaderMarshallingTest(false);
   }

   public void testCacheLoaderRegionBasedMarshalling() throws Exception
   {
      cacheLoaderMarshallingTest(true);
   }

   public void testLoadNodesAtRootOfRegion() throws Exception
   {
      String rootRegionName = "/myregion";
      String hereFqn = rootRegionName + "/here";

      cache = createCache(true);
      cache.start();

      Region r = cache.getRegion(Fqn.fromString(rootRegionName), true);
      r.registerContextClassLoader(Thread.currentThread().getContextClassLoader());
      r.activate();

      cache.put(rootRegionName, "a key", "a value");
      cache.put(hereFqn, "another key", "another value");

      r.deactivate();
      r.unregisterContextClassLoader();

      cache.stop();

      cache.start();

      r = cache.getRegion(Fqn.fromString(rootRegionName), true);
      r.registerContextClassLoader(Thread.currentThread().getContextClassLoader());
      r.activate();

      Node<Object, Object> rootRegionNode = cache.getNode(rootRegionName);
      Node<Object, Object> hereNode = cache.getNode(hereFqn);
      assertNotNull(rootRegionNode);
      assertNotNull(hereNode);

      assertEquals(hereNode.get("another key"), "another value");
      assertEquals(rootRegionNode.get("a key"), "a value");
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

   private Cache createCache(boolean useRegionBased)
   {
      Configuration config = new Configuration();
      config.setUseRegionBasedMarshalling(useRegionBased);
      config.setInactiveOnStartup(useRegionBased);
      Cache cache = new UnitTestCacheFactory<Object, Object>().createCache(config, false, getClass());

      EvictionConfig ec = new EvictionConfig();
      ec.setWakeupInterval(1000000);  // a long time; really disabled
      EvictionRegionConfig erc = new EvictionRegionConfig();
      erc.setRegionFqn(Fqn.ROOT);
      LRUAlgorithmConfig lruAlgorithmConfig = new LRUAlgorithmConfig();
      lruAlgorithmConfig.setMaxNodes(1000);
      lruAlgorithmConfig.setTimeToLive(1000000);
      erc.setEvictionAlgorithmConfig(lruAlgorithmConfig);
      ec.addEvictionRegionConfig(erc);
      config.setEvictionConfig(ec);

      CacheLoaderConfig clc = new CacheLoaderConfig();
      clc.setPassivation(true);
      clc.setShared(false);
      FileCacheLoaderConfig fclc = new FileCacheLoaderConfig();
      fclc.setLocation(tmpDir);

      clc.setIndividualCacheLoaderConfigs(Collections.<IndividualCacheLoaderConfig>singletonList(fclc));
      config.setCacheLoaderConfig(clc);

      return cache;
   }

}
