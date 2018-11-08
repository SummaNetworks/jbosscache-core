package org.jboss.cache.api.mvcc.repeatable_read;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.api.mvcc.LockAssert;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.jboss.cache.UnitTestCacheFactory;

@Test(groups = {"functional", "mvcc"}, sequential = true, testName = "api.mvcc.repeatable_read.WriteSkewTest")
public class WriteSkewTest
{
   protected Cache<String, String> cache;
   protected TransactionManager tm;
   protected Fqn A = Fqn.fromString("/a");
   protected Fqn AB = Fqn.fromString("/a/b");
   protected Fqn ABC = Fqn.fromString("/a/b/c");
   protected Fqn ABCD = Fqn.fromString("/a/b/c/d");
   protected LockManager lockManager;
   protected InvocationContextContainer icc;
   protected boolean repeatableRead = true;

   @BeforeMethod
   public void setUp()
   {
      cache = new UnitTestCacheFactory<String, String>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.LOCAL), false, getClass());
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.MVCC);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setIsolationLevel(repeatableRead ? IsolationLevel.REPEATABLE_READ : IsolationLevel.READ_COMMITTED);
      // reduce lock acquisition timeout so this doesn't take forever to run
      cache.getConfiguration().setLockAcquisitionTimeout(200); // 200 ms
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
      icc = null;
      lockManager = null;
      tm = null;
   }

   private void postStart()
   {
      lockManager = TestingUtil.extractComponentRegistry(cache).getComponent(LockManager.class);
      icc = TestingUtil.extractComponentRegistry(cache).getComponent(InvocationContextContainer.class);
      tm = TestingUtil.extractComponentRegistry(cache).getComponent(TransactionManager.class);
   }

   protected void assertNoLocks()
   {
      LockAssert.assertNoLocks(lockManager, icc);
   }

   public void testDontCheckWriteSkew() throws Exception
   {
      cache.getConfiguration().setWriteSkewCheck(false);
      cache.start();
      postStart();
      doTest(true);
   }

   public void testCheckWriteSkew() throws Exception
   {
      cache.getConfiguration().setWriteSkewCheck(true);
      cache.start();
      postStart();
      doTest(false);
   }

   private void doTest(final boolean allowWriteSkew) throws Exception
   {
      if (repeatableRead)
      {
         cache.put(AB, "k", "v");
         final Set<Exception> w1exceptions = new HashSet<Exception>();
         final Set<Exception> w2exceptions = new HashSet<Exception>();
         final CountDownLatch w1Signal = new CountDownLatch(1);
         final CountDownLatch w2Signal = new CountDownLatch(1);
         final CountDownLatch threadSignal = new CountDownLatch(2);

         Thread w1 = new Thread("Writer-1")
         {
            public void run()
            {
               boolean didCoundDown = false;
               try
               {
                  tm.begin();
                  assert "v".equals(cache.get(AB, "k"));
                  threadSignal.countDown();
                  didCoundDown = true;
                  w1Signal.await();
                  cache.put(AB, "k", "v2");
                  tm.commit();
               }
               catch (Exception e)
               {
                  w1exceptions.add(e);
               }
               finally
               {
                  if (!didCoundDown) threadSignal.countDown();
               }
            }
         };

         Thread w2 = new Thread("Writer-2")
         {
            public void run()
            {
               boolean didCoundDown = false;
               try
               {
                  tm.begin();
                  assert "v".equals(cache.get(AB, "k"));
                  threadSignal.countDown();
                  didCoundDown = true;
                  w2Signal.await();
                  cache.put(AB, "k", "v3");
                  tm.commit();
               }
               catch (Exception e)
               {
                  w2exceptions.add(e);
                  // the exception will be thrown when doing a cache.put().  We should make sure we roll back the tx to release locks.
                  if (!allowWriteSkew)
                  {
                     try
                     {
                        tm.rollback();
                     }
                     catch (SystemException e1)
                     {
                        // do nothing.
                     }
                  }
               }
               finally
               {
                  if (!didCoundDown) threadSignal.countDown();
               }
            }
         };

         w1.start();
         w2.start();

         threadSignal.await();
         // now.  both txs have read.
         // let tx1 start writing
         w1Signal.countDown();
         w1.join();

         w2Signal.countDown();
         w2.join();

         if (allowWriteSkew)
         {
            // should have no exceptions!!
            throwExceptions(w1exceptions, w2exceptions);
            assert w2exceptions.size() == 0;
            assert w1exceptions.size() == 0;
            assert "v3".equals(cache.get(AB, "k")) : "W2 should have overwritten W1's work!";
         }
         else
         {
            // there should be a single exception from w2.
            assert w2exceptions.size() == 1;
            throwExceptions(w1exceptions);
            assert w1exceptions.size() == 0;
            assert "v2".equals(cache.get(AB, "k")) : "W2 should NOT have overwritten W1's work!";
         }

         assertNoLocks();
      }
   }

   private void throwExceptions(Collection<Exception>... exceptions) throws Exception
   {
      for (Collection<Exception> ce : exceptions)
      {
         for (Exception e : ce) throw e;
      }
   }
}
