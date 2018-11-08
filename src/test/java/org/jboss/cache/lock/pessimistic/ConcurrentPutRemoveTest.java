package org.jboss.cache.lock.pessimistic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.List;

// This is disabled because the fix is not absolute and will require pretty bug architectural changes.
// There is an edge case where a NodeNotFoundException may occur, and this is due to parent nodes not being
// write locked when children are added/removed.
//
// The problem is in the way READ_COMMITTED is implemented, i.e., writers are not blocked by readers and this
// allows a reader to hold a lock when a writer comes in and deletes the node in question.

@Test(groups = "functional", enabled = false, testName = "lock.pessimistic.ConcurrentPutRemoveTest")
// Known issue - See JBCACHE-1164 and JBCACHE-1165
public class ConcurrentPutRemoveTest
{
   private TransactionManager tm;

   static int count = 0;
   private Cache cache;

   private final Log log = LogFactory.getLog(ConcurrentPutRemoveTest.class);
   private List<SeparateThread> threads;


   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setIsolationLevel(IsolationLevel.READ_COMMITTED);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setLockAcquisitionTimeout(10000);
      cache.start();
      tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      threads = new ArrayList<SeparateThread>();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
      for (SeparateThread st : threads)
      {
         st.interrupt();
         st.join();
      }
   }

   @Test(invocationCount = 500, enabled = false)
   // TODO: 3.0.0: This is still a known failure.  MVCC in 3.0.0 will fix this; enable it in 3.0.0.
   public void testLock() throws Exception
   {
      for (int x = 0; x < 2; x++)
      {
         SeparateThread t = new SeparateThread(x);
         threads.add(t);
         t.start();
      }
      for (SeparateThread separateThread : threads)
      {
         separateThread.join();
         if (separateThread.getException() != null)
         {
            throw separateThread.getException();
         }
      }

   }

   private class SeparateThread extends Thread
   {
      Exception e = null;

      private int num = 0;

      public SeparateThread(int num)
      {
         this.num = num;
      }

      public Exception getException()
      {
         return e;
      }

      public void run()
      {
         Thread.currentThread().setName("Thread:" + num);
         try
         {
            for (int x = 0; x < 1000; x++)
            {
               tm.begin();
               log.warn("Before Remove (" + x + ")");
               //inside transaction
               cache.removeNode(Fqn.fromString("/a"));
               log.warn("After Remove (" + x + ")");
               tm.commit();
               //outside transaction
               log.warn("Before Put (" + x + ")");
               cache.put(Fqn.fromString("/a/b/c/d"), "text" + x, "b");
               log.warn("After Put (" + x + ")");
            }
         }
         catch (Exception e)
         {
            log.error("*** error on a thread", e);
//            System.exit(1);
            this.e = e;
         }
      }
   }

}
