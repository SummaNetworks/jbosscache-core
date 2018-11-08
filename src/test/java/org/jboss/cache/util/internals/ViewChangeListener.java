package org.jboss.cache.util.internals;

import org.jboss.cache.Cache;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.ViewChanged;
import org.jboss.cache.notifications.event.ViewChangedEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A class that registers a cache listener with a given cache, and waits for a view change on the cache.
 * <p/>
 * Sample usage:
 * <pre>
 *    Cache c = getCache();
 *    ViewChangeListener vcl = new ViewChangeListener(c);
 *    assert vcl.waitForViewChange(60, TimeUnit.SECONDS); // will block for up to 60 seconds for a view change on cache c
 * </pre>
 */
@CacheListener
public class ViewChangeListener
{
   CountDownLatch latch;

   /**
    * Constructs a view change listener
    *
    * @param cache cache to listen on for view change events
    */
   public ViewChangeListener(Cache cache)
   {
      this.latch = new CountDownLatch(1);
      cache.addCacheListener(this);
   }

   @ViewChanged
   public void handleViewChange(ViewChangedEvent e)
   {
      if (!e.isPre()) latch.countDown();
   }

   /**
    * Waits for up to millis milliseconds for a view change to be received.
    *
    * @param timeout length of time to wait for a view change
    * @param unit    time unit to use
    * @return true if a view change is received, false otherwise.
    * @throws InterruptedException
    */
   public boolean waitForViewChange(long timeout, TimeUnit unit) throws InterruptedException
   {
      return latch.await(timeout, unit);
   }
}