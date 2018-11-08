package org.jboss.cache;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;

/**
 * Simple functional tests for CacheSPI
 *
 * @author Bela Ban
 * @version $Id: TreeCacheFunctionalTest.java 7284 2008-12-12 05:00:02Z mircea.markus $
 */
@Test(groups = "functional", sequential = true, testName = "TreeCacheFunctionalTest")
public class TreeCacheFunctionalTest
{
   CacheSPI<Object, Object> cache = null;
   final Fqn FQN = Fqn.fromString("/myNode");

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      cache.create();
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache != null)
      {
         cache.stop();
         cache.destroy();
         cache = null;
      }
   }

   public void testPut() throws CacheException
   {
      cache.put("/a/b/c", "age", 38);
      assertEquals(cache.get("/a/b/c", "age"), 38);
      assertNotNull(cache.getNode("/a/b/c"));
      assertEquals(0, cache.getNumberOfLocksHeld());
//      assertEquals(0, cache.getLockTable().size());
   }


   public void testPutNullKey() throws CacheException
   {
      Object key = null;
      cache.put("/a/b/c", key, "val");
   }

   public void testPutNullValue() throws CacheException
   {
      Object val = null;
      cache.put("/a/b/c", "key", val);
   }

   public void testPutNullKeyAndValues() throws CacheException
   {
      Object key = null, val = null;
      cache.put("/a/b/c", key, val);
   }

   public void testPutMapsWithNullValues() throws CacheException
   {
      HashMap<Object, Object> map = new HashMap<Object, Object>();
      map.put("key", null);
      map.put(null, "val");
      map.put("a", "b");
      map.put(null, null);
      cache.put("/a/b/c", map);
   }

   public void testPutKeys() throws CacheException
   {
      cache.put("/a/b/c", "age", 38);
      cache.put("/a/b/c", "name", "Bela");
      assertEquals(cache.get("/a/b/c", "age"), 38);
      assertNotNull(cache.getNode("/a/b/c"));
      assertEquals(cache.getNode("/a/b/c").getKeys().size(), 2);
      assertEquals(cache.exists("/a/b/c"), true);
      assertEquals(0, cache.getNumberOfLocksHeld());
//      assertEquals(0, cache.getLockTable().size());
   }

   public void testRemove() throws CacheException
   {
      cache.put("/a/b/c", null);
      cache.put("/a/b/c/1", null);
      cache.put("/a/b/c/2", null);
      cache.put("/a/b/c/3", null);
      cache.put("/a/b/c/3/a/b/c", null);

      cache.removeNode("/a/b/c");
      assertEquals(0, cache.getNumberOfLocksHeld());
//      assertEquals(0, cache.getLockTable().size());
   }
}
