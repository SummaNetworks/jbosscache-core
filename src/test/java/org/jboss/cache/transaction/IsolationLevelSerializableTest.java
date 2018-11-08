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

/**
 * Tests READ_COMMITED isolation level.
 *
 * @author <a href="mailto:ovidiu@jboss.org">Ovidiu Feodorov</a>
 * @version $Id: IsolationLevelSerializableTest.java 7451 2009-01-12 11:38:59Z mircea.markus $
 */
@Test(groups = {"functional", "transaction"}, sequential = true, testName = "transaction.IsolationLevelSerializableTest")
public class IsolationLevelSerializableTest
{

   private Cache<String, String> cache = null;
   private final Fqn FQN = Fqn.fromString("/a");
   private final String KEY = "key";
   private final String VALUE = "value";

   private volatile boolean writerFailed;
   private volatile AssertionError writerError;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      writerFailed = false;

      writerError = null;

      Configuration config = new Configuration();
      config.setCacheMode(CacheMode.LOCAL);
      config.setIsolationLevel(IsolationLevel.SERIALIZABLE);
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
               TransactionManager tx = startTransaction();

               // change VALUE in a transaction
               cache.removeNode(FQN);

               // notify the reading thread
               readerCanRead.countDown();

               readerDone.await();

               tx.commit();
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
      catch (TimeoutException good)
      {
         // ignore; means worked as it should
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
