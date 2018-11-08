package org.jboss.cache.transaction;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.lock.TimeoutException;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests READ_COMMITED isolation level.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version $Id: IsolationLevelReadCommittedTest.java 7451 2009-01-12 11:38:59Z mircea.markus $
 */

@Test(groups = {"functional", "transaction"}, sequential = true, testName = "transaction.IsolationLevelReadCommittedTest")
public class IsolationLevelReadCommittedTest
{
   private Cache<String, String> cache = null;
   private final Fqn FQN = Fqn.fromString("/a/b/c");
   private final Fqn PARENT_FQN = FQN.getParent();
   private final String KEY = "key";
   private final String VALUE = "value";

   private volatile boolean writerFailed;
   private volatile boolean readerFailed;
   private volatile AssertionError writerError;
   private volatile AssertionError readerError;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      writerFailed = false;
      readerFailed = false;

      writerError = null;
      readerError = null;

      Configuration config = new Configuration();
      config.setCacheMode(CacheMode.LOCAL);
      config.setIsolationLevel(IsolationLevel.READ_COMMITTED);
      config.setLockAcquisitionTimeout(1000);
      config.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = instance.createCache(config, getClass());
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {

      TestingUtil.killCaches(cache);
      cache = null;
   }

   /**
    * The test starts a reader and writer thread and coordinates access to the cache so the reader
    * reads after the writer changes a node in a transaction, but before roll back. The reader
    * should never read anything else than the original value (uncommitted values should be
    * isolated)
    */
   public void testReadCommitted() throws Exception
   {
      final CountDownLatch readerCanRead = new CountDownLatch(1);
      final CountDownLatch readerDone = new CountDownLatch(1);
      final CountDownLatch writerCanWrite = new CountDownLatch(1);
      final CountDownLatch writerCanRollback = new CountDownLatch(1);
      final CountDownLatch writerDone = new CountDownLatch(1);

      cache.put(FQN, KEY, VALUE);
      assertEquals(VALUE, cache.get(FQN, KEY));

      // start a reader thread; need a transaction so its initial
      // read lock is maintained while the writer does its work

      Thread readerThread = new Thread(new Runnable()
      {
         public void run()
         {
            TransactionManager tm = null;
            try
            {
               tm = startTransaction();

               // Read the value, thus acquiring a read lock on FQN
               assertEquals("Could not read node with expected value!", VALUE, cache.get(FQN, KEY));

               writerCanWrite.countDown();

               // wait until the writer thread changes the value in a transaction, but it did not
               // yet commit or roll back.
               readerCanRead.await();

               try
               {
                  // I shouldn't be able to see the "dirty" value
                  assertEquals("thread w/ read lock can see subsequent uncommitted changes!!", VALUE, cache.get(FQN, KEY));
               }
               catch (TimeoutException good)
               {
                  // this is what should happen due to writer's WL
               }

               // let the writer know it can rollback
               writerCanRollback.countDown();

               // I should still be able to see the "clean" value
               assertEquals("Could not read node with expected value!", VALUE, cache.get(FQN, KEY));
            }
            catch (AssertionError e)
            {
               readerError = e;
            }
            catch (Throwable t)
            {
               t.printStackTrace();
               readerFailed = true;
            }
            finally
            {
               if (tm != null)
               {
                  try
                  {
                     tm.commit();
                  }
                  catch (Exception e)
                  {
                  }
               }
               writerCanWrite.countDown();
               writerCanRollback.countDown();
               readerDone.countDown();
            }
         }
      }, "READER");
      readerThread.start();

      // start a writer thread and a transaction

      Thread writerThread = new Thread(new Runnable()
      {
         public void run()
         {
            try
            {
               // wait until the reader thread reads and allows me to start

               writerCanWrite.await(3, TimeUnit.SECONDS);

               TransactionManager tm = startTransaction();

               // change VALUE in a transaction
               cache.put(FQN, KEY, "this-shouldnt-be-visible");

               // notify the reading thread
               readerCanRead.countDown();

               // wait until the reader thread reads and allows me to rollback or until I timeout
               writerCanWrite.await(3, TimeUnit.SECONDS);

               tm.rollback();
            }
            catch (AssertionError e)
            {
               writerError = e;
            }
            catch (Throwable t)
            {
               t.printStackTrace();
               writerFailed = true;
            }
            finally
            {
               readerCanRead.countDown();
               writerDone.countDown();
            }
         }
      }, "WRITER");
      writerThread.start();

      // wait for both threads to finish
      readerDone.await();
      writerDone.await();

      // If any assertion failed, throw on the AssertionFailedError
      if (readerError != null)
      {
         throw readerError;
      }

      if (writerError != null)
      {
         throw writerError;
      }

      if (readerFailed)
      {
         fail("The reader thread exited incorrectly. Watch the log for previous stack traces");
      }

      if (writerFailed)
      {
         fail("The writer thread exited incorrectly. Watch the log for previous stack traces");
      }
   }

   /**
    * Test creates a cache node then starts a separate thread that removes
    * the node inside a tx. Test confirms that the removal cannot be seen
    * before the test commits.
    *
    * @throws Exception
    */
   public void testNodeRemoved() throws Exception
   {
      final CountDownLatch readerCanRead = new CountDownLatch(1);
      final CountDownLatch readerDone = new CountDownLatch(1);
      final CountDownLatch writerDone = new CountDownLatch(1);

      cache.put(FQN, KEY, VALUE);
      assertEquals(VALUE, cache.get(FQN, KEY));

      // start a writer thread and a transaction

      Thread writerThread = new Thread(new Runnable()
      {
         public void run()
         {
            try
            {
               TransactionManager mgr = startTransaction();

               // change VALUE in a transaction
               cache.removeNode(PARENT_FQN);

               // notify the reading thread
               readerCanRead.countDown();

               readerDone.await();

               mgr.commit();
            }
            catch (AssertionError e)
            {
               writerError = e;
            }
            catch (Throwable t)
            {
               t.printStackTrace();
               writerFailed = true;
            }
            finally
            {
               readerCanRead.countDown();
               writerDone.countDown();
            }
         }
      }, "WRITER");
      writerThread.start();

      try
      {
         // wait until the writer thread changes the value in a transaction,
         // but it did not yet commit or roll back.
         readerCanRead.await();

         // I shouldn't be able to see the "dirty" value
         assertEquals("2nd thread cannot see uncommitted changes", VALUE, cache.get(FQN, KEY));
      }
      catch (TimeoutException t)
      {
         // ignore, this is good
      }
      finally
      {
         readerDone.countDown();
      }

      // wait for the writer to finish
      writerDone.await();

      assertNull("Node was removed", cache.getNode(FQN));

      // If any assertion failed, throw on the AssertionFailedError

      if (writerError != null)
      {
         throw writerError;
      }

      if (writerFailed)
      {
         fail("The writer thread exited incorrectly. Watch the log for previous stack traces");
      }

   }

   private TransactionManager startTransaction() throws SystemException, NotSupportedException
   {
      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();
      return mgr;
   }

}
