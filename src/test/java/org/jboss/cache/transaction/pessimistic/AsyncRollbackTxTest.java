package org.jboss.cache.transaction.pessimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.transaction.AsyncRollbackTransactionManager;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.Status;

/**
 * Test behaviour of async rollback timeouted transaction
 *
 * @author <a href="mailto:jhalat@infovide.pl">Jacek Halat</a>
 * @since 1.4.0
 */
@Test(groups = {"functional"}, testName = "transaction.pessimistic.AsyncRollbackTxTest")
public class AsyncRollbackTxTest
{
   private CacheSPI<String, String> cache;
   private AsyncRollbackTransactionManager tm;
   private Fqn fqn = Fqn.fromString("/test");
   // This sleep time (millis) should be longer than the transaction timeout time.
   private long sleepTime = 2500;
   // seconds
   private int txTimeout = 2;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration c = new Configuration();
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.AsyncRollbackTransactionManagerLookup");
      c.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c.setSerializationExecutorPoolSize(0);
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(c, getClass());
      tm = (AsyncRollbackTransactionManager) cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      tm.setTransactionTimeout(txTimeout);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      try
      {
         if (tm != null && tm.getTransaction() != null)
         {
            try
            {
               tm.rollback();
            }
            catch (SystemException e)
            {
               // do nothing
            }
         }
      }
      catch (SystemException e)
      {
         // do nothing
      }
      TestingUtil.killCaches(cache);
      cache = null;
      tm = null;
   }

   public void testCommitCreationInSameTx() throws Exception
   {
      assertEquals(0, cache.getNumberOfLocksHeld());
      tm.begin();
      cache.put(fqn, "k", "v");
      assertEquals(2, cache.getNumberOfLocksHeld());
      Thread.sleep(sleepTime);
      tm.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testRollbackCreationInSameTx() throws Exception
   {
      assertEquals(0, cache.getNumberOfLocksHeld());
      tm.begin();
      cache.put(fqn, "k", "v");
      assertEquals(2, cache.getNumberOfLocksHeld());
      Thread.sleep(sleepTime);
      tm.rollback();
      assertEquals(0, cache.getNumberOfLocksHeld());
      // make sure the node was NOT added!!
      assertFalse(cache.exists(fqn));
      // even in a "deleted" form
      assertNull(cache.peek(fqn, true));
   }

   private void doTest(boolean commit, boolean writeLock) throws Throwable
   {
      assertEquals(0, cache.getNumberOfLocksHeld());
      cache.put(fqn, "k", "v");//Executed in Not transactional context
      assertEquals(0, cache.getNumberOfLocksHeld());
      SeparateThread t = new SeparateThread(commit, writeLock);
      t.start();
      t.join();
      if (t.getException() != null)
      {
         throw t.getException();
      }
      assertEquals(0, cache.getNumberOfLocksHeld());
      assertEquals("v", cache.get(fqn, "k"));
   }

   public void testRollbackCreationInDifferentTxReadLock() throws Throwable
   {
      doTest(false, false);
   }

   public void testCommitCreationInDifferentTxReadLock() throws Throwable
   {
      doTest(true, false);
   }

   public void testRollbackCreationInDifferentTxWriteLock() throws Throwable
   {
      doTest(false, true);
   }

   public void testCommitCreationInDifferentTxWriteLock() throws Throwable
   {
      doTest(true, true);
   }

   public void testTxTimeoutAndPutAfter() throws Exception
   {
      assertEquals(0, cache.getNumberOfLocksHeld());
      tm.begin();
      cache.put(fqn, "k", "v");
      assertEquals(2, cache.getNumberOfLocksHeld());
      assertNotNull(tm.getTransaction());
      Thread.sleep(sleepTime);
      tm.rollback();
      assertNull(tm.getTransaction());
      assertEquals(0, cache.getNumberOfLocksHeld());

      // make sure the node was NOT added!!
      assertFalse(cache.exists(fqn));
      // even in a "deleted" form
      assertNull(cache.peek(fqn, true));

      //Put not some data into cache. Because no transaction is used
      //no locks should be helds after this line.
      cache.put(fqn, "k", "v");
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testTxTimeoutAndPutGetAfter() throws Throwable
   {
      assertEquals(0, cache.getNumberOfLocksHeld());
      tm.begin();
      cache.put(fqn, "k", "v");
      assertEquals(2, cache.getNumberOfLocksHeld());
      assertNotNull(tm.getTransaction());
      Thread.sleep(sleepTime);
      tm.rollback();
      // make sure the node was NOT added!!
      assertFalse(cache.exists(fqn));
      // even in a "deleted" form
      assertNull(cache.peek(fqn, true));
      assertNull(tm.getTransaction());
      assertEquals(0, cache.getNumberOfLocksHeld());

      //Put not some data into cache. Because no transaction is used
      //no locks should be helds after this line.
      cache.put(fqn, "k", "v");
      cache.get(fqn, "k");

      // Make sure no write lock is retained by the main thread.  Test that another thread can read.
      SeparateThread t = new SeparateThread(false, false);
      t.start();
      t.join();
      if (t.getException() != null)
      {
         throw t.getException();
      }
   }

   private class SeparateThread extends Thread
   {
      Throwable e = null;
      boolean commit, writeLock;

      public SeparateThread(boolean commit, boolean writeLock)
      {
         this.commit = commit;
         this.writeLock = writeLock;
      }

      public Throwable getException()
      {
         return e;
      }

      public void run()
      {
         try
         {
            tm.begin();
            if (writeLock)
            {
               cache.put(fqn, "k", "v2");// obtain write lock on node
            }
            else
            {
               cache.get(fqn, "k");// obtain read lock on node
            }

            for (int i =0; i < 100; i++) //max 50 secs
            {
               if (tm.getTransaction().getStatus() == Status.STATUS_ROLLEDBACK)
               {
                  break;  
               }
               Thread.sleep(500);
            }

            if (commit)
            {
               tm.commit();
            }
            else
            {
               tm.rollback();
            }

            assertEquals(0, cache.getNumberOfLocksHeld());

         }
         catch (Throwable e)
         {
            this.e = e;
         }
      }
   }

}
