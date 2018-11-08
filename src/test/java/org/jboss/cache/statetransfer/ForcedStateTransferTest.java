/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/

package org.jboss.cache.statetransfer;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Version;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.event.NodeEvent;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * Tests the ability to force a state transfer in the presence of
 * transactional and non-transactional threads that are hung holding
 * locks in the cache.
 *
 * @author Brian Stansberry
 * @version $Revision: 7646 $
 */
@Test(groups = {"functional"}, enabled = false, testName = "statetransfer.ForcedStateTransferTest", description = "this has always been disabled since 1.4.x.  See JBCACHE-315")
public class ForcedStateTransferTest extends StateTransferTestBase
{
   /**
    * Starts a cache in a separate thread, allowing the main thread
    * to abort if state transfer is taking too long.
    */
   static class CacheStarter extends Thread
   {
      CacheSPI<Object, Object> cache;
      boolean useMarshalling;
      Exception failure;

      CacheStarter(CacheSPI<Object, Object> cache, boolean useMarshalling)
      {
         this.cache = cache;
         this.useMarshalling = useMarshalling;
      }

      public void run()
      {
         try
         {
            cache.start();

            if (useMarshalling)
            {
               // If we don't do initial state transfer, there is
               // no guarantee of start() blocking until the view is received
               // so we need to do it ourself
               TestingUtil.blockUntilViewReceived(cache, 2, 60000);
               cache.getRegion(Fqn.ROOT, true).activate();
            }
         }
         catch (Exception e)
         {
            failure = e;
         }
      }
   }

   /**
    * Generic superclass of classes that perform some operation on the
    * cache that is intended to hang with a lock held on certain nodes.
    */
   static abstract class TaskRunner extends Thread
   {
      CacheSPI<Object, Object> cache;
      Fqn fqn;
      String value;
      Exception failure;
      boolean asleep = false;

      TaskRunner(CacheSPI<Object, Object> cache, String rootFqn, String value)
      {
         this.cache = cache;
         this.value = value;
         this.fqn = Fqn.fromRelativeElements(Fqn.fromString(rootFqn), value);
      }

      public void run()
      {
         try
         {
            // do whatever the task is
            executeTask();
         }
         catch (Exception e)
         {
            if (!isDone())
               failure = e;
         }
         finally
         {
            asleep = false;
            // hook to allow final processing
            finalCleanup();
         }
      }

      abstract void executeTask() throws Exception;

      abstract boolean isDone();

      void finalCleanup()
      {
      }

      boolean isAsleep()
      {
         return asleep;
      }
   }

   /**
    * Hangs with an active or rollback-only transaction holding locks.
    */
   static class TxRunner extends TaskRunner
   {
      TransactionManager tm = null;
      boolean rollback = false;
      boolean done = true;

      TxRunner(CacheSPI<Object, Object> cache, String rootFqn, String value, boolean rollback)
      {
         super(cache, rootFqn, value);
         this.rollback = rollback;
      }

      void executeTask() throws Exception
      {
         tm = cache.getTransactionManager();
         tm.begin();

         cache.put(fqn, "KEY", value);

         if (rollback)
            tm.setRollbackOnly();

         asleep = true;
         TestingUtil.sleepThread((long) 25000);
         done = true;
      }

      void finalCleanup()
      {
         if (tm != null)
         {
            try
            {
               tm.commit();
            }
            catch (Exception ignore)
            {
            }
         }
      }

      boolean isDone()
      {
         return done;
      }
   }

   /**
    * TreeCacheListener that hangs the thread in nodeModified().
    */
   @CacheListener
   static class HangThreadListener
   {
      boolean asleep;
      Fqn toHang;
      boolean alreadyHung;
      boolean done;

      HangThreadListener(Fqn toHang)
      {
         this.toHang = toHang;
      }

      @NodeModified
      public void nodeModified(NodeEvent e)
      {
         if (!e.isPre()) hangThread(e.getFqn());
      }

      private void hangThread(Fqn fqn)
      {
         if (!alreadyHung && toHang.equals(fqn))
         {
            asleep = true;
            alreadyHung = true;
            TestingUtil.sleepThread((long) 30000);
            done = true;
            asleep = false;
         }
      }
   }

   /**
    * Hangs with a non-transactional thread holding locks.
    */
   static class HangThreadRunner extends TaskRunner
   {
      HangThreadListener listener;

