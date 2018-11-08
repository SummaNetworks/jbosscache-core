package org.jboss.cache.profiling;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.profiling.testinternals.Generator;
import org.jboss.cache.profiling.testinternals.TaskRunner;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test to use with a profiler to profile replication.  To be used in conjunction with ProfileSlaveTest.
 * <p/>
 * Typical usage pattern:
 * <p/>
 * 1.  Start a single test method in ProfileSlaveTest.  This will block until you kill it.
 * 2.  Start the corresponding test in this class, with the same name, in a different JVM, and attached to a profiler.
 * 3.  Profile away!
 * <p/>
 *
 * Importnat - make sure you inly enable these tests locally!
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "profiling", testName = "profiling.ProfileTest", enabled = false)
public class ProfileTest extends AbstractProfileTest
{
   /*
      Test configuration options
    */
   protected static final long NUM_OPERATIONS = 1000000; // DURATION is replaced with a fixed number of operations instead.
   protected static final int NUM_THREADS = 25;
   protected static final int MAX_RANDOM_SLEEP_MILLIS = 1;
   protected static final int MAX_DEPTH = 3;
   protected static final int MAX_OVERALL_NODES = 2000;
   protected static final int WARMUP_LOOPS = 20000;
   protected static final boolean USE_SLEEP = false; // throttle generation a bit


   private List<Fqn> fqns = new ArrayList<Fqn>(MAX_OVERALL_NODES);

   Log log = LogFactory.getLog(ProfileTest.class);

   @Test(enabled = false)
   public void testLocalModePess() throws Exception
   {
      Configuration cfg = cache.getConfiguration();
      cfg.setCacheMode(Configuration.CacheMode.LOCAL);
      cfg.setConcurrencyLevel(2000);
      cfg.setLockAcquisitionTimeout(120000);
      cfg.setLockParentForChildInsertRemove(true);
      cfg.setIsolationLevel(IsolationLevel.READ_COMMITTED);
      cfg.setNodeLockingScheme(Configuration.NodeLockingScheme.MVCC);
      runCompleteTest();
   }

   @Test(enabled = false)
   public void testLocalModeOpt() throws Exception
   {
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      runCompleteTest();
   }

   @Test(enabled = false)
   public void testReplSync() throws Exception
   {
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.MVCC);
      runCompleteTest();
   }

   @Test(enabled = false)
   public void testReplAsync() throws Exception
   {
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.MVCC);
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.REPL_ASYNC);
      cache.getConfiguration().setSerializationExecutorPoolSize(0);
      cache.getConfiguration().setConcurrencyLevel(5000);
      cache.getConfiguration().setClusterConfig(getJGroupsConfig());
      runCompleteTest();
   }

   @Test(enabled = false)
   public void testReplSyncOptimistic() throws Exception
   {
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      runCompleteTest();
   }

   @Test(enabled = false)
   public void testReplAsyncOptimistic() throws Exception
   {
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.REPL_ASYNC);
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      runCompleteTest();
   }

   @Test(enabled = false)
   public void testReplSyncBR() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);
      testReplSync();
   }

   @Test(enabled = false)
   public void testReplAsyncBR() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.MVCC);
      cache.getConfiguration().setConcurrencyLevel(500);
      cache.getConfiguration().setIsolationLevel(IsolationLevel.READ_COMMITTED);
