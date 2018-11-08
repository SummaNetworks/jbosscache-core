package org.jboss.cache.transaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheException;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.invocation.CacheInvocationDelegate;
import org.jboss.cache.util.CachePrinter;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.List;

/**
 * To test JBCACHE-923
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 */
@Test(groups = {"functional", "transaction"}, enabled = true, sequential = true, testName = "transaction.SimultaneousRollbackAndPutTest")
// Known issue - disabled because of JBCACHE-923
public class SimultaneousRollbackAndPutTest
{
   private Cache cache;
   private TransactionManager tm;
   private Fqn A = Fqn.fromString("/a"), B = Fqn.fromString("/b");
   private Log log = LogFactory.getLog(SimultaneousRollbackAndPutTest.class);

   @BeforeMethod(alwaysRun = true)
   protected void setUp() throws Exception
   {
      if (cache == null) {
         cache = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
         cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
         cache.start();
         tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
         cache.put(A, "k", "v");
      }
   }

   @AfterTest(alwaysRun = true)
   protected void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   @AfterMethod(alwaysRun = true)
   protected void resetCache() throws Exception
   {
      try
      {
         cache.removeNode(B);
         cache.getRoot().getChild(A).clearData();
         cache.put(A, "k", "v");
         // make sure we clean up any gtx2EntryMap associa with the thread
         TestingUtil.killTransactions(cache);
      }
      catch (Exception e)
      {
         // restart the cache
         tearDown();
         setUp();
      }

   }

   @Test(invocationCount = 100, alwaysRun = false, enabled = false, description = "This is to do with a flaw in the way pessimistic locking deals with transactions.  See JBCACHE-923")
   public void testStaleLocks() throws Exception
   {
      // scenario:
      // Thread starts tx in cache.  E.g., create and put into B
      tm.begin();
      final Transaction t = tm.getTransaction();
      final List exceptions = new ArrayList();

      cache.put(B, "k", "v");

      // now the container should attempt to rollback the tx in a separate thread.
      Thread rollbackThread = new Thread("RollbackThread")
      {
         public void run()
         {
            try
            {
               t.rollback();
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };
      rollbackThread.start();

      try
      {
         // now try and put stuff in the main thread again
         cache.put(A, "k2", "v2");
         tm.commit();
//         assert false : "Should never reach here";
      }
      catch (RollbackException expected)
      {
         // this is expected.
      }
      catch (CacheException ce)
      {
         // also expected at times
      }

      rollbackThread.join();

      if (((CacheInvocationDelegate) cache).getNumberOfLocksHeld() > 0)
      {
         log.fatal("***********");
         log.fatal(CachePrinter.printCacheLockingInfo(cache));
         log.fatal("***********");
      }

      assert 0 == ((CacheInvocationDelegate) cache).getNumberOfLocksHeld();

      if (exceptions.size() > 0) throw ((Exception) exceptions.get(0));
   }
}
