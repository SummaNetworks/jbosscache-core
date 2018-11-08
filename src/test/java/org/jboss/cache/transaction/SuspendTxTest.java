package org.jboss.cache.transaction;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * Based on a contribution by Owen Taylor
 *
 * @author otaylor@redhat.com
 * @author Manik Surtani (manik AT jboss DOT org)
 */
@Test(groups = {"functional", "transaction"}, sequential = true, testName = "transaction.SuspendTxTest")
public class SuspendTxTest
{
   CacheSPI<String, String> cache;
   TransactionManager mgr;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(false, getClass());
      cache.getConfiguration().setCacheMode("local");
      cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      cache.start();
      mgr = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      if (mgr.getTransaction() != null)
      {
         mgr.rollback();
      }
      mgr = null;
   }

   /**
    * Tests that locks created when a transaction is suspended are independent
    * from the transaction.
    */
   public void testSuspendedLocks() throws Exception
   {
      // create /one first
      cache.put("/one", null);
      cache.put("/a", null);
      mgr.begin();

      cache.put("/one/two", "key1", "val1");
      int numLocksBefore = cache.getNumberOfLocksHeld();

      Transaction tx = mgr.suspend();

      cache.put("/a/b", "key1", "val1");
      mgr.resume(tx);

      assertEquals(numLocksBefore, cache.getNumberOfLocksHeld());
   }

   /**
    * Tests that locks created when a transaction is suspended are independent
    * from the transaction.
    */
   public void testSuspendedUsingOptionsLocks() throws Exception
   {
      mgr.begin();

      cache.put("/one/two", "key1", "val1");
      int numLocksBefore = cache.getNumberOfLocksHeld();

      cache.getInvocationContext().getOptionOverrides().setFailSilently(true);// will cause any current gtx2EntryMap to be suspended for the duration of this call.
      cache.put(Fqn.fromString("/a/b"), "key1", "val1");

      assertEquals(numLocksBefore, cache.getNumberOfLocksHeld());
   }

}
