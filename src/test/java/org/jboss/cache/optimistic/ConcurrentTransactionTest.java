/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.commands.VisitableCommand;
import org.jboss.cache.commands.tx.OptimisticPrepareCommand;
import org.jboss.cache.interceptors.OptimisticCreateIfNotExistsInterceptor;
import org.jboss.cache.interceptors.TxInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.ConcurrentTransactionTest")
public class ConcurrentTransactionTest extends AbstractOptimisticTestCase
{
   private CacheSPI<Object, Object> cache;
   private Fqn f = Fqn.fromString("/a/b");
   private List<Exception> exceptions = new CopyOnWriteArrayList<Exception>();

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      try
      {
         cache = createCacheUnstarted();
         cache.getConfiguration().setUseRegionBasedMarshalling(true);
         cache.start();
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testConcurrentTransactions() throws Exception
   {
      TransactionManager tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      Fqn abcd = Fqn.fromString("/a/b/c/d");
      Fqn abce = Fqn.fromString("/a/b/c/e");
      Fqn abcf = Fqn.fromString("/a/b/c/f");
      Fqn abcg = Fqn.fromString("/a/b/c/g");
      Fqn abxy = Fqn.fromString("/a/b/x/y");
      cache.put(abcd, key, value);

      assertEquals(value, cache.get(abcd, key));

      tm.begin();
      Transaction tx = tm.getTransaction();

      cache.put(abxy, key, value);
      tm.suspend();

      // a number of random puts in unrelated sub nodes.
      cache.put(abcd, key, value + value);
      cache.put(abce, key, value);
      cache.put(abcf, key, value);
      cache.put(abcg, key, value);

      assertEquals(value + value, cache.get(abcd, key));
      assertEquals(value, cache.get(abce, key));
      assertEquals(value, cache.get(abcf, key));
      assertEquals(value, cache.get(abcg, key));

      tm.resume(tx);
      tm.commit();

      assertEquals(value, cache.get(abxy, key));

      NodeSPI<Object, Object> n = cache.getRoot();
   }

   public void testConcurrentCreationTestWithEmptyCache() throws Exception
   {
      doConcurrentCreationTest(false);
   }

   public void testConcurrentCreationTestWithEmptyCacheActivated() throws Exception
   {
      cache.put(Fqn.fromString("/parent"), null);
      cache.getRegion(Fqn.fromString("/parent"), true).activate();
      assertNotNull(cache.peek(Fqn.fromString("/parent"), false));
      doConcurrentCreationTest(false);
   }

   public void testConcurrentCreationTestWithPopulatedCache() throws Exception
   {
      doConcurrentCreationTest(true);
   }

   public void testConcurrentReadAndRemove() throws Exception
   {
      final List<Exception> exceptions = new LinkedList<Exception>();
      final CountDownLatch readerLatch = new CountDownLatch(1);
      final CountDownLatch readerFinishedLatch = new CountDownLatch(1);
      final Fqn fqn = Fqn.fromString("/parent/child");

      cache.put(fqn, "k", "v");

      class Reader extends Thread
      {
         public void run()
         {
            try
            {
               cache.getTransactionManager().begin();
               cache.get(fqn, "k"); // read
               readerFinishedLatch.countDown();
               readerLatch.await(); // wait
               cache.getTransactionManager().commit();
            }
            catch (Exception e)
            {
               e.printStackTrace();
               exceptions.add(e);

            }
         }
      }

      Thread reader = new Reader();

      reader.start();
      readerFinishedLatch.await();
      cache.removeNode(fqn.getParent());
      assertNull(cache.peek(fqn.getParent(), false));
      readerLatch.countDown();
      reader.join();

      assertTrue("Should not have caught any exceptions!!", exceptions.isEmpty());
   }

   public void testConcurrentPutReadAndRemove() throws Exception
   {
      final List<Exception> exceptions = new LinkedList<Exception>();
      final CountDownLatch readerLatch = new CountDownLatch(1);
      final CountDownLatch readerFinishedLatch = new CountDownLatch(1);
      final Fqn fqn = Fqn.fromString("/parent/child");

      cache.put(fqn, "k", "v");

      class Reader extends Thread
      {
         public void run()
         {
            try
            {
               cache.getTransactionManager().begin();
               cache.put(Fqn.ROOT, "x", "y"); // a dummy put to ensure that validation occurs
               cache.get(fqn, "k"); // read
               readerFinishedLatch.countDown();
               readerLatch.await(); // wait
               cache.getTransactionManager().commit();
            }
            catch (Exception e)
            {
               e.printStackTrace();
               exceptions.add(e);

            }
         }
      }

      Thread reader = new Reader();

      reader.start();
      readerFinishedLatch.await();
      cache.removeNode(fqn.getParent());
      assertNull(cache.peek(fqn.getParent(), false));
      readerLatch.countDown();
      reader.join();

      assertTrue("Should not have caught any exceptions!!", exceptions.isEmpty());
   }

   private void doConcurrentCreationTest(boolean prepopulateParent) throws Exception
   {
      if (prepopulateParent)
         cache.put(Fqn.fromString("/parent/dummy"), "k", "v");

      final List<Exception> exceptions = new LinkedList<Exception>();
      final CountDownLatch latch = new CountDownLatch(1);

      class ConcurrentCreator extends Thread
      {
         private String name;

         public ConcurrentCreator(String name)
         {
            this.name = name;
         }

         public void run()
         {
            try
            {
               cache.getTransactionManager().begin();
               cache.put(Fqn.fromString("/parent/child" + name), "key", "value");
               latch.await();
               cache.getTransactionManager().commit();
            }
            catch (Exception e)
            {
               e.printStackTrace();
               exceptions.add(e);
            }
         }
      }

      Thread one = new ConcurrentCreator("one");
      Thread two = new ConcurrentCreator("two");

      one.start();
      two.start();

      latch.countDown();

      one.join();
      two.join();

      assertTrue("Should not have caught any exceptions!!", exceptions.isEmpty());
   }

   public void testConcurrentPut() throws Exception
   {
      final String slowThreadName = "SLOW";
      final String fastThreadName = "FAST";
      CommandInterceptor slowdownInterceptor = new CommandInterceptor()
      {
         public Object handleDefault(InvocationContext ctx, VisitableCommand command) throws Throwable
         {
            if (Thread.currentThread().getName().equals(slowThreadName))
            {
               Thread.sleep(1000);
            }
            return super.handleDefault(ctx, command);
         }

      };

      TestingUtil.injectInterceptor(cache, slowdownInterceptor, OptimisticCreateIfNotExistsInterceptor.class);

      // now create 2 threads to do concurrent puts.
      Putter slow = new Putter(slowThreadName);
      Putter fast = new Putter(fastThreadName);

      // start the slow putter first
      slow.start();
      TestingUtil.sleepThread(200);
      fast.start();

      fast.join();
      slow.join();

      for (Exception e : exceptions)
         e.printStackTrace();
      assertEquals(0, exceptions.size());
   }

   public void testConcurrentRemove() throws Exception
   {
      final String slowThreadName = "SLOW";
      final String fastThreadName = "FAST";
      CommandInterceptor slowdownInterceptor = new CommandInterceptor()
      {
         @Override
         public Object handleDefault(InvocationContext ctx, VisitableCommand command) throws Throwable
         {
            if (Thread.currentThread().getName().equals(slowThreadName) && ctx.getMethodCall().getMethodId() == OptimisticPrepareCommand.METHOD_ID)
            {
               Thread.sleep(1000);
            }
            return invokeNextInterceptor(ctx, command);
         }
      };

      TestingUtil.injectInterceptor(cache, slowdownInterceptor, TxInterceptor.class);

      // now create 2 threads to do concurrent puts.
      Remover slow = new Remover(slowThreadName);
      Remover fast = new Remover(fastThreadName);

      cache.put(f, "hello", "world");

      // start the slow putter first
      slow.start();
      TestingUtil.sleepThread(200);
      fast.start();

      fast.join();
      slow.join();

      for (Exception e : exceptions)
         e.printStackTrace();
      assertEquals(0, exceptions.size());
   }

   public class Putter extends Thread
   {
      public Putter(String name)
      {
         super(name);
      }

      public void run()
      {
         try
         {
            cache.getTransactionManager().begin();
            cache.put(Fqn.fromRelativeElements(f, getName()), "a", "b");
            cache.getTransactionManager().commit();
         }
         catch (Exception e)
         {
            exceptions.add(e);
         }
      }
   }

   public class Remover extends Thread
   {
      public Remover(String name)
      {
         super(name);
      }

      public void run()
      {
         try
         {
            cache.getTransactionManager().begin();
            cache.removeNode(f);
            cache.getTransactionManager().commit();
         }
         catch (Exception e)
         {
            exceptions.add(e);
         }
      }
   }

}
