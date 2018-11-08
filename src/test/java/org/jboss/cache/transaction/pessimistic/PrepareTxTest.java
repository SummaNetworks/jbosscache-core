package org.jboss.cache.transaction.pessimistic;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * Created by IntelliJ IDEA.
 * User: bela
 * Date: Jun 9, 2004
 * Time: 9:05:19 AM
 */
@Test(groups = {"functional", "transaction"}, sequential = true, testName = "transaction.pessimistic.PrepareTxTest")
public class PrepareTxTest
{
   CacheSPI<String, String> cache;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(false, getClass());
      cache.getConfiguration().setCacheMode("local");
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      cache.create();
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   /**
    * Tests cache modification <em>inside</em> the afterCompletion() callback. Reproduces a bug fixed in
    * connection with JBossCache being used as Hibernate's second level cache
    *
    * @throws Exception
    * @throws NotSupportedException
    */
   public void testCacheModificationInBeforeCompletionPhase() throws Exception
   {
      int numLocks = 0;
      TransactionManager mgr = cache.getTransactionManager();
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // this will cause the cache to register with TransactionManager for TX completion callbacks
      cache.put("/one/two/three", "key1", "val1");
      numLocks = cache.getNumberOfLocksHeld();
      assertEquals(4, numLocks);

      // we register *second*
      tx.registerSynchronization(new Synchronization()
      {

         public void beforeCompletion()
         {
            try
            {
               cache.put("/a/b/c", null);
            }
            catch (CacheException e)
            {
               e.printStackTrace();
            }
         }

         public void afterCompletion(int status)
         {
         }
      });

      mgr.commit();
      numLocks = cache.getNumberOfLocksHeld();
      assertEquals(0, numLocks);

      int num_local_txs, num_global_txs;
      TransactionTable tx_table = cache.getTransactionTable();
      num_local_txs = tx_table.getNumLocalTransactions();
      num_global_txs = tx_table.getNumGlobalTransactions();
      assertEquals(num_local_txs, num_global_txs);
      assertEquals(0, num_local_txs);
   }

   /**
    * Tests cache modification <em>inside</em> the afterCompletion() callback. Reproduces a bug fixed in
    * connection with JBossCache being used as Hibernate's second level cache
    *
    * @throws Exception
    * @throws NotSupportedException
    */
   public void testCacheModificationInAfterCompletionPhase() throws Exception
   {
      int numLocks = 0;
      TransactionManager mgr = cache.getTransactionManager();
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // this will cause the cache to register with TransactionManager for TX completion callbacks
      cache.put("/one/two/three", "key1", "val1");
      numLocks = cache.getNumberOfLocksHeld();
      assertEquals(4, numLocks);

      // we register *second*
      tx.registerSynchronization(new Synchronization()
      {

         public void beforeCompletion()
         {
         }

         public void afterCompletion(int status)
         {
            try
            {
               cache.put("/a/b/c", null);
            }
            catch (CacheException e)
            {
               e.printStackTrace();
            }
         }
      });

      mgr.commit();
      numLocks = cache.getNumberOfLocksHeld();
      assertEquals(0, numLocks);

      int num_local_txs, num_global_txs;
      TransactionTable tx_table = cache.getTransactionTable();
      num_local_txs = tx_table.getNumLocalTransactions();
      num_global_txs = tx_table.getNumGlobalTransactions();
      assertEquals(num_local_txs, num_global_txs);
      assertEquals(0, num_local_txs);
   }

}
