package org.jboss.cache.notifications;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeVisited;
import org.jboss.cache.notifications.event.Event;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Manik Surtani
 */
@Test(groups = "functional", testName = "notifications.CacheListenerRemovalTest")
public class CacheListenerRemovalTest
{
   public void testListenerRemoval()
   {
      Cache cache = new UnitTestCacheFactory().createCache(getClass());
      AtomicInteger i = new AtomicInteger(0);
      try
      {
         assert 0 == cache.getCacheListeners().size();
         Listener l = new Listener(i);
         cache.addCacheListener(l);
         assert 1 == cache.getCacheListeners().size();
         assert cache.getCacheListeners().iterator().next() == l;
         assert 0 == i.get();
         cache.get(Fqn.ROOT, "x");
         assert 1 == i.get();

         // remove the listener
         cache.removeCacheListener(l);
         assert 0 == cache.getCacheListeners().size();
         i.set(0);
         assert 0 == i.get();
         cache.get(Fqn.ROOT, "x");
         assert 0 == i.get();
      }
      finally
      {
         cache.stop();
      }
   }

   @CacheListener
   public static class Listener
   {
      AtomicInteger i;

      private Listener(AtomicInteger i)
      {
         this.i = i;
      }

      @NodeVisited
      public void listen(Event e)
      {
         if (e.isPre()) i.incrementAndGet();
      }
   }
}
