/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.optimistic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Cache;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * Tests multiple thread access on opt locked cache
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.ThreadedCacheAccessTest")
public class ThreadedCacheAccessTest extends AbstractOptimisticTestCase
{
   private static final Log log = LogFactory.getLog(ThreadedCacheAccessTest.class);
   // 5 concurrent threads.
   private final int numThreads = 10;
   // how many times each thread loops
   private final int numLoopsPerThread = 1000;
   // write frequency.  1 in writeFrequency loops will do a put().
   private final int writeFrequency = 5;

   private final String key = "key", value = "value";

   private CacheSPI cache;
   private WorkerThread[] threads;

   @Override
   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      super.tearDown();
     TestingUtil.killCaches((Cache<Object, Object>) cache);
     cache = null;
      threads = null;
   }

   public void testThreadedMostlyReads() throws Exception
   {
      cache = createCache();
      // write some stuff into the cache.

      cache.put(fqn, key, value);

      threads = new WorkerThread[numThreads];

      for (int i = 0; i < numThreads; i++)
      {
         threads[i] = new WorkerThread();
         threads[i].start();
      }

      for (WorkerThread t : threads)
      {
         t.join();
         if (t.e != null) throw t.e;
      }
   }

   public class WorkerThread extends Thread
   {
      Exception e = null;

      public WorkerThread()
      {
         setDaemon(true);
      }

      @Override
      public void run()
      {
         log.debug(getName() + " starting up ... ");

         for (int j = 0; j < numLoopsPerThread; j++)
         {
            TransactionManager tm = cache.getTransactionManager();
            boolean write = j % writeFrequency == 0;

            try
            {
               tm.begin();
               // read something from the cache - it should be in its own thread.
               cache.get(fqn, key);
               if (write)
               {
                  cache.put(fqn, key, value + j);
               }
               tm.commit();
            }
            catch (Exception e)
            {
               if (!write) // writes could fail from a perfectly acceptable data versioning exception
               {
                  this.e = e;
               }

               try
               {
                  if (tm.getTransaction() != null) tm.rollback();
               }
               catch (Exception e2)
               {
                  log.error("Rollback failed!", e2);
               }
               if (!write) break;
            }
         }
      }
   }
}
