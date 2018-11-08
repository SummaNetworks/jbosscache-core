/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.element.LoadersElementParser;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, sequential = true, testName = "loader.CacheLoaderPurgingTest")
public class CacheLoaderPurgingTest
{
   private CacheSPI<Object, Object> cache;
   private String key = "key", value = "value";
   private Fqn fqn = Fqn.fromString("/a/b/c");

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws CacheException
   {
      if (cache != null)
      {
         cache.removeNode(Fqn.ROOT);
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   public void testSingleLoaderNoPurge() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      Configuration c = cache.getConfiguration();

      String s = "bin=" + getClass().getName();
      c.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummySharedInMemoryCacheLoader.class.getName(),
            s, false, false, false, false, false));
      cache.start();

      cache.put(fqn, key, value);

      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));

      cache.evict(fqn);
      cache.stop();
      assertEquals(value, loader.get(fqn).get(key));

      cache.start();
      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));
   }

   public void testSingleLoaderPurge() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      Configuration c = cache.getConfiguration();
      String s = "bin=" + getClass().getName();
      c.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummySharedInMemoryCacheLoader.class.getName(),
            s, false, false, false, true, false));
      cache.start();

      cache.put(fqn, key, value);

      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));

      cache.evict(fqn);
      cache.stop();
      assertEquals(value, loader.get(fqn).get(key));

      cache.start();

      assertTrue(cache.getCacheLoaderManager().getCacheLoaderConfig().getFirstCacheLoaderConfig().isPurgeOnStartup());

      assertNull(cache.getNode(fqn));
      assertNull(loader.get(fqn));
   }

   public void testTwoLoadersPurge() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      String xml =
            "      <loaders passivation=\"false\">\n" +
                  "         <loader class=\"org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader\" fetchPersistentState=\"true\"\n" +
                  "                      purgeOnStartup=\"true\">\n" +
                  "                <properties>bin=" + getClass() + "bin1</properties>\n" +
                  "         </loader>" +
                  "         <loader class=\"org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader\" fetchPersistentState=\"false\"\n" +
                  "                      purgeOnStartup=\"false\">\n" +
                  "                <properties>bin=" + getClass()+ "bin2</properties>\n" +
                  "         </loader>" +
                  "      </loaders>";
      LoadersElementParser parser = new LoadersElementParser();
      CacheLoaderConfig cacheLoaderConfig = parser.parseLoadersElement(XmlConfigHelper.stringToElementInCoreNS(xml));
      Configuration c = cache.getConfiguration();
      c.setCacheLoaderConfig(cacheLoaderConfig);
      cache.start();

      cache.put(fqn, key, value);

      CacheLoader loader[] = ((ChainingCacheLoader) cache.getCacheLoaderManager().getCacheLoader()).getCacheLoaders().toArray(new CacheLoader[]{});

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader[0].get(fqn).get(key));
      assertEquals(value, loader[1].get(fqn).get(key));

      cache.evict(fqn);
      cache.stop();
      assertEquals(value, loader[0].get(fqn).get(key));
      assertEquals(value, loader[1].get(fqn).get(key));

      cache.start();
      assertTrue(!cache.exists(fqn));
      assertNull(loader[0].get(fqn));
      assertNotNull(loader[1].get(fqn));
      assertEquals(value, cache.get(fqn, key));
   }

}
