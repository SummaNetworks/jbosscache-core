/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * Tests ignoreModifications and tests contents of individual loaders
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, sequential = true, testName = "loader.ChainingCacheLoaderFullTest")
public class ChainingCacheLoaderFullTest
{
   private CacheSPI<Object, Object> cache;
   private ChainingCacheLoader chainingCacheLoader;
   private CacheLoader loader1, loader2;
   private Fqn fqn = Fqn.fromString("/a/b");
   private String key = "key";
   private String value = "value";


   protected void startCache(boolean ignoreMods1, boolean ignoreMods2) throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(ignoreMods1, ignoreMods2));

      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.start();

      chainingCacheLoader = (ChainingCacheLoader) cache.getCacheLoaderManager().getCacheLoader();
      loader1 = chainingCacheLoader.getCacheLoaders().get(0);
      loader2 = chainingCacheLoader.getCacheLoaders().get(1);

      // we need to make sure we have the raw loaders - not the "ReadOnly.." wrapped versions.
      while (loader1 instanceof AbstractDelegatingCacheLoader)
         loader1 = ((AbstractDelegatingCacheLoader) loader1).getCacheLoader();
      while (loader2 instanceof AbstractDelegatingCacheLoader)
         loader2 = ((AbstractDelegatingCacheLoader) loader2).getCacheLoader();
   }

   protected void cleanup() throws Exception
   {
      cache.removeNode(Fqn.ROOT);
      TestingUtil.killCaches(cache);
      cache = null;
   }

   protected CacheLoaderConfig getCacheLoaderConfig(boolean ignoreMods1, boolean ignoreMods2) throws Exception
   {
      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, null, DummyInMemoryCacheLoader.class.getName(), "", false, true, false, false, ignoreMods1);
      CacheLoaderConfig.IndividualCacheLoaderConfig ic = UnitTestConfigurationFactory.buildIndividualCacheLoaderConfig(null, DummyInMemoryCacheLoader.class.getName(), "", false, false, false, ignoreMods2);
      clc.addIndividualCacheLoaderConfig(ic);
      return clc;
   }

   public void testCruds() throws Exception
   {
      startCache(false, false);

      // put something in the cache.
      cache.put(fqn, key, value);

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));

      // evict
      cache.evict(fqn);
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));
      assertEquals(value, cache.get(fqn, key));

      // remove
      cache.removeNode(fqn);
      assertNull(value, cache.get(fqn, key));
      assertNull(value, chainingCacheLoader.get(fqn));
      assertNull(value, loader1.get(fqn));
      assertNull(value, loader2.get(fqn));

      cleanup();
   }

   public void testGets() throws Exception
   {
      startCache(false, false);

      cache.put(fqn, key, value);

      // test that loader1 is always looked up first.
      cache.evict(fqn);
      loader1.put(fqn, key, value + 2);
      assertEquals(value, loader2.get(fqn).get(key));
      assertEquals(value + 2, loader1.get(fqn).get(key));
      assertEquals(value + 2, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value + 2, cache.get(fqn, key));

      cache.removeNode(Fqn.ROOT);
      cache.put(fqn, key, value);

      // test that loader2 is NOT checked if loader1 has the value
      cache.evict(fqn);
      loader2.put(fqn, key, value + 2);
      assertEquals(value + 2, loader2.get(fqn).get(key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value, cache.get(fqn, key));

      cache.removeNode(Fqn.ROOT);
      cache.put(fqn, key, value);

      // test that loader2 is checked if loader1 returns a null
      cache.evict(fqn);
      loader1.remove(fqn);
      assertNull(loader1.get(fqn));
      assertEquals(value, loader2.get(fqn).get(key));
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value, cache.get(fqn, key));

      cleanup();
   }

   public void testIgnoreMods() throws Exception
   {
      startCache(false, true);

      // initialise the loaders
      loader1.put(fqn, key, value);
      loader2.put(fqn, key, value);

      // check contents
      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));

      // do a put
      cache.put(fqn, key, value + 2);
      assertEquals(value + 2, cache.get(fqn, key));
      assertEquals(value + 2, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value + 2, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));

      // remove
      cache.removeNode(fqn);
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertNull(loader1.get(fqn));
      assertEquals(value, loader2.get(fqn).get(key));
      assertEquals(value, cache.get(fqn, key));

      cleanup();
   }

   public void testIgnoreModsTransactional() throws Exception
   {
      startCache(false, true);
      TransactionManager mgr = cache.getTransactionManager();

      // initialise the loaders
      loader1.put(fqn, key, value);
      loader2.put(fqn, key, value);

      // check contents
      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));

      // do a put
      mgr.begin();
      cache.put(fqn, key, value + 2);
      assertEquals(value + 2, cache.get(fqn, key));
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));
      mgr.commit();
      assertEquals(value + 2, cache.get(fqn, key));
      assertEquals(value + 2, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value + 2, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));

      // remove - not in a tx, see http://jira.jboss.com/jira/browse/JBCACHE-352
      //        mgr.begin();
      cache.removeNode(fqn);

      //        assertNull(cache.get(fqn, key));
      //        assertEquals(value + 2, chainingCacheLoader.get(fqn).get(key));
      //        assertEquals(value + 2, loader1.get(fqn).get(key));
      //        assertEquals(value, loader2.get(fqn).get(key));
      //        mgr.commit();
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertNull(loader1.get(fqn));
      assertEquals(value, loader2.get(fqn).get(key));
      assertEquals(value, cache.get(fqn, key));

      cleanup();
   }

   public void testCrudsTransactional() throws Exception
   {
      startCache(false, false);
      TransactionManager mgr = cache.getTransactionManager();

      // assert that the loaders ae empty
      chainingCacheLoader.remove(cache.getRoot().getFqn());
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));
      assertNull(chainingCacheLoader.get(fqn));

      // put something in the cache.
      mgr.begin();
      cache.put(fqn, key, value);
      assertEquals(value, cache.get(fqn, key));
      assertNull(chainingCacheLoader.get(fqn));
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));
      mgr.commit();
      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));

      // evict
      cache.evict(fqn);
      assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));
      assertEquals(value, cache.get(fqn, key));

      // remove - not in a tx, see http://jira.jboss.com/jira/browse/JBCACHE-352
      //        mgr.begin();
      cache.removeNode(fqn);
      //        assertEquals(value, chainingCacheLoader.get(fqn).get(key));
      //        assertEquals(value, loader1.get(fqn).get(key));
      //        assertEquals(value, loader2.get(fqn).get(key));
      //        assertNull(value, cache.get(fqn, key));
      //        mgr.commit();
      assertNull(value, cache.get(fqn, key));
      assertNull(value, chainingCacheLoader.get(fqn));
      assertNull(value, loader1.get(fqn));
      assertNull(value, loader2.get(fqn));

      cleanup();
   }
}
