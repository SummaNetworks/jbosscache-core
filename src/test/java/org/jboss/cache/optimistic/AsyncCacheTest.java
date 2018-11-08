/*
 * Created on 17-Feb-2005
 *
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Cache;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.loader.SamplePojo;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

@Test(groups = {"functional", "transaction", "optimistic"}, testName = "optimistic.AsyncCacheTest")
public class AsyncCacheTest extends AbstractOptimisticTestCase
{
   private CacheSPI<Object, Object> cache, cache2;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = createReplicatedCache(Configuration.CacheMode.REPL_ASYNC);
      cache2 = createReplicatedCache(Configuration.CacheMode.REPL_ASYNC);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      super.tearDown();
     TestingUtil.killCaches((Cache<Object, Object>) cache);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);
     cache = null;
      cache2 = null;
   }

   @Override
   protected CacheSPI<Object, Object> createCacheUnstarted(boolean optimistic) throws Exception
   {
      CacheSPI<Object, Object> cache = super.createCacheUnstarted(optimistic);
      cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      return cache;
   }

   public void testRemoteCacheBroadcast() throws Exception
   {

      assertEquals(2, cache.getMembers().size());
      assertEquals(2, cache2.getMembers().size());

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();

      //this sets

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      //GlobalTransaction globalTransaction = cache.getCurrentTransaction(tx);
      assertNotNull(mgr.getTransaction());
      mgr.commit();

      assertNull(mgr.getTransaction());

      //assert that the local cache is in the right state
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertTrue(cache.exists(Fqn.fromString("/one")));
      assertEquals(pojo, cache.get(Fqn.fromString("/one/two"), "key1"));

      // allow changes to replicate since this is async
      TestingUtil.sleepThread((long) 5000);

      assertEquals(0, cache2.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache2.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache2.exists(Fqn.fromString("/one/two")));
      assertTrue(cache2.exists(Fqn.fromString("/one")));
      assertEquals(pojo, cache2.get(Fqn.fromString("/one/two"), "key1"));
   }

   public void testTwoWayRemoteCacheBroadcast() throws Exception
   {
      assertEquals(2, cache.getMembers().size());
      assertEquals(2, cache2.getMembers().size());

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      //this sets
      cache.getCurrentTransaction(tx, false);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertNotNull(mgr.getTransaction());
      mgr.commit();

      assertNull(mgr.getTransaction());

      //assert that the local cache is in the right state
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertTrue(cache.exists(Fqn.fromString("/one")));
      assertEquals(pojo, cache.get(Fqn.fromString("/one/two"), "key1"));

      // let the async calls complete
      TestingUtil.sleepThread((long) 5000);

      assertEquals(0, cache2.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache2.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache2.exists(Fqn.fromString("/one/two")));
      assertTrue(cache2.exists(Fqn.fromString("/one")));

      assertEquals(pojo, cache2.get(Fqn.fromString("/one/two"), "key1"));
   }
}
