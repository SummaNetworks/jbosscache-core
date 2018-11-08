package org.jboss.cache.util.internals;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeEvicted;
import org.jboss.cache.notifications.event.NodeEvictedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Watches and waits for eviction events
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 3.0
 */
@CacheListener
public class EvictionWatcher
{
   Cache<?, ?> cache;
   List<Fqn> fqnsToWaitFor;
   CountDownLatch latch;
   EvictionController.Signaller signaller;

   public EvictionWatcher(Cache<?, ?> cache, Fqn... fqnsToWaitFor)
   {
      this(cache, Arrays.asList(fqnsToWaitFor));
   }

   public EvictionWatcher(Cache<?, ?> cache, List<Fqn> fqnsToWaitFor)
   {
      this.cache = cache;
      this.fqnsToWaitFor = new ArrayList<Fqn>(fqnsToWaitFor);
      latch = new CountDownLatch(fqnsToWaitFor.size());
      EvictionController ec = new EvictionController(cache);
      signaller = ec.getEvictionThreadSignaller();
      cache.addCacheListener(this);
   }

   @NodeEvicted
   public void receive(NodeEvictedEvent ee)
   {
      boolean xpect = false;
      if (ee.isPre() && fqnsToWaitFor.contains(ee.getFqn()))
      {
         xpect = true;
         fqnsToWaitFor.remove(ee.getFqn());
         latch.countDown();
      }
   }

   /**
    * Blocks for an eviction event to happen on all the configured Fqns to wait for.
    *
    * @return true if the eviction events occured, false if we timed out.
    */
   public boolean waitForEviction(long timeout, TimeUnit unit) throws InterruptedException
   {
      try
      {
         boolean evicted = latch.await(timeout, unit);

         // now make sure the eviction thread has completed.
         signaller.waitForEvictionThreadCompletion(timeout, unit);
         return evicted;

      }
      finally
      {
         cache.removeCacheListener(this);
      }
   }
}
