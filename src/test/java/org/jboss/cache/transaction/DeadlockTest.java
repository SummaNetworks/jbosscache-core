/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.transaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.lock.TimeoutException;
import org.jboss.cache.lock.UpgradeException;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

/**
 * Tests transactional access to a local Cache, with concurrent (deadlock-prone) access.
 *
 * @version $Id: DeadlockTest.java 7451 2009-01-12 11:38:59Z mircea.markus $
 */
@Test(groups = {"functional", "transaction"}, sequential = true, enabled = false, testName = "transaction.DeadlockTest")
public class DeadlockTest
{
   CacheSPI<String, String> cache = null;
   Exception thread_ex;

   final Fqn NODE = Fqn.fromString("/a/b/c");
   final Fqn PARENT_NODE = Fqn.fromString("/a/b");
   final Fqn FQN1 = NODE;
   final Fqn FQN2 = Fqn.fromString("/1/2/3");
   final Log log = LogFactory.getLog(DeadlockTest.class);

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      Configuration c = new Configuration();
      c.setStateRetrievalTimeout(10000);
      c.setClusterName("test");
      c.setCacheMode(Configuration.CacheMode.LOCAL);
      c.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      c.setLockParentForChildInsertRemove(true);
      c.setLockAcquisitionTimeout(3000);

