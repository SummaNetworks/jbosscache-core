/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.Option;
import org.jboss.cache.loader.CacheLoader;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * Tests optimistic locking with cache loaders
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 */
@Test(groups = {"functional", "transaction", "optimistic"}, sequential = true, testName = "optimistic.OptimisticWithCacheLoaderTest")
public class OptimisticWithCacheLoaderTest extends AbstractOptimisticTestCase
{

   public void testLoaderIndependently() throws Exception
   {
      CacheSPI cache = createCacheWithLoader();
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();

      // test the cache loader independently first ...
      loader.remove(fqn);
      assert loader.get(fqn) == null;
      loader.put(fqn, key, value);
      assertEquals(value, loader.get(fqn).get(key));
      // clean up
      loader.remove(fqn);
      assertNull(loader.get(fqn));
   }

   public void testCacheLoadOnTree() throws Exception
   {
      CacheLoader loader = null;
      try
      {
         CacheSPI<Object, Object> cache = createCacheWithLoader();
         loader = cache.getCacheLoaderManager().getCacheLoader();

         TransactionManager mgr = cache.getTransactionManager();
         Transaction tx;

         // make sure the fqn is not in cache
         assertNull(cache.getNode(fqn));

         // put something in the loader and make sure all tx's can see it
         loader.put(fqn, key, value);

         // start the 1st tx
         mgr.begin();
         tx = mgr.getTransaction();
         assertEquals(value, cache.get(fqn, key));
         mgr.suspend();

         // start a new tx
         mgr.begin();
         assertEquals(value, cache.get(fqn, key));
         mgr.commit();

         mgr.resume(tx);
         assertEquals(value, cache.get(fqn, key));
         mgr.commit();
      }
      finally
      {
         // cleanup
         if (loader != null) loader.remove(fqn);
      }
   }

   public void testCacheStoring() throws Exception
   {
      Transaction tx;
      CacheSPI<Object, Object> cache = createCacheWithLoader();
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();

      // test the cache ...
      TransactionManager mgr = cache.getTransactionManager();
      assertNull(mgr.getTransaction());
      mgr.begin();
      cache.put(fqn, key, value);
      mgr.commit();

      assertEquals(value, cache.get(fqn, key));

      //now lets see if the state has been persisted in the cache loader
      assertEquals(value, loader.get(fqn).get(key));


      mgr.begin();
      cache.removeNode(fqn);
      mgr.commit();

      assertNull(cache.get(fqn, key));
      //now lets see if the state has been persisted in the cache loader
      assertNull(loader.get(fqn));

      mgr.begin();
      cache.put(fqn, key, value);
      tx = mgr.getTransaction();
      mgr.suspend();

      // lets see what we've got halfway within a tx
      assertNull(cache.get(fqn, key));
      assertNull(loader.get(fqn));

      mgr.resume(tx);
      mgr.commit();

      // and after committing...
      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));

      // clean up loader
      loader.remove(fqn);
   }


   public void testCacheStoringImplicitTx() throws Exception
   {
      CacheSPI<Object, Object> cache = createCacheWithLoader();
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();

      // test the cache ...
      TransactionManager mgr = cache.getTransactionManager();
      assertNull(mgr.getTransaction());
      cache.put(fqn, key, value);

      assertEquals(value, cache.get(fqn, key));

      //now lets see if the state has been persisted in the cache loader
      assertEquals(value, loader.get(fqn).get(key));


      cache.removeNode(fqn);

      assertNull(cache.get(fqn, key));
      //now lets see if the state has been persisted in the cache loader
      assertNull(loader.get(fqn));

      cache.put(fqn, key, value);

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));

      // clean up loader
      loader.remove(fqn);
   }

   public void testCacheStoringImplicitTxOptionOverride() throws Exception
   {
      CacheSPI<Object, Object> cache = createCacheWithLoader();
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      Option option = new Option();
      option.setCacheModeLocal(true);
      cache.getInvocationContext().setOptionOverrides(option);
      cache.put(fqn, key, value);
      assertEquals(value, cache.get(fqn, key));
      //now lets see if the state has been persisted in the cache loader
      assertNotNull(loader.get(fqn));
      assertNotNull(value, loader.get(fqn).get(key));
      cache.removeNode(fqn);
   }


   public void testCacheLoading() throws Exception
   {
      CacheSPI<Object, Object> cache = createCacheWithLoader();
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();

      assertNull(cache.get(fqn, key));

      // put something in the loader
      loader.put(fqn, key, value);
      assertEquals(value, loader.get(fqn).get(key));

      // test that this can now be accessed by the cache
      assertEquals(value, cache.get(fqn, key));

      // clean up loader
      loader.remove(fqn);

      // what's in the cache now?  Should not ne null...
      assertNotNull(cache.get(fqn, key));
   }

   public void testCacheLoadingWithReplication() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createReplicatedCacheWithLoader(false);
      CacheLoader loader1 = cache1.getCacheLoaderManager().getCacheLoader();

      CacheSPI<Object, Object> cache2 = createReplicatedCacheWithLoader(false);
      CacheLoader loader2 = cache2.getCacheLoaderManager().getCacheLoader();

      // test the cache ...
      TransactionManager mgr = cache1.getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      // add something in cache1
      cache1.put(fqn, key, value);
      assertEquals(value, cache1.get(fqn, key));

      // test that loader1, loader2, cache2 doesnt have entry
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));
      assertNull(cache2.get(fqn, key));

      // commit
      mgr.commit();

      // test that loader1, loader2, cache2 has entry
      assertEquals(value, cache1.get(fqn, key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));
      assertEquals(value, cache2.get(fqn, key));

      // cache2 removes entry
      cache2.getTransactionManager().begin();
      cache2.removeNode(fqn);
      assertNull(cache2.get(fqn, key));
      // test that loader1, loader2 and cache2 have the entry
      assertEquals(value, cache1.get(fqn, key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));

      // commit
      cache2.getTransactionManager().commit();

      // test that the entry has been removed everywhere.
      assertNull(cache1.getNode(fqn));
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));
      assertNull(cache2.getNode(fqn));

   }

   public void testSharedCacheLoadingWithReplication() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createReplicatedCacheWithLoader(true);
      CacheLoader loader1 = cache1.getCacheLoaderManager().getCacheLoader();

      CacheSPI<Object, Object> cache2 = createReplicatedCacheWithLoader(true);
      CacheLoader loader2 = cache2.getCacheLoaderManager().getCacheLoader();

      // test the cache ...
      TransactionManager mgr = cache1.getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      // add something in cache1
      cache1.put(fqn, key, value);
      assertEquals(value, cache1.get(fqn, key));

      // test that loader1, loader2, cache2 doesnt have entry
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));
      assertNull(cache2.get(fqn, key));

      // commit
      mgr.commit();

      // test that loader1, loader2, cache2 has entry
      assertEquals(value, cache1.get(fqn, key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));
      assertEquals(value, cache2.get(fqn, key));

      // cache2 removes entry
      cache2.getTransactionManager().begin();
      cache2.removeNode(fqn);
      assertNull(cache2.get(fqn, key));
      // test that loader1, loader2 and cache2 have the entry
      assertEquals(value, cache1.get(fqn, key));
      assertEquals(value, loader1.get(fqn).get(key));
      assertEquals(value, loader2.get(fqn).get(key));

      // commit
      cache2.getTransactionManager().commit();

      // test that the entry has been removed everywhere.
      assertNull(cache1.getNode(fqn));
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));
      assertNull(cache2.getNode(fqn));

   }
}
