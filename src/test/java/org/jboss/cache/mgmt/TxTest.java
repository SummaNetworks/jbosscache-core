package org.jboss.cache.mgmt;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.interceptors.TxInterceptor;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.HashMap;
import java.util.List;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Simple functional tests for TxInterceptor statistics
 *
 * @author Jerry Gauthier
 * @version $Id: TxTest.java 7557 2009-01-21 12:19:26Z mircea.markus $
 */
@Test(groups = {"functional"}, testName = "mgmt.TxTest")
public class TxTest
{
   private static final String CLUSTER_NAME = "TxTestCluster";
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

   @BeforeMethod(alwaysRun = true)
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

   public void testTxMgmt() throws Exception
   {
      assertNotNull("Cache1 is null.", cache1);
      assertNotNull("Cache2 is null.", cache2);

      // Note: because these tests are normally executed without a server, the interceptor
      // MBeans are usually not available for use in the tests.  Consequently it's necessary
      // to obtain a reference to the interceptor and work with it directly.
      TxInterceptor tx1 = getTxInterceptor(cache1);
      assertNotNull("Cache1 InvalidationInterceptor not found.", tx1);
      TxInterceptor tx2 = getTxInterceptor(cache2);
      assertNotNull("Cache2 InvalidationInterceptor not found.", tx2);

      TransactionManager tm1 = cache1.getTransactionManager();
      assertNotNull("TransactionManager is null.", tm1);

      // populate cache1 with test data - no transaction
      loadCacheNoTx(cache1);

      // confirm that data is in cache1 and in cache2
      Fqn key = Fqn.fromString("Europe/Austria");
      assertNotNull("Cache1 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));
      assertNotNull("Cache2 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache2.get(key, CAPITAL));
      key = Fqn.fromString("Europe/Albania");
      assertNotNull("Cache1 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));
      assertNotNull("Cache2 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache2.get(key, CAPITAL));

      // verify basic statistics for entries loaded into cache
      assertEquals("Cache1 Tx Prepares error after reset: ", new Long(0), new Long(tx1.getPrepares()));
      assertEquals("Cache1 Tx Commits error after reset: ", new Long(0), new Long(tx1.getCommits()));
      assertEquals("Cache1 Tx Rollbacks error after reset: ", new Long(0), new Long(tx1.getRollbacks()));
      assertEquals("Cache2 Tx Prepares error after reset: ", new Long(0), new Long(tx2.getPrepares()));
      assertEquals("Cache2 Tx Commits error after reset: ", new Long(0), new Long(tx2.getCommits()));
      assertEquals("Cache2 Tx Rollbacks error after reset: ", new Long(0), new Long(tx2.getRollbacks()));

      // populate cache1 with test data - then transaction commit
      loadCacheTxCommit(cache1, tm1);
      loadCacheTxCommit2(cache1, tm1);

      // confirm that committed data is in cache1 and in cache2
      key = Fqn.fromString("Europe/England");
      assertNotNull("Cache1 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));
      assertNotNull("Cache2 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache2.get(key, CAPITAL));
      key = Fqn.fromString("Europe/Hungary");
      assertNotNull("Cache1 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));
      assertNotNull("Cache2 retrieval error: expected to retrieve " + CAPITAL + " for " + key, cache2.get(key, CAPITAL));

      // populate cache1 with test data - then transaction rollback
      loadCacheTxRollback(cache1, tm1);

      // confirm that rolled back data is not in cache1 or cache2
      key = Fqn.fromString("Europe/France");
      assertNull("Cache1 retrieval error: did not expect to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));
      assertNull("Cache2 retrieval error: did not expect to retrieve " + CAPITAL + " for " + key, cache2.get(key, CAPITAL));
      key = Fqn.fromString("Europe/Germany");
      assertNull("Cache1 retrieval error: did not expect to retrieve " + CAPITAL + " for " + key, cache1.get(key, CAPITAL));
      assertNull("Cache2 retrieval error: did not expect to retrieve " + CAPITAL + " for " + key, cache2.get(key, CAPITAL));

      // check the statistics - transactions are only handled by JBoss Cache on the remote node (i.e., node2)
      assertEquals("Cache1 Tx Prepares error after reset: ", new Long(0), new Long(tx1.getPrepares()));
      assertEquals("Cache1 Tx Commits error after reset: ", new Long(0), new Long(tx1.getCommits()));
      assertEquals("Cache1 Tx Rollbacks error after reset: ", new Long(0), new Long(tx1.getRollbacks()));
      assertEquals("Cache2 Tx Prepares error after reset: ", new Long(2), new Long(tx2.getPrepares()));
      assertEquals("Cache2 Tx Commits error after reset: ", new Long(2), new Long(tx2.getCommits()));
      // rollbacks don't currently get propagated so the counter will be 0, not 1
      assertEquals("Cache2 Tx Rollbacks error after reset: ", new Long(0), new Long(tx2.getRollbacks()));

      // reset statistics
      tx1.resetStatistics();
      tx2.resetStatistics();

      // check the statistics again
      assertEquals("Cache1 Tx Prepares error after reset: ", new Long(0), new Long(tx1.getPrepares()));
      assertEquals("Cache1 Tx Commits error after reset: ", new Long(0), new Long(tx1.getCommits()));
      assertEquals("Cache1 Tx Rollbacks error after reset: ", new Long(0), new Long(tx1.getRollbacks()));
      assertEquals("Cache2 Tx Prepares error after reset: ", new Long(0), new Long(tx2.getPrepares()));
      assertEquals("Cache2 Tx Commits error after reset: ", new Long(0), new Long(tx2.getCommits()));
      assertEquals("Cache2 Tx Rollbacks error after reset: ", new Long(0), new Long(tx2.getRollbacks()));
   }

   private void loadCacheNoTx(CacheSPI<Object, Object> cache)
   {
      cache.put("Europe", new HashMap<Object, Object>());
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

   private void loadCacheTxCommit(CacheSPI<Object, Object> cache, TransactionManager tm) throws Exception
   {
      tm.begin();

      cache.put("Europe/Czech Republic", new HashMap<Object, Object>());
      cache.put("Europe/Czech Republic", CAPITAL, "Prague");
      cache.put("Europe/Czech Republic", CURRENCY, "Czech Koruna");
      cache.put("Europe/Czech Republic", POPULATION, 10241138);

      cache.put("Europe/England", new HashMap<Object, Object>());
      cache.put("Europe/England", CAPITAL, "London");
      cache.put("Europe/England", CURRENCY, "British Pound");
      cache.put("Europe/England", POPULATION, 60441457);

      tm.commit();
   }

   private void loadCacheTxCommit2(CacheSPI<Object, Object> cache, TransactionManager tm) throws Exception
   {
      tm.begin();

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

      tm.commit();
   }

   private void loadCacheTxRollback(CacheSPI<Object, Object> cache, TransactionManager tm) throws Exception
   {
      tm.begin();

      cache.put("Europe/France", new HashMap<Object, Object>());
      cache.put("Europe/France", CAPITAL, "Paris");
      cache.put("Europe/France", CURRENCY, "Euro");
      cache.put("Europe/France", POPULATION, 60656178);

      cache.put("Europe/Germany", new HashMap<Object, Object>());
      cache.put("Europe/Germany", CAPITAL, "Berlin");
      cache.put("Europe/Germany", CURRENCY, "Euro");
      cache.put("Europe/Germany", POPULATION, 82431390);

      tm.rollback();
   }

   private CacheSPI<Object, Object> createCache(String clusterName)
   {      
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);
      c.setSyncCommitPhase(true);
      c.setSyncRollbackPhase(true);
      c.setUseRegionBasedMarshalling(false);
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      c.setExposeManagementStatistics(true);
      c.setClusterName(clusterName);
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>)
              new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      
      cache.create();
      cache.start();
      return cache;
   }

   private TxInterceptor getTxInterceptor(CacheSPI cache)
   {
      List interceptors = cache.getInterceptorChain();
      if (interceptors.isEmpty())
      {
         return null;
      }

      for (Object o : interceptors)
      {
         if (o instanceof TxInterceptor)
         {
            return (TxInterceptor) o;
         }
      }
      return null;
   }

}