      HangThreadRunner(CacheSPI<Object, Object> cache, String rootFqn, String value)
      {
         super(cache, rootFqn, value);
         listener = new HangThreadListener(fqn);
         cache.addCacheListener(listener);
      }

      void executeTask() throws Exception
      {
         // Just do a put and the listener will hang the thread
         cache.put(fqn, "KEY", value);
      }

      boolean isAsleep()
      {
         return listener.asleep;
      }

      boolean isDone()
      {
         return listener.done;
      }
   }

   /**
    * Synchronization that hangs the thread either in
    * beforeCompletion() or afterCompletion().
    */
   static class HangThreadSynchronization implements Synchronization
   {
      boolean asleep;
      boolean hangBefore;
      boolean done;

      HangThreadSynchronization(boolean hangBefore)
      {
         this.hangBefore = hangBefore;
      }

      public void beforeCompletion()
      {
         if (hangBefore)
         {
            hang();
         }
      }

      public void afterCompletion(int status)
      {
         if (!hangBefore)
         {
            hang();
         }
      }

      void hang()
      {
         asleep = true;
         TestingUtil.sleepThread((long) 30000);
         done = true;
      }

   }

   /**
    * Hangs with a transactional thread either in the beforeCompletion()
    * or afterCompletion() phase holding locks.
    */
   static class SynchronizationTxRunner extends TaskRunner
   {
      Transaction tx = null;
      HangThreadSynchronization sync;

      SynchronizationTxRunner(CacheSPI<Object, Object> cache, String rootFqn, String value, boolean hangBefore)
      {
         super(cache, rootFqn, value);
         this.sync = new HangThreadSynchronization(hangBefore);
      }

      void executeTask() throws Exception
      {
         TransactionManager tm = cache.getTransactionManager();
         tm.begin();
         tx = tm.getTransaction();
         tx.registerSynchronization(sync);

         cache.put(fqn, "KEY", value);

         // Committing the tx will hang the thread
         tm.commit();
      }

      boolean isAsleep()
      {
         return sync.asleep;
      }

      boolean isDone()
      {
         return sync.done;
      }
   }

   /**
    * Tests the ability to force a state transfer in the presence
    * of active transactions on the sending cache.
    *
    * @throws Exception
    */
   public void testActiveTransaction() throws Exception
   {
      String[] values = {"A", "B", "C"};
      transactionTest(values, false, "REPEATABLE_READ");
   }

   /**
    * Tests the ability to force a state transfer in the presence
    * of a transaction marked rollback-only on the sending cache.
    *
    * @throws Exception
    */
   public void testRollbackOnlyTransaction() throws Exception
   {
      String[] values = {"A", "B", "C"};
      transactionTest(values, true, "REPEATABLE_READ");
   }

   /**
    * Run a basic test with transactional threads doing puts and then
    * hanging before committing.
    *
    * @param values         node names under which puts should be done
    * @param rollback       should the transactions be marked rollback-only
    *                       before hanging
    * @param isolationLevel cache's isolation level
    * @throws Exception
    */
   private void transactionTest(String[] values,
                                boolean rollback,
                                String isolationLevel) throws Exception
   {
      // Create the cache from which state will be requested
      CacheSPI<Object, Object> sender = initializeSender(isolationLevel, false, false);

      // Start threads that will do operations on the cache and then hang
      TxRunner[] runners =
            initializeTransactionRunners(values, sender, "/LOCK", rollback);

      // Create and start the cache that requests a state transfer
      CacheSPI<Object, Object> receiver = startReceiver(isolationLevel, false, false);

      // Confirm the receiver got the expected state and the threads are OK
      checkResults(receiver, runners, false);
   }

   /**
    * Creates and starts a CacheSPI from which another cache will request
    * state. Also adds value "X" under key "KEY" in node "/OK".  This node
    * should be present in the transferred state in any test.
    *
    * @param isolationLevel cache's isolation level
    * @param replSync       is cache REPL_SYNC?
    * @param useMarshalling is the activateRegion() API to be used?
    * @return the cache
    * @throws Exception
    */
   private CacheSPI<Object, Object> initializeSender(String isolationLevel,
                                                     boolean replSync,
                                                     boolean useMarshalling) throws Exception
   {
      CacheSPI<Object, Object> sender = createCache("sender", isolationLevel, replSync, useMarshalling, true);

      if (useMarshalling)
         sender.getRegion(Fqn.ROOT, true).activate();

      sender.put(Fqn.fromString("/OK"), "KEY", "X");

      return sender;
   }

