/*
 * Created on 17-Feb-2005
 *
 *
 *
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.interceptors.OptimisticCreateIfNotExistsInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.loader.SamplePojo;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * @author xenephon
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.ThreadedOptimisticCreateIfNotExistsInterceptorTest")
public class ThreadedOptimisticCreateIfNotExistsInterceptorTest extends AbstractOptimisticTestCase
{
   protected synchronized void setTransactionsInInvocationCtx(TransactionManager mgr, CacheSPI cache) throws Exception
   {
      cache.getInvocationContext().setTransaction(mgr.getTransaction());
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction());
   }

   protected void resetInvocationCtx(CacheSPI cache)
   {
      cache.getInvocationContext().setTransaction(null);
      cache.getInvocationContext().setGlobalTransaction(null);
   }

   public void testDifferentTransactions() throws Exception
   {

      int numThreads = 100;
      final int minSleep = 0;
      final int maxSleep = 1000;
      TestListener listener = new TestListener();
      final CacheSPI<Object, Object> cache = createCacheWithListener(listener);

      CommandInterceptor interceptor = new OptimisticCreateIfNotExistsInterceptor();
      CommandInterceptor dummy = new MockInterceptor();
      interceptor.setNext(dummy);

      TestingUtil.replaceInterceptorChain(cache, interceptor);

      // should just be the root node
      assertEquals(0, cache.getNumberOfNodes());

      Runnable run = new Runnable()
      {

         public void run()
         {
            try
            {
               //start a new transaction in this thread
               DummyTransactionManager mgr = DummyTransactionManager.getInstance();
               mgr.begin();
               setTransactionsInInvocationCtx(mgr, cache);
               SamplePojo pojo = new SamplePojo(21, "test");

               cache.put("/one", "key1", pojo);

               randomSleep(minSleep, maxSleep);

               cache.put("/one/two", "key2", pojo);

               OptimisticTransactionContext entry = (OptimisticTransactionContext) cache.getTransactionTable().get(cache.getCurrentTransaction());
               assertEquals(3, entry.getTransactionWorkSpace().getNodes().size());
               assertTrue(entry.getTransactionWorkSpace().getNode(Fqn.fromString("/")) != null);
               assertTrue(entry.getTransactionWorkSpace().getNode(Fqn.fromString("/one")) != null);
               assertTrue(entry.getTransactionWorkSpace().getNode(Fqn.fromString("/one/two")) != null);
               mgr.commit();
               resetInvocationCtx(cache);
            }
            catch (Exception e)
            {
               e.printStackTrace();
            }
         }
      };
      Thread[] threads = new Thread[numThreads];
      for (int i = 0; i < numThreads; i++)
      {
         Thread t = new Thread(run);
         t.start();
         threads[i] = t;
      }
      for (int i = 0; i < numThreads; i++)
      {
         threads[i].join();
      }
      cache.stop();
   }

   public void testDifferentThreadsSameTransaction() throws Exception
   {
      int numThreads = 100;
      final int minSleep = 0;
      final int maxSleep = 500;
      TestListener listener = new TestListener();
      final CacheSPI<Object, Object> cache = createCacheWithListener(listener);

      CommandInterceptor interceptor = new OptimisticCreateIfNotExistsInterceptor();
      CommandInterceptor dummy = new MockInterceptor();
      interceptor.setNext(dummy);

      TestingUtil.replaceInterceptorChain(cache, interceptor);

      final DummyTransactionManager mgr = DummyTransactionManager.getInstance();
      mgr.begin();
      final Transaction tx = mgr.getTransaction();

      Runnable run = new Runnable()
      {

         public void run()
         {
            try
            {
               //start a new transaction in this thread

               mgr.setTransaction(tx);
               SamplePojo pojo = new SamplePojo(21, "test");

               setTransactionsInInvocationCtx(mgr, cache);
               cache.put("/one", "key1", pojo);
               OptimisticTransactionContext entry = (OptimisticTransactionContext) cache.getTransactionTable().get(cache.getCurrentTransaction());

               randomSleep(minSleep, maxSleep);

               cache.put("/one/two", "key2", pojo);
               assertEquals(3, entry.getTransactionWorkSpace().getNodes().size());
               assertTrue(entry.getTransactionWorkSpace().getNode(Fqn.fromString("/")) != null);
               assertTrue(entry.getTransactionWorkSpace().getNode(Fqn.fromString("/one")) != null);
               assertTrue(entry.getTransactionWorkSpace().getNode(Fqn.fromString("/one/two")) != null);
            }
            catch (Exception e)
            {
               e.printStackTrace();
            }
            finally
            {
               resetInvocationCtx(cache);
            }
         }
      };
      Thread[] threads = new Thread[numThreads];
      for (int i = 0; i < numThreads; i++)
      {
         Thread t = new Thread(run);
         t.start();
         threads[i] = t;
      }
      for (int i = 0; i < numThreads; i++)
      {
         threads[i].join();
      }
      mgr.commit();

      TestingUtil.sleepThread((long) 4000);
      cache.stop();
   }
}