      cache = (CacheSPI<String, String>) instance.createCache(c, false, getClass());
      cache.create();
      cache.start();
      thread_ex = null;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
      if (thread_ex != null)
      {
         throw thread_ex;
      }
   }

   public void testConcurrentUpgrade() throws CacheException, InterruptedException
   {
      MyThread t1 = new MyThreadTimeout("MyThread#1", NODE);
      MyThread t2 = new MyThread("MyThread#2", NODE);

      cache.put(NODE, null);

      t1.start();
      t2.start();

      TestingUtil.sleepThread((long) 5000);

      synchronized (t1)
      {
         t1.notify();// t1 will now try to upgrade RL to WL, but fails b/c t2 still has a RL
      }

      TestingUtil.sleepThread((long) 5000);

      synchronized (t2)
      {
         t2.notify();// t1 should now be able to upgrade because t1 was rolled back (RL was removed)
      }

      t1.join();
      t2.join();
   }

   /**
    * Typical deadlock: t1 acquires WL on /a/b/c, t2 WL on /1/2/3, then t1 attempts to get WL on /1/2/3 (locked by t2),
    * and t2 tries to acquire WL on /a/b/c. One (or both) of the 2 transactions is going to timeout and roll back.
    */
   public void testPutDeadlock() throws CacheException, InterruptedException
   {
      MyPutter t1 = new MyPutterTimeout("MyPutter#1", FQN1, FQN2);
      MyPutter t2 = new MyPutter("MyPutter#2", FQN2, FQN1);

      cache.put(FQN1, null);
      cache.put(FQN2, null);

      t1.start();
      t2.start();

      TestingUtil.sleepThread((long) 1000);

      synchronized (t1)
      {
         t1.notify();// t1 will now try to acquire WL on /1/2/3 (held by t2) - this will time out
      }

      TestingUtil.sleepThread((long) 1000);

      synchronized (t2)
      {
         t2.notify();// t2 tries to acquire WL on /a/b/c (held by t1)
      }

      t1.join();
      t2.join();
   }

   /*

    Commented out since JBCACHE-875 onwards, this will end up in a classic deadlock since we lock parent when removing a child.

    public void testCreateIfNotExistsLogic() throws CacheException, InterruptedException
    {
    cache.put(NODE, null);

    class T0 extends GenericThread
    {
    public T0(String name)
    {
    super(name);
    }

    protected void _run() throws Exception
    {
    Transaction myTx = startTransaction();
    log("put(" + NODE + ")");
    cache.put(NODE, null);
    log("put(" + NODE + "): OK");

    synchronized (this) {wait();}

    log("remove(" + NODE + ")");
    cache.remove(NODE);
    log("remove(" + NODE + "): OK");

    log("committing TX");
    myTx.commit();
    }
    }

    class T1 extends GenericThread
    {
    public T1(String name)
    {
    super(name);
    }

    protected void _run() throws Exception
    {
    Transaction myTx = startTransaction();
    log("put(" + NODE + ")");
    cache.put(NODE, null);
    log("put(" + NODE + "): OK");

    log("committing TX");
    myTx.commit();
    }

    }

    T0 t0 = new T0("T0");
    t0.start();
    TestingUtil.sleepThread((long) 500);
    T1 t1 = new T1("T1");
    t1.start();
    TestingUtil.sleepThread((long) 500);
    synchronized (t0)
    {
    t0.notify();
    }
    t0.join();
    t1.join();
    }

    */

   public void testMoreThanOneUpgrader() throws Exception
   {
      final int NUM = 2;
      final Object lock = new Object();

      cache.put(NODE, "bla", "blo");

      MyUpgrader[] upgraders = new MyUpgrader[NUM];
      for (int i = 0; i < upgraders.length; i++)
      {
         upgraders[i] = new MyUpgrader("Upgrader#" + i, NODE, lock);
         upgraders[i].start();
      }

      TestingUtil.sleepThread((long) 1000);

      synchronized (lock)
      {
         lock.notifyAll();
      }

      // all threads now try to upgrade the RL to a WL
      for (MyUpgrader upgrader : upgraders)
      {
         upgrader.join();
      }
   }

   public void testPutsAndRemovesOnParentAndChildNodes() throws InterruptedException
   {
      ContinuousPutter putter = new ContinuousPutter("DeadlockTestPutter", NODE);
      ContinuousRemover remover = new ContinuousRemover("DeadlockTestRemover", PARENT_NODE);
      putter.start();
      remover.start();
      TestingUtil.sleepThread((long) 5000);
      putter.looping = false;
      remover.looping = false;
      putter.join();
      remover.join();
   }

   public void testPutsAndRemovesOnParentAndChildNodesReversed() throws InterruptedException
   {
      ContinuousPutter putter = new ContinuousPutter("DeadlockTestPutter", PARENT_NODE);
      ContinuousRemover remover = new ContinuousRemover("DeadlockTestRemover", NODE);
      putter.start();
      remover.start();
      TestingUtil.sleepThread((long) 5000);
      putter.looping = false;
      remover.looping = false;
      putter.join();
      remover.join();
   }

   public void testPutsAndRemovesOnSameNode() throws InterruptedException
   {
      ContinuousPutter putter = new ContinuousPutter("DeadlockTestPutter", NODE);
      ContinuousRemover remover = new ContinuousRemover("DeadlockTestRemover", NODE);
      putter.start();
      remover.start();
      TestingUtil.sleepThread((long) 5000);
      putter.looping = false;
      remover.looping = false;
      putter.join();
      remover.join();
   }

   class GenericThread extends Thread
   {
      protected TransactionManager tm;
      protected boolean looping = true;

      public GenericThread()
      {

      }

      public GenericThread(String name)
      {
         super(name);
      }

      public void setLooping(boolean looping)
      {
         this.looping = looping;
      }

      public void run()
      {
         try
         {
            _run();
         }
         catch (Exception t)
         {
            if (thread_ex == null)
            {
               thread_ex = t;
            }
         }
         if (log.isTraceEnabled())
         {
            log.trace("Thread " + getName() + " terminated");
         }
      }

      protected void _run() throws Exception
      {
         throw new UnsupportedOperationException();
      }
   }

   class ContinuousRemover extends GenericThread
   {
      Fqn fqn;

      public ContinuousRemover(String name, Fqn fqn)
      {
         super(name);
         this.fqn = fqn;
      }

      protected void _run() throws Exception
      {
         while (thread_ex == null && looping)
         {
            try
            {
               if (interrupted())
               {
                  break;
               }
               tm = startTransaction();
               cache.removeNode(fqn);
               sleep(random(20));
               tm.commit();
            }
            catch (InterruptedException interrupted)
            {
               tm.rollback();
               break;
            }
            catch (Exception ex)
            {
               tm.rollback();
               throw ex;
            }
         }
      }
   }

   class ContinuousPutter extends GenericThread
   {
      Fqn fqn;

      public ContinuousPutter(String name, Fqn fqn)
      {
         super(name);
         this.fqn = fqn;
      }

      protected void _run() throws Exception
      {
         while (thread_ex == null && looping)
         {
            try
            {
               if (interrupted())
               {
                  break;
               }
               tm = startTransaction();
               cache.put(fqn, "foo", "bar");
               sleep(random(20));
               tm.commit();
            }
            catch (InterruptedException interrupted)
            {
               tm.rollback();
               break;
            }
            catch (Exception ex)
            {
               tm.rollback();
               throw ex;
            }
         }
      }
   }

   private static long random(long range)
   {
      return (long) ((Math.random() * 100000) % range) + 1;
   }

   class MyThread extends GenericThread
   {
      Fqn fqn;

      public MyThread(String name, Fqn fqn)
      {
         super(name);
         this.fqn = fqn;
      }

      protected void _run() throws Exception
      {
         tm = startTransaction();
         cache.get(fqn, "bla");// acquires RL

         synchronized (this)
         {
            wait();
         }

         cache.put(fqn, "key", "val");// need to upgrade RL to WL
         tm.commit();
      }
   }

   class MyUpgrader extends MyThread
   {
      Object lock;

      public MyUpgrader(String name, Fqn fqn)
      {
         super(name, fqn);
      }

      public MyUpgrader(String name, Fqn fqn, Object lock)
      {
         super(name, fqn);
         this.lock = lock;
      }

      protected void _run() throws Exception
      {
         tm = startTransaction();
         try
         {
            cache.get(fqn, "bla");// acquires RL

            synchronized (lock)
            {
               lock.wait();
            }

            cache.put(fqn, "key", "val");// need to upgrade RL to WL
            tm.commit();
         }
         catch (UpgradeException upge)
         {
            tm.rollback();
         }
      }
   }

   class MyThreadTimeout extends MyThread
   {

      public MyThreadTimeout(String name, Fqn fqn)
      {
         super(name, fqn);
      }

      protected void _run() throws Exception
      {
         try
         {
            super._run();
         }
         catch (UpgradeException upgradeEx)
         {
            tm.rollback();
         }
         catch (TimeoutException timeoutEx)
         {
            tm.rollback();
         }
      }
   }

   class MyPutter extends GenericThread
   {
      Fqn fqn1, fqn2;

      public MyPutter(String name, Fqn fqn1, Fqn fqn2)
      {
         super(name);
         this.fqn1 = fqn1;
         this.fqn2 = fqn2;
      }

      protected void _run() throws Exception
      {
         tm = startTransaction();
         cache.put(fqn1, "key", "val");// need to upgrade RL to WL
         synchronized (this)
         {
            wait();
         }
         cache.put(fqn2, "key", "val");// need to upgrade RL to WL
         tm.commit();
      }
   }

   class MyPutterTimeout extends MyPutter
   {

      public MyPutterTimeout(String name, Fqn fqn1, Fqn fqn2)
      {
         super(name, fqn1, fqn2);
      }

      protected void _run() throws Exception
      {
         try
         {
            super._run();
         }
         catch (TimeoutException timeoutEx)
         {
            tm.rollback();
         }
      }
   }

   private TransactionManager startTransaction() throws SystemException, NotSupportedException
   {
      TransactionManager mgr = cache.getTransactionManager();
      mgr.begin();
      return mgr;
   }

}
