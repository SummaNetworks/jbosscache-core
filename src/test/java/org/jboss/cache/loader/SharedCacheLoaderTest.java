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
import org.jboss.cache.config.Configuration;
import org.jboss.cache.interceptors.CacheStoreInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Iterator;

/**
 * See http://www.jboss.com/index.html?module=bb&op=viewtopic&p=3919374#3919374
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = "functional", sequential = true, testName = "loader.SharedCacheLoaderTest")
public class SharedCacheLoaderTest
{
   private CacheSPI<Object, Object> cache1, cache2;
   private DummyCountingCacheLoader dummyCacheLoader;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      if (cache1 != null || cache2 != null) tearDown();

      // set up 2 instances of CacheImpl with shared CacheLoaders.

      Configuration c1 = new Configuration();
      Configuration c2 = new Configuration();

      c1.setCacheMode("REPL_SYNC");
      c2.setCacheMode("REPL_SYNC");

      c1.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyCountingCacheLoader.class.getName(),
            "", false, false, true, false, false));
      c2.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyCountingCacheLoader.class.getName(),
            "", false, false, true, false, false));

      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c1, false, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c2, false, getClass());
      
      cache1.start();
      cache2.start();

      dummyCacheLoader = new DummyCountingCacheLoader(); // statistics are stored statically so this is safe.
      dummyCacheLoader.scrubStats();
   }

   protected CacheStoreInterceptor findCacheStoreInterceptor(CacheSPI cache)
   {
      Iterator ints = cache.getInterceptorChain().iterator();
      CacheStoreInterceptor csi = null;
      while (ints.hasNext())
      {
         CommandInterceptor i = (CommandInterceptor) ints.next();
         if (i instanceof CacheStoreInterceptor)
         {
            csi = (CacheStoreInterceptor) i;
            break;
         }
      }
      return csi;
   }

   @AfterMethod(alwaysRun = true)
   protected void tearDown()
   {
      if (cache1 != null) TestingUtil.killCaches(cache1);
      if (cache2 != null) TestingUtil.killCaches(cache2);
      cache1 = null;
      cache2 = null;
   }

   public void testReplicationWithSharedCL()
   {
      cache1.put("/test", "one", "two");

      // should have replicated
      assertEquals("two", cache1.get("/test", "one"));
      assertEquals("two", cache2.get("/test", "one"));

      // only a single put() should have happened on the cache loader though.
      assertEquals(1, dummyCacheLoader.getPutCount());
   }
}
