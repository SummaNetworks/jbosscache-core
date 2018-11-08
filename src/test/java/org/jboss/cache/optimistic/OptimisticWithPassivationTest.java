/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.CacheLoaderConfig;
import static org.jboss.cache.factories.UnitTestConfigurationFactory.buildSingleCacheLoaderConfig;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.transaction.DummyTransactionManager;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.Test;

/**
 * Tests optimistic locking with pasivation
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.OptimisticWithPassivationTest")
public class OptimisticWithPassivationTest extends AbstractOptimisticTestCase
{
   protected CacheLoaderConfig getCacheLoaderConfig() throws Exception
   {
      return buildSingleCacheLoaderConfig(true, null, "org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader",
            "", false, false, false, false, false);
   }

   private CacheSPI createLocalCache() throws Exception
   {
      CacheSPI cache = createCacheUnstarted(true);
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig());

      cache.create();
      cache.start();
      return cache;
   }

   public void testPassivationLocal() throws Exception
   {
      CacheSPI cache = createLocalCache();
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();

      // clean up
      cache.removeNode(fqn);
      loader.remove(fqn);

      assertNull(loader.get(fqn));

      DummyTransactionManager mgr = DummyTransactionManager.getInstance();

      // put something in the cache
      mgr.begin();
      cache.put(fqn, key, value);
      mgr.commit();

      // should be nothing in the loader
      assertEquals(value, cache.get(fqn, key));
      assertNull(loader.get(fqn));

      // evict from cache
      mgr.begin();
      cache.evict(fqn);
      mgr.commit();

      mgr.begin();
      // should now be passivated in the loader
      // don't do a cache.get() first as this will activate this node from the loader again!
      // assertNull( cache.get( fqn ) );
      assertEquals(value, loader.get(fqn).get(key));

      // now do a cache.get()...
      assertEquals(value, cache.get(fqn, key));

      // and the object should now be removed from the loader
      assertNull(loader.get(fqn));
      mgr.commit();

      // clean up
      mgr.begin();
      cache.removeNode(fqn);
      loader.remove(fqn);
      mgr.commit();

   }
}
