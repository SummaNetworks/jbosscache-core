package org.jboss.cache.mgmt;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.interceptors.InvalidationInterceptor;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;

/**
 * Simple functional tests for InvalidationInterceptor statistics
 *
 * @author Jerry Gauthier
 * @version $Id: InvalidationTest.java 7422 2009-01-09 09:21:18Z mircea.markus $
 */
@Test(groups = {"functional"}, sequential = true, testName = "mgmt.InvalidationTest")
public class InvalidationTest
{
   private static final String CLUSTER_NAME = "InvalidationTestCluster";
   private static final String CAPITAL = "capital";
   private static final String CURRENCY = "currency";
   private static final String POPULATION = "population";
   private static final String AREA = "area";

   private CacheSPI<Object, Object> cache1 = null;
   private CacheSPI<Object, Object> cache2 = null;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache1 = createCache(CLUSTER_NAME);
      cache2 = createCache(CLUSTER_NAME);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache1 != null)
      {
         TestingUtil.killCaches(cache1);
         cache1 = null;
      }
      if (cache2 != null)
      {
         TestingUtil.killCaches(cache2);
         cache2 = null;
      }
   }

   public void testInvalidationMgmt() throws Exception
   {
      assertNotNull("Cache1 is null.", cache1);
      assertNotNull("Cache2 is null.", cache2);

      // populate cache1 with test data
      loadCache1(cache1);

      // confirm that data is in cache1 and not in cache2
      Fqn key = Fqn.fromString("Europe/Austria");
      assertNotNull("Cache1 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));
      assertNull("Cache2 retrieval error: did not expect to retrieve " + CAPITAL + " for " + key, cache2.get(key, CAPITAL));
      key = Fqn.fromString("Europe/Albania");
      assertNotNull("Cache1 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));
      assertNull("Cache2 retrieval error: did not expect to retrieve " + CAPITAL + " for " + key, cache2.get(key, CAPITAL));

      // populate cache2 with test data - this will invalidate Austria
      loadCache2(cache2);

      // confirm that Austria is now in cache2 and not in cache1
      key = Fqn.fromString("Europe/Austria");
      assertNull("Cache1 retrieval error: did not expect to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));
      assertNotNull("Cache2 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache2.get(key, CAPITAL));

      // confirm that Albania is still in cache1
      key = Fqn.fromString("Europe/Albania");
      assertNotNull("Cache1 retrieval error after unrelated eviction: expected to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));

      // Note: because these tests are normally executed without a server, the interceptor
      // MBeans are usually not available for use in the tests.  Consequently it's necessary
      // to obtain a reference to the interceptor and work with it directly.
      InvalidationInterceptor mgmt1 = TestingUtil.findInterceptor(cache1, InvalidationInterceptor.class);
      assertNotNull("Cache1 InvalidationInterceptor not found.", mgmt1);

      InvalidationInterceptor mgmt2 = TestingUtil.findInterceptor(cache2, InvalidationInterceptor.class);
      assertNotNull("Cache2 InvalidationInterceptor not found.", mgmt2);

      assertTrue("Cache1 not configured to use MBeans", cache1.getConfiguration().getExposeManagementStatistics());
      assertTrue("Cache2 not configured to use MBeans", cache2.getConfiguration().getExposeManagementStatistics());
      assertTrue("InvalidationInterceptor on Cache1 not set up to use statistics!", mgmt1.getStatisticsEnabled());
      assertTrue("InvalidationInterceptor on Cache2 not set up to use statistics!", mgmt2.getStatisticsEnabled());

      // verify basic statistics for entries loaded into cache
      assertEquals("Cache1 Invalidations count error: ", new Long(6), new Long(mgmt1.getInvalidations()));
      assertEquals("Cache2 Invalidations count error: ", new Long(9), new Long(mgmt2.getInvalidations()));

      // reset statistics
      mgmt1.resetStatistics();
      mgmt2.resetStatistics();

      // check the statistics again
      assertEquals("Cache1 Invalidations count error after reset: ", new Long(0), new Long(mgmt1.getInvalidations()));
      assertEquals("Cache2 Invalidations count error after reset: ", new Long(0), new Long(mgmt2.getInvalidations()));

   }

   private void loadCache1(CacheSPI<Object, Object> cache)
   {
      cache.put("Europe", new HashMap());
      cache.put("Europe/Austria", new HashMap());
      cache.put("Europe/Austria", CAPITAL, "Vienna");
      cache.put("Europe/Austria", CURRENCY, "Euro");
      cache.put("Europe/Austria", POPULATION, 8184691);

      HashMap<Object, Object> albania = new HashMap<Object, Object>(4);
      albania.put(CAPITAL, "Tirana");
      albania.put(CURRENCY, "Lek");
      albania.put(POPULATION, 3563112);
      albania.put(AREA, 28748);
      cache.put("Europe/Albania", albania);

   }

   private void loadCache2(CacheSPI<Object, Object> cache)
   {
      // the following line will invalidate /Europe (and its' children) across the cluster!!
//      cache.put("Europe", new HashMap<Object, Object>());
      cache.put("Europe/Austria", new HashMap<Object, Object>());
      cache.put("Europe/Austria", CAPITAL, "Vienna");
      cache.put("Europe/Austria", CURRENCY, "Euro");
      cache.put("Europe/Austria", POPULATION, 8184691);

      cache.put("Europe/Romania", new HashMap<Object, Object>());
      cache.put("Europe/Romania", CAPITAL, "Bucharest");
      cache.put("Europe/Romania", CURRENCY, "Leu");
      cache.put("Europe/Romania", POPULATION, 22329977);
      cache.put("Europe/Romania", AREA, 237500);

   }

   private CacheSPI<Object, Object> createCache(String clusterName)
   {

      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.INVALIDATION_SYNC);
      c.setCacheMode(Configuration.CacheMode.INVALIDATION_SYNC);
      c.setExposeManagementStatistics(true);
      c.setClusterName(clusterName);
      return (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
   }
}
