package org.jboss.cache.invocationcontext;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.AbstractSingleCacheTest;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.transaction.TransactionContext;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

import javax.transaction.TransactionManager;
import javax.transaction.Transaction;

/**
 * @author Mircea.Markus@jboss.com
 */
public class TwoPcTransactionTest extends AbstractSingleCacheTest
{
   private TransactionManager tm;

   protected CacheSPI createCache()
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache("configs/local-tx.xml", getClass());
      tm = cache.getTransactionManager();
      return cache;
   }

   @SuppressWarnings("deprecation")
   static void doScrubbingTest(CacheSPI cache, TransactionManager tm, boolean commit) throws Exception
   {
      // Start clean
      cache.getInvocationContext().reset();

      tm.begin();
      TransactionTable tt = cache.getTransactionTable();
      cache.getRoot().put("key", "value");

      assertNotNull("Tx should have been set up by now", cache.getInvocationContext().getTransaction());
      assertEquals("The same current transaction should be associated with this invocation ctx.", tm.getTransaction(), cache.getInvocationContext().getTransaction());
      assertNotNull("Gtx should have been set up by now", cache.getInvocationContext().getGlobalTransaction());

      Transaction tx = tm.getTransaction();
      TransactionContext transactionContext = tt.get(tt.get(tx));

      if (commit)
      {
         tm.commit();
      } else
      {
         tm.rollback();
      }

      assertNull("Tx should have been scrubbed", cache.getInvocationContext().getTransaction());
      assertNull("Gtx should have been scrubbed", cache.getInvocationContext().getGlobalTransaction());
      assertEquals("Method call should have been scrubbed", null, cache.getInvocationContext().getMethodCall());
      assertEquals("Cache command should have been scrubbed", null, cache.getInvocationContext().getCommand());

      // check that the transaction transactionContext hasn't leaked stuff.
      assert transactionContext.getModifications().isEmpty() : "Should have scrubbed modifications in transaction transactionContext";
      assert transactionContext.getLocks().isEmpty() : "Should have scrubbed modifications in transaction transactionContext";
      assert transactionContext.getOrderedSynchronizationHandler() == null : "Should have removed the ordered sync handler";
   }

   public void testScrubbingAfterCommit() throws Exception
   {
      doScrubbingTest(cache, tm, true);
   }

   public void testScrubbingAfterRollback() throws Exception
   {
      doScrubbingTest(cache, tm, false);
   }



}
