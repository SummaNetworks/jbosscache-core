package org.jboss.cache.invalidation;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.AbstractMultipleCachesTest;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.config.Configuration;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;

import javax.transaction.TransactionManager;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "invalidation.PessWithCacheLoaderInvalidationTest")
public class PessWithCacheLoaderInvalidationTest extends AbstractMultipleCachesTest
{
   private CacheSPI<Object, Object> cache1;
   private CacheSPI<Object, Object> cache2;
   
   protected void createCaches() throws Throwable
   {
      Configuration c = new Configuration();
      c.setStateRetrievalTimeout(3000);
      c.setCacheMode(Configuration.CacheMode.INVALIDATION_SYNC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      c.setCacheLoaderConfig(CacheLoaderInvalidationTest.getCacheLoaderConfig(getClass()));
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, true, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), true, getClass());
      registerCaches(cache1, cache2);
   }

   @BeforeMethod 
   public void clearCacheLoaderBetweenTests() throws Exception
   {
      DummySharedInMemoryCacheLoader sharedCl = (DummySharedInMemoryCacheLoader) cache1.getCacheLoaderManager().getCacheLoader();
      sharedCl.remove(Fqn.ROOT);
   }

   public void testPessimisticNonTransactionalWithCacheLoader() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");
      cache1.put(fqn, "key", "value");

      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals("value", cache2.get(fqn, "key"));

      // now make sure cache2 is in sync with cache1:
      cache2.put(fqn, "key", "value");
      assertEquals("value", cache2.get(fqn, "key"));
      assertEquals("value", cache1.get(fqn, "key"));

      // now test the invalidation:
      cache1.put(fqn, "key2", "value2");
      assertEquals("value2", cache1.get(fqn, "key2"));
      assertEquals("value2", cache2.get(fqn, "key2"));
      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals("value", cache2.get(fqn, "key"));
   }

   public void testPessimisticTransactionalWithCacheLoader() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");
      TransactionManager mgr = cache1.getTransactionManager();
      assertNull("Should be null", cache1.get(fqn, "key"));
      assertNull("Should be null", cache2.get(fqn, "key"));
      mgr.begin();
      cache1.put(fqn, "key", "value");
      assertEquals("value", cache1.get(fqn, "key"));
      mgr.commit();
      assertEquals("value", cache2.get(fqn, "key"));
      assertEquals("value", cache1.get(fqn, "key"));

      mgr.begin();
      cache1.put(fqn, "key2", "value2");
      assertEquals("value2", cache1.get(fqn, "key2"));
      mgr.rollback();
      assertEquals("value", cache2.get(fqn, "key"));
      assertEquals("value", cache1.get(fqn, "key"));
      assertNull("Should be null", cache1.get(fqn, "key2"));
      assertNull("Should be null", cache2.get(fqn, "key2"));
   }
}
