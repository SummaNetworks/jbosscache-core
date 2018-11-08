package org.jboss.cache.mgmt;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.interceptors.CacheMgmtInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import org.jboss.cache.util.TestingUtil;

/**
 * Simple functional tests for CacheMgmtInterceptor
 *
 * @author Jerry Gauthier
 * @version $Id: MgmtCoreTest.java 7284 2008-12-12 05:00:02Z mircea.markus $
 */
@Test(groups = {"functional"}, sequential = true, testName = "mgmt.MgmtCoreTest")
public class MgmtCoreTest
{
   private static final String CAPITAL = "capital";
   private static final String CURRENCY = "currency";
   private static final String POPULATION = "population";
   private static final String AREA = "area";

   CacheSPI<Object, Object> cache = null;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setExposeManagementStatistics(true);
      cache.create();
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   public void testCacheMgmt() throws Exception
   {
      assertNotNull("Cache is null.", cache);

      // populate the cache with test data
      loadCacheData();

      // Note: because these tests are normally executed without a server, the interceptor
      // MBeans are usually not available for use in the tests.  Consequently it's necessary
      // to obtain a reference to the interceptor and work with it directly.
      CacheMgmtInterceptor mgmt = getCacheMgmtInterceptor();
      assertNotNull("CacheMgmtInterceptor not found.", mgmt);

      // try some successful retrievals - fail if they miss since this shouldn't occur
      Fqn key = Fqn.fromString("Europe/Austria");
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache.get(key, CAPITAL));
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + key, cache.get(key, CURRENCY));
      assertNotNull("Retrieval error: expected to retrieve " + POPULATION + " for " + key, cache.get(key, POPULATION));
      key = Fqn.fromString("Europe/England");
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache.get(key, CAPITAL));
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + key, cache.get(key, CURRENCY));
      assertNotNull("Retrieval error: expected to retrieve " + POPULATION + " for " + key, cache.get(key, POPULATION));
      key = Fqn.fromString("Europe/France");
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache.get(key, CAPITAL));
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + key, cache.get(key, CURRENCY));
      assertNotNull("Retrieval error: expected to retrieve " + POPULATION + " for " + key, cache.get(key, POPULATION));
      key = Fqn.fromString("Europe/Germany");
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache.get(key, CAPITAL));
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + key, cache.get(key, CURRENCY));
      assertNotNull("Retrieval error: expected to retrieve " + POPULATION + " for " + key, cache.get(key, POPULATION));
      key = Fqn.fromString("Europe/Italy");
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache.get(key, CAPITAL));
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + key, cache.get(key, CURRENCY));
      assertNotNull("Retrieval error: expected to retrieve " + POPULATION + " for " + key, cache.get(key, POPULATION));
      key = Fqn.fromString("Europe/Switzerland");
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache.get(key, CAPITAL));
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + key, cache.get(key, CURRENCY));
      assertNotNull("Retrieval error: expected to retrieve " + POPULATION + " for " + key, cache.get(key, POPULATION));

      // try some unsuccessful retrievals - fail if they hit since this shouldn't occur
      key = Fqn.fromString("Europe/Austria");
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + key, cache.get(key, AREA));
      key = Fqn.fromString("Europe/England");
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + key, cache.get(key, AREA));
      key = Fqn.fromString("Europe/France");
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + key, cache.get(key, AREA));
      key = Fqn.fromString("Europe/Germany");
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + key, cache.get(key, AREA));
      key = Fqn.fromString("Europe/Italy");
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + key, cache.get(key, AREA));
      key = Fqn.fromString("Europe/Switzerland");
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + key, cache.get(key, AREA));

      // verify basic statistics for entries loaded into cache
      assertEquals("NumberOfNodes count error: ", new Integer(13), new Integer(mgmt.getNumberOfNodes()));
      assertEquals("NumberOfAttributes count error: ", new Integer(40), new Integer(mgmt.getNumberOfAttributes()));
      assertEquals("Stores count error: ", new Long(40), new Long(mgmt.getStores()));
      assertEquals("Evictions count error: ", new Long(0), new Long(mgmt.getEvictions()));
      assertEquals("Hits count error: ", new Long(18), new Long(mgmt.getHits()));
      assertEquals("Misses count error: ", new Long(6), new Long(mgmt.getMisses()));
      assertEquals("HitMissRatio error: ", 0.75, mgmt.getHitMissRatio());
      assertEquals("ReadWriteRatio error: ", 0.60, mgmt.getReadWriteRatio());

      // now evict some nodes (each node has 3 attributes)
      cache.evict(Fqn.fromString("Europe/Czech Republic"));
      cache.evict(Fqn.fromString("Europe/Poland"));
      assertEquals("NumberOfNodes count error after evictions: ", new Integer(11), new Integer(mgmt.getNumberOfNodes()));
      assertEquals("NumberOfAttributes count error after evictions: ", new Integer(34), new Integer(mgmt.getNumberOfAttributes()));
      assertEquals("Stores count error: ", new Long(40), new Long(mgmt.getStores()));
      assertEquals("Evictions count error: ", new Long(2), new Long(mgmt.getEvictions()));

      // time is measured in seconds so add a delay to ensure it's not rounded to zero
      Thread.sleep(1000);
      long t1 = mgmt.getElapsedTime();
      if (t1 < 1)
      {
         fail("ElapsedTime should be greater than 0 seconds.");
      }
      t1 = mgmt.getTimeSinceReset();
      if (t1 < 1)
      {
         fail("TimeSinceReset should be greater than 0 seconds.");
      }
      Thread.sleep(1000);

      // now reset the statistics (node count and attribute count aren't affected)
      mgmt.resetStatistics();

      // check times again
      t1 = mgmt.getElapsedTime();
      if (t1 < 2)
      {
         fail("ElapsedTime after reset should be greater than 1 second.");
      }
      t1 = mgmt.getTimeSinceReset();
      if (t1 > 1)// assumes that reset takes less than 2 seconds
      {
         fail("TimeSinceReset after reset should be less than 2 seconds.");
      }

      // check other statistics
      assertEquals("NumberOfNodes count error after reset: ", new Integer(11), new Integer(mgmt.getNumberOfNodes()));
      assertEquals("NumberOfAttributes count error after reset: ", new Integer(34), new Integer(mgmt.getNumberOfAttributes()));
      assertEquals("Stores count error after reset: ", new Long(0), new Long(mgmt.getStores()));
      assertEquals("Evictions count error after reset: ", new Long(0), new Long(mgmt.getEvictions()));
      assertEquals("Hits count error after reset: ", new Long(0), new Long(mgmt.getHits()));
      assertEquals("Misses count error after reset: ", new Long(0), new Long(mgmt.getMisses()));
   }

   private void loadCacheData()
   {
      cache.put("Europe", new HashMap());
      cache.put("Europe/Austria", new HashMap());
      cache.put("Europe/Czech Republic", new HashMap());
      cache.put("Europe/England", new HashMap());
      cache.put("Europe/France", new HashMap());
      cache.put("Europe/Germany", new HashMap());
      cache.put("Europe/Italy", new HashMap());
      cache.put("Europe/Poland", new HashMap());
      cache.put("Europe/Switzerland", new HashMap());

      cache.put("Europe/Austria", CAPITAL, "Vienna");
      cache.put("Europe/Czech Republic", CAPITAL, "Prague");
      cache.put("Europe/England", CAPITAL, "London");
      cache.put("Europe/France", CAPITAL, "Paris");
      cache.put("Europe/Germany", CAPITAL, "Berlin");
      cache.put("Europe/Italy", CAPITAL, "Rome");
      cache.put("Europe/Poland", CAPITAL, "Warsaw");
      cache.put("Europe/Switzerland", CAPITAL, "Bern");

      cache.put("Europe/Austria", CURRENCY, "Euro");
      cache.put("Europe/Czech Republic", CURRENCY, "Czech Koruna");
      cache.put("Europe/England", CURRENCY, "British Pound");
      cache.put("Europe/France", CURRENCY, "Euro");
      cache.put("Europe/Germany", CURRENCY, "Euro");
      cache.put("Europe/Italy", CURRENCY, "Euro");
      cache.put("Europe/Poland", CURRENCY, "Zloty");
      cache.put("Europe/Switzerland", CURRENCY, "Swiss Franc");

      cache.put("Europe/Austria", POPULATION, 8184691);
      cache.put("Europe/Czech Republic", POPULATION, 10241138);
      cache.put("Europe/England", POPULATION, 60441457);
      cache.put("Europe/France", POPULATION, 60656178);
      cache.put("Europe/Germany", POPULATION, 82431390);
      cache.put("Europe/Italy", POPULATION, 58103033);
      cache.put("Europe/Poland", POPULATION, 38635144);
      cache.put("Europe/Switzerland", POPULATION, 7489370);

      HashMap<Object, Object> albania = new HashMap<Object, Object>(4);
      albania.put(CAPITAL, "Tirana");
      albania.put(CURRENCY, "Lek");
      albania.put(POPULATION, 3563112);
      albania.put(AREA, 28748);
      cache.put("Europe/Albania", albania);

      HashMap<Object, Object> hungary = new HashMap<Object, Object>(4);
      hungary.put(CAPITAL, "Budapest");
      hungary.put(CURRENCY, "Forint");
      hungary.put(POPULATION, 10006835);
      hungary.put(AREA, 93030);
      cache.put("Europe/Hungary", hungary);

      HashMap<Object, Object> romania = new HashMap<Object, Object>(4);
      romania.put(CAPITAL, "Bucharest");
      romania.put(CURRENCY, "Leu");
      romania.put(POPULATION, 22329977);
      romania.put(AREA, 237500);
      cache.put("Europe/Romania", romania);

      HashMap<Object, Object> slovakia = new HashMap<Object, Object>(4);
      slovakia.put(CAPITAL, "Bratislava");
      slovakia.put(CURRENCY, "Slovak Koruna");
      slovakia.put(POPULATION, 5431363);
      slovakia.put(AREA, 48845);
      cache.put("Europe/Slovakia", slovakia);

   }

   private CacheMgmtInterceptor getCacheMgmtInterceptor()
   {
      List<CommandInterceptor> interceptors = cache.getInterceptorChain();
      if (interceptors.isEmpty())
      {
         return null;
      }

      for (CommandInterceptor interceptor : interceptors)
      {
         if (interceptor instanceof CacheMgmtInterceptor)
         {
            return (CacheMgmtInterceptor) interceptor;
         }
      }
      return null;
   }

}