//      cache.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      testReplAsync();
   }

   @Test(enabled = false)
   public void testReplSyncOptBR() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);
      testReplSyncOptimistic();
   }

   @Test(enabled = false)
   public void testReplAsyncOptBR() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);
      testReplAsyncOptimistic();
   }

   private void runCompleteTest() throws Exception
   {
      init();
      startup();
      warmup();
      doTest();

      // wait for user exit
      System.in.read();
   }

   /**
    * Thr following test phases can be profiled individually using triggers in JProfiler.
    */

   protected void init()
   {
      long startTime = System.currentTimeMillis();
      log.warn("Starting init() phase");
      fqns.clear();
      for (int i = 0; i < MAX_OVERALL_NODES; i++)
      {
         Fqn fqn = Generator.createRandomFqn(MAX_DEPTH);
         while (fqns.contains(fqn)) fqn = Generator.createRandomFqn(MAX_DEPTH);
         if (i % 10 == 0)
         {
            log.warn("Generated " + i + " fqns");
         }
         fqns.add(fqn);
      }
      System.gc();
      long duration = System.currentTimeMillis() - startTime;
      log.warn("Finished init() phase.  " + printDuration(duration));
   }

   protected void startup()
   {
      long startTime = System.currentTimeMillis();
      log.warn("Starting cache");
      cache.start();
      long duration = System.currentTimeMillis() - startTime;
      log.warn("Started cache.  " + printDuration(duration));
   }

   private void warmup() throws InterruptedException
   {
      long startTime = System.currentTimeMillis();
      TaskRunner exec = new TaskRunner(NUM_THREADS);
      log.warn("Starting warmup");
      // creates all the Fqns since this can be expensive and we don't really want to measure this (for now)
      for (final Fqn f : fqns)
      {
         exec.execute(new Runnable()
         {
            public void run()
            {
               // this will create the necessary nodes.
               cache.put(f, Collections.emptyMap());
            }
         });
      }

      // loop through WARMUP_LOOPS gets and puts for JVM optimisation
      for (int i = 0; i < WARMUP_LOOPS; i++)
      {
         exec.execute(new Runnable()
         {
            public void run()
            {
               Fqn f = Generator.getRandomElement(fqns);
               cache.get(f, "");
               cache.put(f, "k", "v");
               cache.remove(f, "k");
            }
         });
      }

      exec.stop();

      long duration = System.currentTimeMillis() - startTime;
      log.warn("Finished warmup.  " + printDuration(duration));
      //cache.removeNode(Fqn.ROOT);
      cache.stop();

      startup();
   }

   private void doTest() throws Exception
   {
      TaskRunner exec = new TaskRunner(NUM_THREADS);
      log.warn("Starting test");
      int i;
      long print = NUM_OPERATIONS / 10;

      AtomicLong durationPuts = new AtomicLong();
      AtomicLong durationGets = new AtomicLong();
      AtomicLong durationRemoves = new AtomicLong();

      long stElapsed = System.nanoTime();
      for (i = 0; i < NUM_OPERATIONS; i++)
      {
         MyRunnable r = null;
         switch (i % 3)
         {
            case 0:
               r = new Putter(i, durationPuts);
               break;
            case 1:
               r = new Getter(i, durationGets);
               break;
            case 2:
               r = new Remover(i, durationRemoves);
               break;
         }
         if (i % print == 0)
            log.warn("processing iteration " + i);
         exec.execute(r);
//         if (USE_SLEEP) TestingUtil.sleepRandom(MAX_RANDOM_SLEEP_MILLIS);
         if (USE_SLEEP) TestingUtil.sleepThread(MAX_RANDOM_SLEEP_MILLIS);
      }
      log.warn("Finished generating runnables; awaiting executor completion");
      // wait for executors to complete!
      exec.stop();

      // wait up to 1 sec for each call?
      long elapsedTimeNanos = System.nanoTime() - stElapsed;

      log.warn("Finished test.  " + printDuration((long) toMillis(elapsedTimeNanos)));
      log.warn("Throughput: " + ((double) NUM_OPERATIONS * 1000 / toMillis(elapsedTimeNanos)) + " operations per second (roughly equal numbers of PUT, GET and REMOVE)");
      log.warn("Average GET time: " + printAvg(durationGets.get()));
      log.warn("Average PUT time: " + printAvg(durationPuts.get()));
      log.warn("Average REMOVE time: " + printAvg(durationRemoves.get()));
   }

   private String printAvg(long totalNanos)
   {
      double nOps = (double) (NUM_OPERATIONS / 3);
      double avg = ((double) totalNanos) / nOps;
      double avgMicros = avg / 1000;
      return avgMicros + " µs";
   }

   private double toMillis(long nanos)
   {
      return ((double) nanos / (double) 1000000);
   }

   enum Mode
   {
      PUT, GET, REMOVE
   }

   private abstract class MyRunnable implements Runnable
   {
      int id;
      Mode mode;
      AtomicLong duration;

      public void run()
      {
         String k = Generator.getRandomString();
         Fqn f = Generator.getRandomElement(fqns);
         long d = 0, st = 0;
         switch (mode)
         {
            case PUT:
               st = System.nanoTime();
               cache.put(f, k, Generator.getRandomString());
               d = System.nanoTime() - st;
               break;
            case GET:
               st = System.nanoTime();
               cache.get(f, k);
               d = System.nanoTime() - st;
               break;
            case REMOVE:
               st = System.nanoTime();
               cache.remove(f, k);
               d = System.nanoTime() - st;
               break;
         }
         duration.getAndAdd(d);
      }
   }

   private class Putter extends MyRunnable
   {
      private Putter(int id, AtomicLong duration)
      {
         this.id = id;
         this.duration = duration;
         mode = Mode.PUT;
      }
   }

   private class Getter extends MyRunnable
   {
      private Getter(int id, AtomicLong duration)
      {
         this.id = id;
         this.duration = duration;
         mode = Mode.GET;
      }
   }

   private class Remover extends MyRunnable
   {
      private Remover(int id, AtomicLong duration)
      {
         this.id = id;
         this.duration = duration;
         mode = Mode.REMOVE;
      }
   }

   protected String printDuration(long duration)
   {
      if (duration > 2000)
      {
         double dSecs = ((double) duration / (double) 1000);
         return "Duration: " + dSecs + " seconds";
      }
      else
      {
         return "Duration: " + duration + " millis";
      }
   }

   @Test(enabled = false)
   public void testStateTransfer() throws Exception
   {
      throw new Exception("Implement me");
   }

   @Test(enabled = false)
   public void testStartup() throws Exception
   {
      throw new Exception("Implement me");
   }

   @Test(enabled = false)
   public void testCacheLoading() throws Exception
   {
      throw new Exception("Implement me");
   }

   @Test(enabled = false)
   public void testPassivation() throws Exception
   {
      throw new Exception("Implement me");
   }
}
