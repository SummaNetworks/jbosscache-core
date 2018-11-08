package org.jboss.cache.profiling;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.util.Caches;
import org.jboss.cache.util.Caches.HashKeySelector;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Importnat - make sure you inly enable these tests locally!
 */
@Test(groups = "profiling", enabled = false, testName = "profiling.ProfileMapViewTest")
public class ProfileMapViewTest
{
   /*
      Test configuration options
    */
   protected static final long DURATION = 60 * 1000; // 1 min of GENERATION = a lot of processing.  :-)
   protected static final int NUM_THREADS = 15;
   protected static final int MAX_RANDOM_SLEEP_MILLIS = 1;
   protected static final int MAX_ENTRIES = 200;
   protected static final int WARMUP_LOOPS = 20000;
   protected static final boolean USE_SLEEP = false; // throttle generation a bit

   private List<String> keys = new ArrayList<String>(MAX_ENTRIES);
   private Random r = new Random();
   private Cache<String, String> cache;
   private Map<String, String> map;


   private Log log = LogFactory.getLog(ProfileTest.class);

   @BeforeTest
   public void setUp()
   {
      Configuration cfg = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL);
      cfg.setNodeLockingScheme(NodeLockingScheme.MVCC);
      cfg.setConcurrencyLevel(500);
      cache = new UnitTestCacheFactory<String, String>().createCache(cfg, false, getClass());
   }

   @AfterTest
   public void tearDown()
   {
      cache.stop();
   }


   public void testLocalModeMVCC_RC() throws Exception
   {
      cache.getConfiguration().setIsolationLevel(IsolationLevel.READ_COMMITTED);
      runCompleteTest();
   }

   public void testLocalModeMVCC_RR() throws Exception
   {
      cache.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      runCompleteTest();
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
      keys.clear();
      for (int i = 0; i < MAX_ENTRIES; i++)
      {
         String key = createRandomKey(r);
         while (keys.contains(key)) key = createRandomKey(r);
         if (i % 10 == 0)
         {
            log.warn("Generated " + i + " fqns");
         }
         keys.add(key);
      }
      System.gc();
      long duration = System.currentTimeMillis() - startTime;
      log.warn("Finished init() phase.  " + printDuration(duration));
   }

   private String createRandomKey(Random r)
   {
      StringBuilder s = new StringBuilder("/");
      int depth = r.nextInt(3);
      for (int i = 0; i < depth; i++)
      {
         s.append(r.nextInt(Integer.MAX_VALUE)).append("/");
      }

      return s.toString();
   }

   private Map<String, String> createMap(Cache<String, String> cache)
   {
      return Caches.asPartitionedMap(cache.getRoot(), new HashKeySelector(128));
   }

   private void startup()
   {
      long startTime = System.currentTimeMillis();
      log.warn("Starting cache");
      cache.start();
      map = createMap(cache);
      long duration = System.currentTimeMillis() - startTime;
      log.warn("Started cache.  " + printDuration(duration));
   }

   private void warmup() throws InterruptedException
   {
      long startTime = System.currentTimeMillis();
      ExecutorService exec = Executors.newFixedThreadPool(NUM_THREADS);
      log.warn("Starting warmup");
      // creates all the Fqns since this can be expensive and we don't really want to measure this (for now)
      for (final String key : keys)
      {
         exec.execute(new Runnable()
         {
            public void run()
            {
               // this will create the necessary nodes.
//               cache.put(f, Collections.emptyMap());
               map.put(key, "value");
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
//               Fqn f = fqns.get(r.nextInt(MAX_ENTRIES));
               String key = keys.get(r.nextInt(MAX_ENTRIES));
//               cache.get(f, "");
//               cache.put(f, "k", "v");
//               cache.remove(f, "k");
               map.get(key);
               map.put(key, "value");
               map.remove(key);
            }
         });
      }

      exec.shutdown();
      exec.awaitTermination(360, TimeUnit.SECONDS);

      long duration = System.currentTimeMillis() - startTime;
      log.warn("Finished warmup.  " + printDuration(duration));
      //cache.removeNode(Fqn.ROOT);
      cache.stop();
      cache.start();
      map = createMap(cache);
   }

   private void doTest() throws Exception
   {
      ExecutorService exec = Executors.newFixedThreadPool(NUM_THREADS);
      long end = System.currentTimeMillis() + DURATION;
      long startTime = System.currentTimeMillis();
      log.warn("Starting test");
      int i = 0;
      while (System.currentTimeMillis() < end)
      {
         MyRunnable r = null;
         switch (i % 3)
         {
            case 0:
               r = new Putter(i++);
               break;
            case 1:
               r = new Getter(i++);
               break;
            case 2:
               r = new Remover(i++);
               break;
         }
         exec.execute(r);
//         if (USE_SLEEP) TestingUtil.sleepRandom(MAX_RANDOM_SLEEP_MILLIS);
         if (USE_SLEEP) TestingUtil.sleepThread(MAX_RANDOM_SLEEP_MILLIS);
      }
      log.warn("Finished generating runnables; awaiting executor completion");
      // wait for executors to complete!
      exec.shutdown();
      exec.awaitTermination(((long) i), TimeUnit.SECONDS);  // wait up to 1 sec for each call?
      long duration = System.currentTimeMillis() - startTime;
      log.warn("Finished test.  " + printDuration(duration));
   }

   enum Mode
   {
      PUT, GET, REMOVE
   }

   private abstract class MyRunnable implements Runnable
   {
      int id;
      Mode mode;

      public void run()
      {
         if (id % 100 == 0) log.warn("Processing iteration " + id);
         String k = getRandomString();
         String key = keys.get(r.nextInt(MAX_ENTRIES));
         switch (mode)
         {
            case PUT:
//               cache.put(f, k, getRandomString());
               map.put(key, getRandomString());
               break;
            case GET:
//               cache.get(f, k);
               map.get(key);
               break;
            case REMOVE:
//               cache.remove(f, k);
               map.remove(key);
               break;
         }
      }
   }

   private class Putter extends MyRunnable
   {
      private Putter(int id)
      {
         this.id = id;
         mode = Mode.PUT;
      }
   }

   private class Getter extends MyRunnable
   {
      private Getter(int id)
      {
         this.id = id;
         mode = Mode.GET;
      }
   }

   private class Remover extends MyRunnable
   {
      private Remover(int id)
      {
         this.id = id;
         mode = Mode.REMOVE;
      }
   }

   private String getRandomString()
   {
      StringBuilder sb = new StringBuilder();
      int len = r.nextInt(10);

      for (int i = 0; i < len; i++)
      {
         sb.append((char) (63 + r.nextInt(26)));
      }
      return sb.toString();
   }

   private String printDuration(long duration)
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
}