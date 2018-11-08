package org.jboss.cache.invocationcontext;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.AbstractSingleCacheTest;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.transaction.TransactionContext;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.transaction.GenericTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Map;

/**
 * A test to ensure the transactional context is properly set up in the IC
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 */
@Test(groups = {"functional", "transaction"}, testName = "invocationcontext.OnePcTransactionTest")
public class OnePcTransactionTest extends AbstractSingleCacheTest
{
   private TransactionManager tm;

   protected CacheSPI createCache()
   {
      Configuration config = new Configuration();
      config.setTransactionManagerLookupClass(GenericTransactionManagerLookup.class.getName());
      config.setCacheMode(CacheMode.REPL_ASYNC);
      cache = (CacheSPI) new UnitTestCacheFactory().createCache(config, true, getClass());
      tm = cache.getTransactionManager();
      return cache;
   }

   public void testTxExistenceAfterWrite() throws Exception
   {
      // make sure we have a running transaction.
      tm.begin();

      assertNull("Tx should not have been set up yet", cache.getInvocationContext().getTransaction());
      assertNull("Gtx should not have been set up yet", cache.getInvocationContext().getGlobalTransaction());

      // now make a WRITE call into the cache (should start a tx)
      cache.getRoot().put("k", "v");
      Map data = cache.getRoot().getData();
      assertEquals("Data map should not empty", 1, data.size());

      // but now we should have a local tx registered in the invocation context
      assertNotNull("Tx should have been set up by now", cache.getInvocationContext().getTransaction());
      assertEquals("The same current transaction should be associated with this invocation ctx.", tm.getTransaction(), cache.getInvocationContext().getTransaction());
      assertNotNull("Gtx should have been set up by now", cache.getInvocationContext().getGlobalTransaction());

      tm.commit();
   }

   public void testTxExistenceAfterRead() throws Exception
   {
      // make sure we have a running transaction.
      tm.begin();

      assertNull("Tx should not have been set up yet", cache.getInvocationContext().getTransaction());
      assertNull("Gtx should not have been set up yet", cache.getInvocationContext().getGlobalTransaction());

      // now make a WRITE call into the cache (should start a tx)
      Object value = cache.get(Fqn.ROOT, "k");
      assertNull("Value should be null", value);

      // but now we should have a local tx registered in the invocation context
      assertNotNull("Tx should have been set up by now", cache.getInvocationContext().getTransaction());
      assertEquals("The same current transaction should be associated with this invocation ctx.", tm.getTransaction(), cache.getInvocationContext().getTransaction());
      assertNotNull("Gtx should have been set up by now", cache.getInvocationContext().getGlobalTransaction());

      tm.commit();
   }

   public void testScrubbingAfterOnePhaseCommit() throws Exception
   {
      TwoPcTransactionTest.doScrubbingTest(cache, tm, true);
   }

   public void testScrubbingAfterOnePhaseRollback() throws Exception
   {
      TwoPcTransactionTest.doScrubbingTest(cache, tm, false);
   }
   
}
