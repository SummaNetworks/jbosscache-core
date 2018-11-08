/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.loader.testloaders.DummyCountingCacheLoader;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * A simple non-failing unit test to measure how many times each method on a cache loader is called.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = "functional", sequential = true, testName = "loader.CacheLoaderMethodCallCounterTest")
public class CacheLoaderMethodCallCounterTest
{
   private CacheSPI cache;
   private DummyCountingCacheLoader dummyLoader;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      if (cache != null) tearDown();
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyCountingCacheLoader.class.getName(),
            "", false, false, false, false, false));
      cache.start();
      dummyLoader = (DummyCountingCacheLoader) cache.getCacheLoaderManager().getCacheLoader();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }


   public void testPut()
   {
      cache.put("/node", "key", "value");
   }

   public void testGet()
   {
      cache.get("/node", "key");
   }

   public void testRemove()
   {
      cache.remove("/node", "key");
   }

   public void testLoopedGets()
   {
      // put an object in cache
      cache.put("/test", "key", "value");

      // we should see this put in the cl
      assertEquals(1, dummyLoader.getPutCount());
      // the cloader interceptor does a get as well when doing the put ... ?
      assertEquals(1, dummyLoader.getGetCount());

      for (int i = 0; i < 2000; i++)
      {
         cache.getNode("/test");
      }

      assertEquals(1, dummyLoader.getPutCount());
      assertEquals(1, dummyLoader.getGetCount());
      assertEquals(0, dummyLoader.getRemoveCount());
      assertEquals(0, dummyLoader.getExistsCount());
   }
}
