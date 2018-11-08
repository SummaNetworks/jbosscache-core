/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.transaction;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
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
 * @version $Id: IsolationLevelReadCommittedNodeCreationRollbackTest.java 7451 2009-01-12 11:38:59Z mircea.markus $
 */
@Test(groups = {"functional", "transaction"}, sequential = true, testName = "transaction.IsolationLevelReadCommittedNodeCreationRollbackTest")
public class IsolationLevelReadCommittedNodeCreationRollbackTest
{

   private CacheSPI<String, String> cache = null;
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

      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.LOCAL);
      c.setIsolationLevel(IsolationLevel.READ_COMMITTED);
      c.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      cache = (CacheSPI<String, String>) instance.createCache(c, false, getClass());
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {

      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testNodeCreationRollback() throws Exception
   {
      final CountDownLatch secondCanWrite = new CountDownLatch(1);
      final CountDownLatch secondCanRead = new CountDownLatch(1);
      final CountDownLatch secondDone = new CountDownLatch(1);
      final CountDownLatch firstCanRollback = new CountDownLatch(1);
      final CountDownLatch firstDone = new CountDownLatch(1);

      final Fqn PARENT = Fqn.fromString("/a");

      // start a first thread and a transaction

      Thread firstThread = new Thread(new Runnable()
      {
         public void run()
         {
            try
            {
               TransactionManager tm = startTransaction();

               // Create an empty parent node and a node with data
               Fqn a1 = Fqn.fromRelativeElements(PARENT, "1");
               cache.put(a1, KEY, VALUE);

               // notify the second thread it can write
               secondCanWrite.countDown();

               // wait until the second thread writes and allows me to rollback or until I timeout
               firstCanRollback.await(3000, TimeUnit.MILLISECONDS);

               tm.rollback();

               assertNull("a1 empty", cache.get(a1, KEY));

               // notify the reading thread
               secondCanRead.countDown();
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
               secondCanWrite.countDown();
               secondCanRead.countDown();
               firstDone.countDown();
            }
         }
      }, "FIRST");
      firstThread.start();

      // start a second thread; no transaction is necessary here

      Thread secondThread = new Thread(new Runnable()
      {
         public void run()
         {
            try
            {
               // wait until the first thread has created PARENT and a child
               secondCanWrite.await();

               // create a second child under parent
               Fqn a2 = Fqn.fromRelativeElements(PARENT, "2");
               try
               {
                  cache.put(a2, KEY, VALUE);
               }
               catch (TimeoutException good)
               {
                  // first thread locked us out of parent
                  return;
               }

               // let the first thread know it can rollback
               firstCanRollback.countDown();

               // wait until the first thread rolls back.
               secondCanRead.await();

               // I should still see the value I put
               assertEquals("write lock not acquired on " + "creation of an empty node", VALUE, cache.get(a2, KEY));
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
               firstCanRollback.countDown();
               secondDone.countDown();
            }
         }
      }, "SECOND");
      secondThread.start();

      // wait for both threads to finish
      secondDone.await();
      firstDone.await();

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
         fail("The second thread exited incorrectly. Watch the log for previous stack traces");
      }

      if (writerFailed)
      {
         fail("The first thread exited incorrectly. Watch the log for previous stack traces");
      }
   }

   private TransactionManager startTransaction() throws SystemException, NotSupportedException
   {
      TransactionManager mgr = cache.getTransactionManager();
      mgr.begin();
      return mgr;
   }

}