   /**
    * Start a set of TaskRunner threads that do a transactional put on the cache
    * and then go to sleep with the transaction uncommitted.
    *
    * @param values   the name of the node that should be put under
    *                 rootFqn, and the value that shoud be put in its map
    * @param sender   the cache on which the put should be done
    * @param rootFqn  Fqn under which the new node should be inserted -- the
    *                 Fqn of the new node will be /rootFqn/value
    * @param rollback <code>true</code> if the tx should be marked
    *                 rollback-only before the thread goes to sleep
    * @return the TaskRunner threads
    */
   private TxRunner[] initializeTransactionRunners(String[] values,
                                                   CacheSPI<Object, Object> sender,
                                                   String rootFqn,
                                                   boolean rollback)
   {
      TxRunner[] runners = new TxRunner[values.length];
      for (int i = 0; i < values.length; i++)
      {
         runners[i] = new TxRunner(sender, rootFqn, values[i], rollback);
         initializeRunner(runners[i]);
      }

      return runners;
   }

   /**
    * Starts the runner and waits up to 1 second until it is asleep, confirming
    * that it is alive.
    *
    * @param runner
    */
   private void initializeRunner(TaskRunner runner)
   {
      runner.start();

      // Loop until it executes its put and goes to sleep (i.e. hangs)
      long start = System.currentTimeMillis();
      while (!(runner.isAsleep()))
      {
         assertTrue(runner.getClass().getName() + " " + runner.value +
               " is alive", runner.isAlive());
         // Avoid hanging test fixture by only waiting 1 sec before failing
         assertFalse(runner.getClass().getName() + " " + runner.value +
               " has not timed out",
               (System.currentTimeMillis() - start) > 1000);
      }
   }

   /**
    * Checks whether the receiver cache has the expected state and whether
    * the runners ran cleanly.  Also terminates the runners.
    *
    * @param receiver    the cache that received state
    * @param runners     the task runners
    * @param allowValues true if the runners' values are expected to
    *                    be in the cache state; false otherwise
    * @throws CacheException
    */
   private void checkResults(CacheSPI<Object, Object> receiver,
                             TaskRunner[] runners,
                             boolean allowValues) throws CacheException
   {
      // Check that the runners are alive and kill them
      boolean[] aliveStates = new boolean[runners.length];
      for (int i = 0; i < runners.length; i++)
      {
         aliveStates[i] = runners[i].isAlive();
         if (aliveStates[i])
            runners[i].interrupt();
      }

      // Confirm we got the "non-hung" state
      assertEquals("OK value correct", "X", receiver.get(Fqn.fromString("/OK"), "KEY"));

      for (int i = 0; i < runners.length; i++)
      {
         assertTrue("Runner " + runners[i].value + " was alive", aliveStates[i]);
         assertNull("Runner " + runners[i].value + " ran cleanly", runners[i].failure);
         if (allowValues)
         {
            assertEquals("Correct value in " + runners[i].fqn,
                  runners[i].value, receiver.get(runners[i].fqn, "KEY"));
         }
         else
         {
            assertNull("No value in " + runners[i].fqn,
                  receiver.get(runners[i].fqn, "KEY"));
         }
      }
   }

   /**
    * Tests the ability to force a state transfer in the presence of
    * a hung thread holding a lock on the sending cache.
    *
    * @throws Exception
    */
   public void testHungThread() throws Exception
   {
      // Create the cache from which state will be requested
      CacheSPI<Object, Object> sender = initializeSender("REPEATABLE_READ", false, false);

      // Start threads that will do operations on the cache and then hang
      String[] values = {"A", "B", "C"};
      HangThreadRunner[] runners = initializeHangThreadRunners(values, sender, "/LOCK");

      // Create and start the cache that requests a state transfer
      CacheSPI<Object, Object> receiver = startReceiver("REPEATABLE_READ", false, false);

      // Confirm the receiver got the expected state and the threads are OK
      checkResults(receiver, runners, true);
   }

