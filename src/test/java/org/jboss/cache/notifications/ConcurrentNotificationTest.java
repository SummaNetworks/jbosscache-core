package org.jboss.cache.notifications;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.notifications.annotation.*;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional"}, sequential = true, testName = "notifications.ConcurrentNotificationTest")
public class ConcurrentNotificationTest
{
   private Cache<String, String> cache;
   private Listener listener;
   private Fqn fqn = Fqn.fromString("/a/b/c");
   private static final Log log = LogFactory.getLog(ConcurrentNotificationTest.class);

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = instance.createCache(getClass());
      listener = new Listener();
      cache.addCacheListener(listener);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testNulls()
   {
      cache.put(fqn, "key", null);
      cache.put(fqn, null, "value");
   }

   public void testThreads() throws Exception
   {
      Thread workers[] = new Thread[20];
      final List<Exception> exceptions = new LinkedList<Exception>();
      final int loops = 100;
      final CountDownLatch latch = new CountDownLatch(1);

      for (int i = 0; i < workers.length; i++)
      {
         workers[i] = new Thread()
         {
            public void run()
            {
               try
               {
                  latch.await();
               }
               catch (InterruptedException e)
               {
               }

               for (int j = 0; j < loops; j++)
               {
                  try
                  {
                     cache.put(fqn, "key", "value");
                  }
                  catch (Exception e)
                  {
                     log.error("Exception doing put in loop " + j, e);
                     exceptions.add(new Exception("Caused on thread " + getName() + " in loop " + j + " when doing a put()", e));
                  }

                  try
                  {
                     cache.remove(fqn, "key");
                  }
                  catch (Exception e)
                  {
                     log.error("Exception doing remove in loop " + j, e);
                     exceptions.add(new Exception("Caused on thread " + getName() + " in loop " + j + " when doing a remove()", e));
                  }

                  try
                  {
                     cache.get(fqn, "key");
                  }
                  catch (Exception e)
                  {
                     log.error("Exception doing get in loop " + j, e);
                     exceptions.add(new Exception("Caused on thread " + getName() + " in loop " + j + " when doing a get()", e));
                  }
               }
            }
         };

         workers[i].start();
      }

      latch.countDown();

      for (Thread t : workers)
         t.join();

      for (Exception e : exceptions)
         throw e;

      assertEquals(3 * loops * workers.length + 3, listener.counter.get());
   }

   @CacheListener
   public class Listener
   {
      private AtomicInteger counter = new AtomicInteger(0);

      @NodeModified
      @NodeRemoved
      @NodeVisited
      @NodeCreated
      public void catchEvent(Event e)
      {
         if (e.isPre())
            counter.getAndIncrement();
      }
   }
}
