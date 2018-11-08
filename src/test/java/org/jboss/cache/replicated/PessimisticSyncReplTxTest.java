/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.replicated;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.lock.TimeoutException;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.event.NodeEvent;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.util.CachePrinter;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Replicated unit test for sync transactional CacheImpl
 * Note: we use DummyTransactionManager for Tx purpose instead of relying on
 * jta.
 *
 * @version $Revision: 7301 $
 */
@Test(groups = {"functional", "jgroups", "transaction"}, sequential = true, testName = "replicated.PessimisticSyncReplTxTest")
public class PessimisticSyncReplTxTest
{
   private static Log log = LogFactory.getLog(PessimisticSyncReplTxTest.class);
   private CacheSPI<Object, Object> cache1;
   private CacheSPI<Object, Object> cache2;

   Semaphore lock;
   private Throwable t1_ex;
   private Throwable t2_ex;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      t1_ex = t2_ex = null;
      lock = new Semaphore(1, true);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killTransaction(TransactionSetup.getManager());      
      destroyCaches();
   }

   private TransactionManager beginTransaction() throws SystemException, NotSupportedException
   {
      return beginTransaction(cache1);
   }

   private TransactionManager beginTransaction(CacheSPI c) throws SystemException, NotSupportedException
   {
      TransactionManager mgr = c.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();
      return mgr;
   }

   private void initCaches(Configuration.CacheMode caching_mode) throws Exception
   {
      Configuration c1 = new Configuration();
      Configuration c2 = new Configuration();

      c1.setCacheMode(caching_mode);
      c2.setCacheMode(caching_mode);
      c1.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c2.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c1.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      c2.setIsolationLevel(IsolationLevel.SERIALIZABLE);

      c1.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      c2.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      c1.setLockAcquisitionTimeout(5000);
      c2.setLockAcquisitionTimeout(5000);

      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c1, false, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c2, false, getClass());

      configureMultiplexer(cache1);
      configureMultiplexer(cache2);

      cache1.start();
      cache2.start();

      validateMultiplexer(cache1);
      validateMultiplexer(cache2);
   }

   /**
    * Provides a hook for multiplexer integration. This default implementation
    * is a no-op; subclasses that test mux integration would override
    * to integrate the given cache with a multiplexer.
    * <p/>
    * param cache a cache that has been configured but not yet created.
    *
    * @param cache cache
    * @throws Exception exception
    */
   protected void configureMultiplexer(Cache cache) throws Exception
   {
      // default does nothing
   }

   /**
    * Provides a hook to check that the cache's channel came from the
    * multiplexer, or not, as expected.  This default impl asserts that
    * the channel did not come from the multiplexer.
    *
    * @param cache a cache that has already been started
    */
   protected void validateMultiplexer(Cache cache)
   {
      assertFalse("Cache is not using multiplexer", cache.getConfiguration().isUsingMultiplexer());
   }

   private void destroyCaches()
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   public void testLockRemoval() throws Exception
   {
      initCaches(Configuration.CacheMode.REPL_SYNC);
      cache1.getConfiguration().setSyncCommitPhase(true);
      TestingUtil.extractLockManager(cache1).unlockAll(cache1.getRoot());
      TransactionManager tm = beginTransaction();
      cache1.put("/bela/ban", "name", "Bela Ban");
      assertEquals(3, cache1.getNumberOfLocksHeld());
      assertEquals(0, cache2.getNumberOfLocksHeld());
      tm.commit();
      assertEquals(0, cache1.getNumberOfLocksHeld());
      assertEquals(0, cache2.getNumberOfLocksHeld());
   }

   public void testSyncRepl() throws Exception
   {
      Integer age;
      Transaction tx;

      initCaches(Configuration.CacheMode.REPL_SYNC);
      cache1.getConfiguration().setSyncCommitPhase(true);
      cache2.getConfiguration().setSyncCommitPhase(true);

      TransactionManager mgr = beginTransaction();
      cache1.put("/a/b/c", "age", 38);
      tx = mgr.suspend();
      assertNull("age on cache2 must be null as the TX has not yet been committed", cache2.get("/a/b/c", "age"));
      log.debug("cache1: locks held before commit: " + CachePrinter.printCacheLockingInfo(cache1));
      log.debug("cache2: locks held before commit: " + CachePrinter.printCacheLockingInfo(cache2));
      mgr.resume(tx);
      mgr.commit();
      log.debug("cache1: locks held after commit: " + CachePrinter.printCacheLockingInfo(cache1));
      log.debug("cache2: locks held after commit: " + CachePrinter.printCacheLockingInfo(cache2));

      // value on cache2 must be 38
      age = (Integer) cache2.get("/a/b/c", "age");
      assertNotNull("\"age\" obtained from cache2 must be non-null ", age);
      assertTrue("\"age\" must be 38", age == 38);
   }

   public void testSimplePut() throws Exception
   {
      initCaches(Configuration.CacheMode.REPL_SYNC);

      cache1.put("/JSESSION/localhost/192.168.1.10:32882/Courses/0", "Instructor", "Ben Wang");

      cache1.put("/JSESSION/localhost/192.168.1.10:32882/1", "Number", 10);
   }

   public void testSimpleTxPut() throws Exception
   {
      TransactionManager tm;
      final Fqn NODE1 = Fqn.fromString("/one/two/three");
      initCaches(Configuration.CacheMode.REPL_SYNC);

      tm = beginTransaction();
      cache1.put(NODE1, "age", 38);
      tm.commit();

      /*
       tx=beginTransaction();
       cache1.put(NODE1, "age", new Integer(38));
       cache1.put(NODE2, "name", "Ben of The Far East");
       cache1.put(NODE3, "key", "UnknowKey");

       tx.commit();
       */

      /*
       tx=beginTransaction();
       cache1.put(NODE1, "age", new Integer(38));
       cache1.put(NODE1, "AOPInstance", new AOPInstance());
       cache1.put(NODE2, "AOPInstance", new AOPInstance());
       cache1.put(NODE1, "AOPInstance", new AOPInstance());
       tx.commit();
       */
   }

   public void testSyncReplWithModficationsOnBothCaches() throws Exception
   {
      TransactionManager tm;
      final Fqn NODE1 = Fqn.fromString("/one/two/three");
      final Fqn NODE2 = Fqn.fromString("/eins/zwei/drei");

      initCaches(Configuration.CacheMode.REPL_SYNC);

      // create roots first
      cache1.put("/one/two", null);
      cache2.put("/eins/zwei", null);

      cache1.getConfiguration().setSyncCommitPhase(true);
      cache2.getConfiguration().setSyncCommitPhase(true);

      tm = beginTransaction();
      cache1.put(NODE1, "age", 38);

      cache2.put(NODE2, "age", 39);

      try
      {
         tm.commit();
         fail("Should not succeed with SERIALIZABLE semantics");
      }
      catch (Exception e)
      {
         //should be a classic deadlock here.
      }

      /*
       assertTrue(cache1.exists(NODE1));
       assertTrue(cache1.exists(NODE2));
       assertTrue(cache1.exists(NODE1));
       assertTrue(cache2.exists(NODE2));

       age = (Integer) cache1.get(NODE1, "age");
       assertNotNull("\"age\" obtained from cache1 for " + NODE1 + " must be non-null ", age);
       assertTrue("\"age\" must be 38", age == 38);

       age = (Integer) cache2.get(NODE1, "age");
       assertNotNull("\"age\" obtained from cache2 for " + NODE1 + " must be non-null ", age);
       assertTrue("\"age\" must be 38", age == 38);

       age = (Integer) cache1.get(NODE2, "age");
       assertNotNull("\"age\" obtained from cache1 for " + NODE2 + " must be non-null ", age);
       assertTrue("\"age\" must be 39", age == 39);

       age = (Integer) cache2.get(NODE2, "age");
       assertNotNull("\"age\" obtained from cache2 for " + NODE2 + " must be non-null ", age);
       assertTrue("\"age\" must be 39", age == 39);
       */

      assertEquals(0, cache1.getNumberOfLocksHeld());
      assertEquals(0, cache2.getNumberOfLocksHeld());
   }

   public void testSyncReplWithModficationsOnBothCachesSameData() throws Exception
   {
      TransactionManager tm;
      final Fqn NODE = Fqn.fromString("/one/two/three");
      initCaches(Configuration.CacheMode.REPL_SYNC);
      tm = beginTransaction();
      cache1.put(NODE, "age", 38);

      cache2.put(NODE, "age", 39);

      try
      {
         tm.commit();
         fail("commit should throw a RollbackException, we should not get here");
      }
      catch (RollbackException rollback)
      {
      }

      assertEquals(0, cache1.getNumberOfLocksHeld());
      assertEquals(0, cache2.getNumberOfLocksHeld());

      assertEquals(0, cache1.getNumberOfNodes());
      assertEquals(0, cache2.getNumberOfNodes());
   }

   public void testSyncReplWithModficationsOnBothCachesWithRollback() throws Exception
   {
      TransactionManager tm;
      final Fqn fqn1 = Fqn.fromString("/one/two/three");
      final Fqn fqn2 = Fqn.fromString("/eins/zwei/drei");

      initCaches(Configuration.CacheMode.REPL_SYNC);

      cache1.getConfiguration().setSyncRollbackPhase(true);
      cache2.getConfiguration().setSyncRollbackPhase(true);

      tm = beginTransaction();
      cache1.put(fqn1, "age", 38);
      cache2.put(fqn2, "age", 39);

      // this will rollback the transaction
      Transaction tx = tm.getTransaction();
      tx.registerSynchronization(new TransactionAborter(tx));

      try
      {
         tm.commit();
         fail("commit should throw a RollbackException, we should not get here");
      }
      catch (RollbackException rollback)
      {
      }

      assertEquals(0, cache1.getNumberOfLocksHeld());
      assertEquals(0, cache2.getNumberOfLocksHeld());

      assertEquals(0, cache1.getNumberOfNodes());
      assertEquals(0, cache2.getNumberOfNodes());
   }

   /**
    * Test for JBCACHE-361 -- does marking a tx on the remote side
    * rollback-only cause a rollback on the originating side?
    */
   public void testSyncReplWithRemoteRollback() throws Exception
   {
      TransactionManager tm;
      final Fqn NODE1 = Fqn.fromString("/one/two/three");

      initCaches(Configuration.CacheMode.REPL_SYNC);

      cache1.getConfiguration().setSyncRollbackPhase(true);
      cache2.getConfiguration().setSyncRollbackPhase(true);

      // Test with a rollback on the remote side

      // listener aborts any active tx
      //TransactionAborterListener tal = new TransactionAborterListener(cache2);

      tm = beginTransaction();
      cache1.put(NODE1, "age", 38);

      // instead of a listener lets just get a WL on ROOT on cache2. And hold on to it.
      Transaction tx = tm.suspend();

      tm.begin();
      cache2.getRoot().put("x", "y");
      Transaction tx2 = cache2.getTransactionManager().suspend();

      tm.resume(tx);

      try
      {
         tm.commit();
         fail("commit should throw a RollbackException, we should not get here");
      }
      catch (RollbackException rollback)
      {
      }
      finally
      {
         tm.resume(tx2);
         tm.rollback();
      }

      // Sleep, as the commit call to cache2 is async
      TestingUtil.sleepThread(1000);

      //assertNull(tal.getCallbackException());

      assertEquals(0, cache1.getNumberOfLocksHeld());
      assertEquals(0, cache2.getNumberOfLocksHeld());

      assertEquals(0, cache1.getNumberOfNodes());
      assertEquals(0, cache2.getNumberOfNodes());

   }

   public void testASyncRepl() throws Exception
   {
      Integer age;
      TransactionManager tm;

      initCaches(Configuration.CacheMode.REPL_ASYNC);

      tm = beginTransaction();
      cache1.put("/a/b/c", "age", 38);
      Thread.sleep(1000);
      assertNull("age on cache2 must be null as the TX has not yet been committed", cache2.get("/a/b/c", "age"));
      tm.commit();
      Thread.sleep(1000);

      // value on cache2 must be 38
      age = (Integer) cache2.get("/a/b/c", "age");
      assertNotNull("\"age\" obtained from cache2 is null ", age);
      assertTrue("\"age\" must be 38", age == 38);

   }

   /**
    * Tests concurrent modifications: thread1 succeeds and thread2 is blocked until thread1 is done, and then succeeds
    * too. However, this is flawed with the introduction of interceptors, here's why.<br/>
    * <ul>
    * <li>Thread1 acquires the lock for /bela/ban on cache1
    * <li>Thread2 blocks on Thread1 to release the lock
    * <li>Thread1 commits: this means the TransactionInterceptor and the ReplicationInterceptor are called in
    * the sequence in which they registered. Unfortunately, the TransactionInterceptor registered first. In the
    * PREPARE phase, the ReplicationInterceptor calls prepare() in cache2 synchronously. The TxInterceptor
    * does nothing. The the COMMIT phase, the TxInterceptor commits the data by releasing the locks locally and
    * then the ReplicationInterceptor sends an asynchronous COMMIT to cache2.
    * <li>Because the TxInterceptor for Thread1 releases the locks locally <em>before</em> sending the async COMMIT,
    * Thread2 is able to acquire the lock for /bela/ban in cache1 and then starts the PREPARE phase by sending a
    * synchronous PREPARE to cache2. If this PREPARE arrives at cache2 <em>before</em> the COMMIT from Thread1,
    * the PREPARE will block because it attempts to acquire a lock on /bela/ban on cache2 still held by Thread1
    * (which would be released by Thread1's COMMIT). This results in deadlock, which is resolved by Thread2 running
    * into a timeout with subsequent rollback and Thread1 succeeding.<br/>
    * </ul>
    * There are 3 solutions to this:
    * <ol>
    * <li>Do nothing. This is standard behavior for concurrent access to the same data. Same thing if the 2 threads
    * operated on the same data in <em>separate</em> caches, e.g. Thread1 on /bela/ban in cache1 and Thread2 on
    * /bela/ban in cache2. The semantics of Tx commit as handled by the interceptors is: after tx1.commit() returns
    * the locks held by tx1 are release and a COMMIT message is on the way (if sent asynchronously).
    * <li>Force an order over TxInterceptor and ReplicationInterceptor. This would require ReplicationInterceptor
    * to always be fired first on TX commit. Downside: the interceptors have an implicit dependency, which is not
    * nice.
    * <li>Priority-order requests at the receiver; e.g. a COMMIT could release a blocked PREPARE. This is bad because
    * it violates JGroups' FIFO ordering guarantees.
    * </ol>
    * I'm currently investigating solution #2, ie. creating an OrderedSynchronizationHandler, which allows other
    * SynchronizationHandlers to register (atHead, atTail), and the OrderedSynchronizationHandler would call the
    * SynchronizationHandler in the order in which they are defined.
    *
    * @throws Exception exception
    */
   public void testConcurrentPuts() throws Exception
   {
      initCaches(Configuration.CacheMode.REPL_SYNC);
      cache1.getConfiguration().setSyncCommitPhase(true);

      Thread t1 = new Thread("Thread1")
      {
         TransactionManager tm;

         public void run()
         {
            try
            {
               tm = beginTransaction();
               cache1.put("/bela/ban", "name", "Bela Ban");
               TestingUtil.sleepThread(2000);// Thread2 will be blocked until we commit
               tm.commit();
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t1_ex = ex;
            }
         }
      };

      Thread t2 = new Thread("Thread2")
      {
         TransactionManager tm;

         public void run()
         {
            try
            {
               TestingUtil.sleepThread(1000);// give Thread1 time to acquire the lock
               tm = beginTransaction();
               cache1.put("/bela/ban", "name", "Michelle Ban");
               tm.commit();
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t2_ex = ex;
            }
         }
      };

      // Let the game start
      t1.start();
      t2.start();

      // Wait for threads to die
      t1.join();
      t2.join();

      if (t1_ex != null)
      {
         fail("Thread1 failed: " + t1_ex);
      }
      if (t2_ex != null)
      {
         fail("Thread2 failed: " + t2_ex);
      }

      assertEquals("Michelle Ban", cache1.get("/bela/ban", "name"));
   }

   /**
    * Should reproduce JBCACHE-32 problem (http://jira.jboss.com/jira/browse/JBCACHE-32)
    */
   public void testConcurrentCommitsWith1Thread() throws Exception
   {
      _testConcurrentCommits(1);
   }

   /**
    * Should reproduce JBCACHE-32 problem (http://jira.jboss.com/jira/browse/JBCACHE-32)
    */
   public void testConcurrentCommitsWith5Threads() throws Exception
   {
      _testConcurrentCommits(5);
   }

   /**
    * Should reproduce JBCACHE-32 problem (http://jira.jboss.com/jira/browse/JBCACHE-32)
    */
   private void _testConcurrentCommits(int num_threads)
   {
      Object myMutex = new Object();

      Configuration conf1 = new Configuration();
      Configuration conf2 = new Configuration();
      conf1.setClusterName("TempCluster");
      conf2.setClusterName("TempCluster");
      conf1.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      conf2.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      conf1.setSyncCommitPhase(true);
      conf2.setSyncCommitPhase(true);
      conf1.setSyncRollbackPhase(true);
      conf2.setSyncRollbackPhase(true);
      conf1.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      conf2.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      conf1.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      conf2.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      conf1.setLockAcquisitionTimeout(5000);
      conf2.setLockAcquisitionTimeout(5000);

      final CacheSPI<Object, Object> c1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(conf1, false, getClass());
      final CacheSPI<Object, Object> c2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(conf2, false, getClass());

      c1.start();
      c2.start();
      final List<Exception> exceptions = new ArrayList<Exception>();

      class MyThread extends Thread
      {
         final Object mutex;

         public MyThread(String name, Object mutex)
         {
            super(name);
            this.mutex = mutex;
         }

         public void run()
         {
            TransactionManager tm = null;

            try
            {
               tm = beginTransaction(c1);
               c1.put("/thread/" + getName(), null);
               synchronized (mutex)
               {
                  mutex.wait();
               }
               tm.commit();
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
            finally
            {
               try
               {
                  if (tm != null)
                     tm.rollback();
               }
               catch (Exception e)
               {
                  // do nothing
               }
            }
         }
      }

      MyThread[] threads = new MyThread[num_threads];
      for (int i = 0; i < threads.length; i++)
      {
         threads[i] = new MyThread("#" + i, myMutex);
      }
      for (int i = 0; i < threads.length; i++)
      {
         MyThread thread = threads[i];
         thread.start();
      }

      TestingUtil.sleepThread(6000);
      synchronized (myMutex)
      {
         myMutex.notifyAll();
      }

      for (MyThread thread : threads)
      {
         try
         {
            thread.join();
         }
         catch (InterruptedException e)
         {
            e.printStackTrace();
         }
      }


      assertEquals(0, c1.getNumberOfLocksHeld());
      assertEquals(0, c2.getNumberOfLocksHeld());

      c1.stop();
      c2.stop();

      //      if(ex != null)
      //      {
      //         ex.printStackTrace();
      //         fail("Thread failed: " + ex);
      //      }

      // we can only expect 1 thread to succeed.  The others will fail.  So, threads.length -1 exceptions.
      // this is a timing issue - 2 threads still may succeed on a multi cpu system
      // assertEquals(threads.length - 1, exceptions.size());

      for (Exception exception : exceptions)
         assertEquals(TimeoutException.class, exception.getClass());
   }

   /**
    * Conncurrent put on 2 different instances.
    */
   public void testConcurrentPutsOnTwoInstances() throws Exception
   {
      initCaches(Configuration.CacheMode.REPL_SYNC);
      final CacheSPI<Object, Object> c1 = this.cache1;
      final CacheSPI<Object, Object> c2 = this.cache2;

      Thread t1 = new Thread()
      {
         TransactionManager tm;

         public void run()
         {
            try
            {
               tm = beginTransaction();
               c1.put("/ben/wang", "name", "Ben Wang");
               TestingUtil.sleepThread(8000);
               tm.commit();// This should go thru
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t1_ex = ex;
            }
         }
      };

      Thread t2 = new Thread()
      {
         TransactionManager tm;

         public void run()
         {
            try
            {
               TestingUtil.sleepThread(1000);// give Thread1 time to acquire the lock
               tm = beginTransaction();
               c2.put("/ben/wang", "name", "Ben Jr.");
               tm.commit();// This will time out and rollback first because Thread1 has a tx going as well.
            }
            catch (RollbackException rollback_ex)
            {
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t2_ex = ex;
            }
         }
      };

      // Let the game start
      t1.start();
      t2.start();

      // Wait for thread to die but put an insurance of 5 seconds on it.
      t1.join();
      t2.join();

      if (t1_ex != null)
      {
         fail("Thread1 failed: " + t1_ex);
      }
      if (t2_ex != null)
      {
         fail("Thread2 failed: " + t2_ex);
      }
      assertEquals("Ben Wang", c1.get("/ben/wang", "name"));
   }

   public void testPut() throws Exception
   {
      initCaches(Configuration.CacheMode.REPL_SYNC);
      final CacheSPI<Object, Object> c1 = this.cache1;

      Thread t1 = new Thread()
      {
         public void run()
         {
            try
            {
               lock.acquire();
               c1.put("/a/b/c", "age", 38);

               lock.release();
               TestingUtil.sleepThread(300);
               Thread.yield();

               lock.acquire();
               c1.put("/a/b/c", "age", 39);

               lock.release();
               assertEquals(39, c1.get("/a/b/c", "age"));
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t1_ex = ex;
            }
            finally
            {
               lock.release();
            }
         }
      };

      Thread t2 = new Thread()
      {
         public void run()
         {
            try
            {
               TestingUtil.sleepThread(100);
               Thread.yield();
               lock.acquire();
               // Should replicate the value right away.
               Integer val = (Integer) cache2.get("/a/b/c", "age");
               assertEquals(new Integer(38), val);
               lock.release();
               TestingUtil.sleepThread(300);
               Thread.yield();
               TestingUtil.sleepThread(500);
               lock.acquire();
               val = (Integer) cache2.get("/a/b/c", "age");
               lock.release();
               assertEquals(new Integer(39), val);
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t2_ex = ex;
            }
            finally
            {
               lock.release();
            }
         }
      };

      // Let the game start
      t1.start();
      t2.start();

      // Wait for thread to die but put an insurance of 5 seconds on it.
      t1.join();
      t2.join();
      if (t1_ex != null)
      {
         fail("Thread1 failed: " + t1_ex);
      }
      if (t2_ex != null)
      {
         fail("Thread2 failed: " + t2_ex);
      }
   }

   /**
    * Test replicated cache with transaction. Idea is to have two threads running
    * a local cache each that is replicating. Depending on whether cache1 commit/rollback or not,
    * the cache2.get will get different values.
    * Note that we have used sleep to interpose thread execution sequence.
    * Although it's not fool proof, it is rather simple and intuitive.
    */
   public void testPutTx() throws Exception
   {
      TransactionManager tm;

      try
      {
         initCaches(Configuration.CacheMode.REPL_SYNC);
         cache1.getConfiguration().setSyncCommitPhase(true);
         cache2.getConfiguration().setSyncCommitPhase(true);
         tm = beginTransaction();
         cache1.put("/a/b/c", "age", 38);
         cache1.put("/a/b/c", "age", 39);
         Object val = cache2.get("/a/b/c", "age");// must be null as not yet committed
         assertNull(val);
         tm.commit();

         tm = beginTransaction();
         assertEquals(39, cache2.get("/a/b/c", "age"));// must not be null
         tm.commit();
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         t1_ex = t;
      }
      finally
      {
         lock.release();
      }
   }

   /**
    * Have both cache1 and cache2 do add and commit. cache1 commit should time out
    * since it can't obtain the lock when trying to replicate cache2. On the other hand,
    * cache2 commit will succeed since now that cache1 is rollbacked and lock is
    * released.
    */
   public void testPutTx1() throws Exception
   {
      initCaches(Configuration.CacheMode.REPL_SYNC);
      final CacheSPI<Object, Object> c1 = this.cache1;

      final Semaphore threadOneFirstPart = new Semaphore(0);
      final Semaphore threadTwoFirstPart = new Semaphore(0);
      final Semaphore threadOneSecondPart = new Semaphore(0);

      Thread t1 = new Thread()
      {
         public void run()
         {
            TransactionManager tm;

            try
            {
               tm = beginTransaction();
               c1.put("/a/b/c", "age", 38);
               c1.put("/a/b/c", "age", 39);
               threadOneFirstPart.release();

               threadTwoFirstPart.acquire();
               try
               {
                  tm.commit();
               }
               catch (RollbackException ex)
               {
               } finally
               {
                  threadOneSecondPart.release();   
               }
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t1_ex = ex;
            }
         }
      };

      Thread t2 = new Thread()
      {
         public void run()
         {
            TransactionManager tm;

            try
            {
               threadOneFirstPart.acquire();
               tm = beginTransaction();
               assertNull(cache2.get("/a/b/c", "age"));// must be null as not yet committed
               cache2.put("/a/b/c", "age", 40);

               threadTwoFirstPart.release();

               threadOneSecondPart.acquire();
               assertEquals(40, cache2.get("/a/b/c", "age"));// must not be null
               tm.commit();

               tm = beginTransaction();
               assertEquals("After cache2 commit", 40, cache2.get("/a/b/c", "age"));
               tm.commit();
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t2_ex = ex;
            }
            finally
            {
               lock.release();
            }
         }
      };

      // Let the game start
      t1.start();
      t2.start();

      t1.join();
      t2.join();

      if (t1_ex != null)
      {
         fail("Thread1 failed: " + t1_ex);
      }
      if (t2_ex != null)
      {
         fail("Thread2 failed: " + t2_ex);
      }
   }

   public void testPutTxWithRollback() throws Exception
   {
      initCaches(Configuration.CacheMode.REPL_SYNC);
      final CacheSPI<Object, Object> c2 = this.cache1;
      Thread t1 = new Thread()
      {
         public void run()
         {
            TransactionManager tm;

            try
            {
               lock.acquire();
               tm = beginTransaction();
               c2.put("/a/b/c", "age", 38);
               c2.put("/a/b/c", "age", 39);
               lock.release();

               TestingUtil.sleepThread(100);
               lock.acquire();
               tm.rollback();
               lock.release();
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t1_ex = ex;
            }
            finally
            {
               lock.release();
            }
         }
      };

      Thread t2 = new Thread()
      {
         public void run()
         {
            TransactionManager tm;

            try
            {
               sleep(200);
               Thread.yield();
               lock.acquire();
               tm = beginTransaction();
               assertNull(cache2.get("/a/b/c", "age"));// must be null as not yet committed
               lock.release();

               TestingUtil.sleepThread(100);
               lock.acquire();
               assertNull(cache2.get("/a/b/c", "age"));// must be null as rolledback
               tm.commit();
               lock.release();
            }
            catch (Throwable ex)
            {
               ex.printStackTrace();
               t2_ex = ex;
            }
            finally
            {
               lock.release();
            }
         }
      };

      // Let the game start
      t1.start();
      t2.start();

      // Wait for thread to die but put an insurance of 5 seconds on it.
      t1.join();
      t2.join();
      if (t1_ex != null)
      {
         fail("Thread1 failed: " + t1_ex);
      }
      if (t2_ex != null)
      {
         fail("Thread2 failed: " + t2_ex);
      }
   }

   static class TransactionAborter implements Synchronization
   {
      Transaction ltx = null;

      public TransactionAborter(Transaction ltx)
      {
         this.ltx = ltx;
      }

      public void beforeCompletion()
      {
         try
         {
            ltx.setRollbackOnly();
         }
         catch (SystemException e)
         {
            // who cares
         }
      }

      public void afterCompletion(int status)
      {
      }
   }

   @CacheListener
   static class CallbackListener
   {

      CacheSPI<Object, Object> callbackCache;
      Object callbackKey;
      Exception ex;
      final Object mutex = new Object();

      CallbackListener(CacheSPI<Object, Object> cache, Object callbackKey)
      {
         this.callbackCache = cache;
         this.callbackKey = callbackKey;
         cache.getNotifier().addCacheListener(this);
      }

      @NodeModified
      public void nodeModified(NodeEvent e)
      {
         if (!e.isPre())
         {
            // Lock on a mutex so test can't check for an exception
            // until the get call completes
            synchronized (mutex)
            {
               try
               {
                  callbackCache.get(e.getFqn(), callbackKey);
               }
               catch (CacheException exc)
               {
                  exc.printStackTrace();
                  ex = exc;
               }
            }
         }
      }

      Exception getCallbackException()
      {
         synchronized (mutex)
         {
            return ex;
         }
      }

   }

   static class TransactionAborterCallbackListener extends CallbackListener
   {

      TransactionManager callbackTM;

      TransactionAborterCallbackListener(CacheSPI<Object, Object> cache, Object callbackKey)
      {
         super(cache, callbackKey);
         callbackTM = callbackCache.getTransactionManager();
      }

      @NodeModified
      public void nodeModified(NodeEvent ne)
      {
         if (!ne.isPre())
         {
            try
            {
               Transaction tx = callbackTM.getTransaction();
               if (tx != null && tx.getStatus() == Status.STATUS_ACTIVE)
               {
                  // this will rollback the transaction
                  tx.registerSynchronization(new TransactionAborter(tx));
               }
               else
               {
                  super.nodeModified(ne);
               }

            }
            catch (Exception e)
            {
               e.printStackTrace();
               if (ex == null)
               {
                  ex = e;
               }
            }
         }
      }

   }

}