   /**
    * Start a set of TaskRunner threads that do a non-transactional put on the
    * cache and then go to sleep with the thread hung in a
    * TreeCacheListener and locks unreleased
    *
    * @param values  the name of the node that should be put under
    *                rootFqn, and the value that shoud be put in its map
    * @param sender  the cache on which the put should be done
    * @param rootFqn Fqn under which the new node should be inserted -- the
    *                Fqn of the new node will be /rootFqn/value
    * @return the TaskRunner threads
    */
   private HangThreadRunner[] initializeHangThreadRunners(String[] values,
                                                          CacheSPI<Object, Object> sender,
                                                          String rootFqn)
   {
      HangThreadRunner[] runners = new HangThreadRunner[values.length];
      for (int i = 0; i < values.length; i++)
      {
         runners[i] = new HangThreadRunner(sender, rootFqn, values[i]);
         initializeRunner(runners[i]);
      }

      return runners;
   }

   /**
    * Tests the ability to force a state transfer in the presence
    * of a transaction that is hung in a
    * Synchronization.beforeCompletion() call.
    *
    * @throws Exception
    */
   public void testBeforeCompletionLock() throws Exception
   {
      synchronizationTest(true);
   }

   /**
    * Tests the ability to force a state transfer in the presence
    * of a transaction that is hung in a
    * Synchronization.beforeCompletion() call.
    *
    * @throws Exception
    */
   public void testAfterCompletionLock() throws Exception
   {
      synchronizationTest(false);
   }

   /**
    * Tests the ability to force a state transfer in the presence
    * of a transaction that is hung either in a
    * Synchronization.beforeCompletion() or Synchronization.afterCompletion()
    * call.
    *
    * @param hangBefore <code>true</code> if the thread should hang in
    *                   <code>beforeCompletion()</code>, <code>false</code>
    *                   if it should hang in <code>afterCompletion</code>
    * @throws Exception
    */
   private void synchronizationTest(boolean hangBefore) throws Exception
   {
      CacheSPI<Object, Object> sender = initializeSender("REPEATABLE_READ", false, false);

      String[] values = {"A", "B", "C"};
      SynchronizationTxRunner[] runners =
            initializeSynchronizationTxRunners(values, sender, "/LOCK", hangBefore);

      CacheSPI<Object, Object> receiver = startReceiver("REPEATABLE_READ", false, false);

      checkResults(receiver, runners, !hangBefore);
   }


   /**
    * Start a set of TaskRunner threads that do a transactional put on the
    * cache and then go to sleep with the thread hung in a
    * transaction Synchronization call and locks unreleased
    *
    * @param values     the name of the node that should be put under
    *                   rootFqn, and the value that shoud be put in its map
    * @param sender     the cache on which the put should be done
    * @param rootFqn    Fqn under which the new node should be inserted -- the
    *                   Fqn of the new node will be /rootFqn/value
    * @param hangBefore <code>true</code> if the thread should hang in
    *                   <code>beforeCompletion()</code>, <code>false</code>
    *                   if it should hang in <code>afterCompletion</code>
    * @return the TaskRunner threads
    */
   private SynchronizationTxRunner[] initializeSynchronizationTxRunners(String[] values,
                                                                        CacheSPI<Object, Object> sender,
                                                                        String rootFqn,
                                                                        boolean hangBefore)
   {
      SynchronizationTxRunner[] runners =
            new SynchronizationTxRunner[values.length];
      for (int i = 0; i < values.length; i++)
      {
         runners[i] = new SynchronizationTxRunner(sender, rootFqn, values[i], hangBefore);
         initializeRunner(runners[i]);
      }
      return runners;
   }

   /**
    * Tests the ability to force a state transfer in the presence
    * of multiple issues on the sending cache (active transactions,
    * rollback-only transactions, transactions hung in beforeCompletion() and
    * afterCompletion() calls, as well as hung threads).
    *
    * @throws Exception
    */
   public void testMultipleProblems() throws Exception
   {
      multipleProblemTest("REPEATABLE_READ", "/LOCK", false, false);
   }

   /**
    * Tests the ability to force a state transfer in the presence
    * of an active transaction in the sending cache
    * and isolation level SERIALIZABLE.
    *
    * @throws Exception
    */
   public void testSerializableIsolation() throws Exception
   {
      multipleProblemTest("SERIALIZABLE", "/", false, false);
   }

