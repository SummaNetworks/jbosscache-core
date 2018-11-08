package org.jboss.cache.mgmt;

import org.jboss.cache.interceptors.CacheLoaderInterceptor;
import org.jboss.cache.interceptors.CacheStoreInterceptor;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.Test;

import java.util.HashMap;

/**
 * Simple functional tests for CacheLoaderInterceptor and CacheStoreInterceptor statistics
 *
 * @author Jerry Gauthier
 * @version $Id: CacheLoaderTest.java 7735 2009-02-19 13:40:55Z manik.surtani@jboss.com $
 */
@Test(groups = "functional", sequential = true, testName = "mgmt.CacheLoaderTest")
public class CacheLoaderTest extends MgmtTestBase
{
   public void testCacheLoaderMgmt() throws Exception
   {
      assertNotNull("Cache is null.", cache);

      // Note: because these tests are normally executed without a server, the interceptor
      // MBeans are usually not available for use in the tests.  Consequently it's necessary
      // to obtain references to the interceptors and work with them directly.
      CacheLoaderInterceptor loader = TestingUtil.findInterceptor(cache, CacheLoaderInterceptor.class);
      assertNotNull("CacheLoaderInterceptor not found.", loader);
      CacheStoreInterceptor store = TestingUtil.findInterceptor(cache, CacheStoreInterceptor.class);
      assertNotNull("CacheStoreInterceptor not found.", store);

      // verify cache loader statistics for entries loaded into cache
      int miss = 5;
      int load = 0;
      int stores = 5;
      assertEquals("CacheLoaderLoads count error: ", load, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error: ", stores, store.getCacheLoaderStores());

      // now try retrieving a valid attribute and an invalid attribute
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + AUSTRIA, cache.get(AUSTRIA, CAPITAL));
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + AUSTRIA, cache.get(AUSTRIA, AREA));

      // verify statistics after retrieving entries - misses should still be same since nodes were already loaded
      assertEquals("CacheLoaderLoads count error: ", load, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error: ", stores, store.getCacheLoaderStores());

      // now try retrieving an attribute for an invalid node
      assertNull("Retrieval error: did not expect to retrieve " + CAPITAL + " for " + POLAND, cache.get(POLAND, CAPITAL));

      // verify statistics for after retrieving entries - misses should now be +1 after attempt to load Poland
      miss++;
      assertEquals("CacheLoaderLoads count error: ", load, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error: ", stores, store.getCacheLoaderStores());

      // now evict Austria and confirm that it's no longer in cache
      cache.evict(AUSTRIA, false);
      assertNull("Retrieval error: did not expect to find node " + AUSTRIA + " in cache", cache.peek(AUSTRIA, false));

      // now try retrieving its attributes again - first retrieval should trigger a cache load
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + AUSTRIA, cache.get(AUSTRIA, CAPITAL));
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + AUSTRIA, cache.get(AUSTRIA, CURRENCY));

      // verify statistics after retrieving evicted entry - loads should now be +1
      load++;
      assertEquals("CacheLoaderLoads count error: ", load, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error: ", stores, store.getCacheLoaderStores());

      // now remove Austria and confirm that it's not in cache or loader
      cache.removeNode(AUSTRIA);
      assertNull("Retrieval error: did not expect to find node " + AUSTRIA + " in cache", cache.peek(AUSTRIA, false));
      assertFalse("Retrieval error: did not expect to find node " + AUSTRIA + " in loader", cl.exists(AUSTRIA));

      // verify statistics after removing entry - should be unchanged
      assertEquals("CacheLoaderLoads count error: ", load, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error: ", stores, store.getCacheLoaderStores());

      // now try retrieving attributes again - each attempt should fail and cause a miss since node is now removed
      assertNull("Retrieval error: did not expect to retrieve " + CAPITAL + " for " + AUSTRIA, cache.get(AUSTRIA, CAPITAL));
      assertNull("Retrieval error: did not expect to retrieve " + CURRENCY + " for " + AUSTRIA, cache.get(AUSTRIA, CURRENCY));

      // verify statistics after trying to retrieve removed node's attributes - should be two more misses
      miss += 2;
      assertEquals("CacheLoaderLoads count error: ", load, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error: ", stores, store.getCacheLoaderStores());

      // add a new node - this should cause a store
      stores++;
      miss++;
      cache.put(POLAND, new HashMap<String, Object>());
      assertEquals("CacheLoaderLoads count error: ", load, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error: ", stores, store.getCacheLoaderStores());

      // add two attributes - this should cause two stores
      stores += 2;
      cache.put(POLAND, CAPITAL, "Warsaw");
      cache.put(POLAND, CURRENCY, "Zloty");
      assertEquals("CacheLoaderLoads count error: ", load, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error: ", stores, store.getCacheLoaderStores());

      // evict Poland and then try to retrieve an invalid attribute - this will cause a load as the node is restored
      load++;
      cache.evict(POLAND, false);
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + POLAND, cache.get(POLAND, AREA));
      assertEquals("CacheLoaderLoads count error: ", load, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error: ", stores, store.getCacheLoaderStores());

      // reset statistics
      loader.resetStatistics();
      store.resetStatistics();

      // check the statistics again
      assertEquals("CacheLoaderLoads count error after reset: ", 0, loader.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error after reset: ", 0, loader.getCacheLoaderMisses());
      assertEquals("CacheLoaderStores count error after reset: ", 0, store.getCacheLoaderStores());
   }
}
