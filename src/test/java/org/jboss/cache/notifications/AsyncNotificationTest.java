package org.jboss.cache.notifications;

import org.jboss.cache.Cache;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeCreated;
import org.jboss.cache.notifications.event.NodeCreatedEvent;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import org.jboss.cache.UnitTestCacheFactory;

@Test(groups = "functional", testName = "notifications.AsyncNotificationTest")
public class AsyncNotificationTest
{
   public void testAsyncNotification() throws InterruptedException
   {
      Cache<String, String> c = null;
      try
      {
         c = new UnitTestCacheFactory<String, String>().createCache(getClass());
         CountDownLatch latch = new CountDownLatch(2);
         AbstractListener syncListener = new Listener(latch);
         AbstractListener asyncListener = new AsyncListener(latch);
         c.addCacheListener(syncListener);
         c.addCacheListener(asyncListener);
         c.put("/a", "k", "v");
         latch.await();
         assert syncListener.caller == Thread.currentThread();
         assert asyncListener.caller != Thread.currentThread();
      }
      finally
      {
         TestingUtil.killCaches(c);
      }
   }

   public abstract static class AbstractListener
   {
      Thread caller;
      CountDownLatch latch;

      protected AbstractListener(CountDownLatch latch)
      {
         this.latch = latch;
      }
   }

   @CacheListener(sync = true)
   public static class Listener extends AbstractListener
   {
      public Listener(CountDownLatch latch)
      {
         super(latch);
      }

      @NodeCreated
      public void handle(NodeCreatedEvent e)
      {
         if (e.isPre())
         {
            caller = Thread.currentThread();
            latch.countDown();
         }
      }
   }

   @CacheListener(sync = false)
   public static class AsyncListener extends AbstractListener
   {
      public AsyncListener(CountDownLatch latch)
      {
         super(latch);
      }

      @NodeCreated
      public void handle(NodeCreatedEvent e)
      {
         if (e.isPre())
         {
            caller = Thread.currentThread();
            latch.countDown();
         }
      }
   }

}