   /**
    * Tests the ability to force a partial state transfer with multiple
    * "problem" actors holding locks on the sending node.  Same test as
    * {@link #testMultipleProblems()} except the partial state transfer API is
    * used instead of an initial state transfer.
    *
    * @throws Exception
    */
   public void testPartialStateTransfer() throws Exception
   {
      multipleProblemTest("REPEATABLE_READ", "/LOCK", false, true);
   }

   /**
    * Tests the ability to force a partial state transfer with multiple
    * "problem" actors holding locks on the sending node and cache mode
    * REPL_SYNC.  Same test as {@link #testMultipleProblems()} except the
    * cache is configured for REPL_SYNC.
    *
    * @throws Exception
    */
   public void testReplSync() throws Exception
   {
      multipleProblemTest("REPEATABLE_READ", "/LOCK", true, false);
   }

   /**
    * Tests the ability to force a partial state transfer with multiple
    * "problem" actors holding locks on the sending node.
    *
    * @throws Exception
    */
   private void multipleProblemTest(String isolationLevel,
                                    String rootFqn,
                                    boolean replSync,
                                    boolean useMarshalling) throws Exception
   {
      CacheSPI<Object, Object> sender = initializeSender(isolationLevel, replSync, useMarshalling);

      // Do the "after" nodes first, otherwise if there is a /LOCK parent
      // node, the rollback of a tx will remove it causing the test to fail
      // since the child node created by it will be gone as well.
      // This is really a REPEATABLE_READ bug that this test isn't intended
      // to catch; will create a separate locking test that shows it
      String[] val1 = {"A", "B", "C"};
      SynchronizationTxRunner[] after =
            initializeSynchronizationTxRunners(val1, sender, rootFqn, false);

      String[] val2 = {"D", "E", "F"};
      SynchronizationTxRunner[] before =
            initializeSynchronizationTxRunners(val2, sender, rootFqn, true);

      String[] val3 = {"G", "H", "I"};
      TxRunner[] active =
            initializeTransactionRunners(val3, sender, rootFqn, false);

      String[] val4 = {"J", "K", "L"};
      TxRunner[] rollback =
            initializeTransactionRunners(val4, sender, rootFqn, true);

      String[] val5 = {"M", "N", "O"};
      HangThreadRunner[] threads =
            initializeHangThreadRunners(val5, sender, rootFqn);

      CacheSPI<Object, Object> receiver = startReceiver(isolationLevel, replSync, useMarshalling);

      checkResults(receiver, active, false);
      checkResults(receiver, rollback, false);
      checkResults(receiver, before, false);
      checkResults(receiver, after, true);
      checkResults(receiver, threads, true);
   }

   protected String getReplicationVersion()
   {
      return Version.version;
   }

   /**
    * Starts a cache that requests state from another cache.  Confirms
    * that the receiver cache starts properly.
    *
    * @param isolationLevel
    * @param replSync
    * @param useMarshalling
    * @return the receiver cache
    * @throws Exception
    */
   private CacheSPI<Object, Object> startReceiver(String isolationLevel,
                                                  boolean replSync,
                                                  boolean useMarshalling) throws Exception
   {
      CacheSPI<Object, Object> receiver = createCache("receiver", isolationLevel, replSync, useMarshalling, false);

      // Start the cache in a separate thread so we can kill the
      // thread if the cache doesn't start properly
      CacheStarter starter = new CacheStarter(receiver, useMarshalling);

      starter.start();

      starter.join(20000);

      boolean alive = starter.isAlive();
      if (alive)
         starter.interrupt();
      assertFalse("Starter finished", alive);

      assertNull("No exceptions in starter", starter.failure);

      return receiver;
   }

   /**
    * Override the superclass version to set an unlimited state transfer timeout
    * and a 1 sec lock acquisition timeout.
    */
   private CacheSPI<Object, Object> createCache(String cacheID,
                                                String isolationLevel,
                                                boolean replSync,
                                                boolean useMarshalling,
                                                boolean startCache)
         throws Exception
   {
      CacheSPI<Object, Object> result = super.createCache(replSync,
            useMarshalling, false, false, false, true);
      result.getConfiguration().setStateRetrievalTimeout(0);
      result.getConfiguration().setLockAcquisitionTimeout(1000);
      result.getConfiguration().setIsolationLevel(isolationLevel);

      if (startCache)
         result.start();

      return result;
   }


}
