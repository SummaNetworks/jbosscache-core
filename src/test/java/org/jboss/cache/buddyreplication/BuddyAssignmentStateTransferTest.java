/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.notifications.annotation.BuddyGroupChanged;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.event.Event;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests how groups are formed and disbanded
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "jgroups"}, testName = "buddyreplication.BuddyAssignmentStateTransferTest")
public class BuddyAssignmentStateTransferTest extends BuddyReplicationTestsBase
{

   protected int timeout = 10000; // !!!

   protected int getSleepTimeout()
   {
      return timeout;
   }

   @CacheListener
   public static class BuddyJoinedListener
   {
      CountDownLatch latch;

      public BuddyJoinedListener(CountDownLatch latch)
      {
         this.latch = latch;
      }

      @BuddyGroupChanged
      public void buddyJoined(Event e)
      {
         latch.countDown();
      }
   }

   private void createCacheWithLatch(CountDownLatch latch) throws Exception
   {
      CacheSPI<Object, Object> cache2 = createCache(1, "TEST", false, false);
      cache2.create();
      cache2.addCacheListener(new BuddyJoinedListener(latch));
      cache2.start();
      cachesTL.get().add(cache2);
   }

   private void replaceLatch(Cache<?, ?> cache, CountDownLatch newLatch)
   {
      BuddyJoinedListener bjl = null;
      for (Object listener : cache.getCacheListeners())
      {
         if (listener instanceof BuddyJoinedListener)
         {
            bjl = (BuddyJoinedListener) listener;
            break;
         }
      }

      if (bjl != null) cache.removeCacheListener(bjl);
      cache.addCacheListener(new BuddyJoinedListener(newLatch));
   }

   public void testNonRegionBasedStateTransfer() throws Exception
   {
      List<CacheSPI<Object, Object>> caches = new ArrayList<CacheSPI<Object, Object>>();
      cachesTL.set(caches);
      caches.add(createCache(1, "TEST", false, true));

      Fqn main = Fqn.fromString("/a/b/c");
      caches.get(0).put(main, "name", "Joe");

      CountDownLatch latch = new CountDownLatch(2);

      //first cache should also wait for buddy groups changes
      caches.get(0).addCacheListener(new BuddyJoinedListener(latch));
      createCacheWithLatch(latch);

      assert latch.await(getSleepTimeout(), TimeUnit.MILLISECONDS) : "Buddy groups not formed after " + getSleepTimeout() + " millis!";

      Fqn test = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), main);

      assertEquals("State not transferred", "Joe", caches.get(1).get(test, "name"));

      latch = new CountDownLatch(1);
      createCacheWithLatch(latch);

      assert latch.await(getSleepTimeout(), TimeUnit.MILLISECONDS) : "Buddy groups not formed after " + getSleepTimeout() + " millis!";

      assertNull("State not transferred", caches.get(2).get(test, "name"));

      latch = new CountDownLatch(1);
      replaceLatch(caches.get(2), latch);
      replaceLatch(caches.get(0), latch);
      // Make 2 the buddy of 0
      caches.get(1).stop();
      caches.set(1, null);

      assert latch.await(getSleepTimeout(), TimeUnit.MILLISECONDS) : "Buddy groups not formed after " + getSleepTimeout() + " millis!";

      assertEquals("State transferred", "Joe", caches.get(2).get(test, "name"));
   }

   public void testRegionBasedStateTransfer() throws Exception
   {
      List<CacheSPI<Object, Object>> caches = new ArrayList<CacheSPI<Object, Object>>();
      cachesTL.set(caches);

      CountDownLatch latch = new CountDownLatch(4);

      caches.add(createCache(1, "TEST", false, false));
      caches.add(createCache(1, "TEST", false, false));
      caches.add(createCache(1, "TEST", false, false));
      // JBCACHE-1234 -- add a 4th cache so when we kill caches[1]
      // caches[0] is not the backup node for caches[2] (although
      // caches[2] *is* the backup node for caches[0]
      caches.add(createCache(1, "TEST", false, false));

      for (Cache c : caches)
      {
         c.getConfiguration().setInactiveOnStartup(true);
         c.getConfiguration().setUseRegionBasedMarshalling(true);
         c.create();
         c.addCacheListener(new BuddyJoinedListener(latch));
      }

      for (Cache c : caches) c.start();

      assert latch.await(getSleepTimeout(), TimeUnit.MILLISECONDS) : "Buddy groups not formed after " + getSleepTimeout() + " millis!";

      Fqn fqnA = Fqn.fromString("/a");
      Fqn fqnD = Fqn.fromString("/d");

      // FIXME We have to use a hack to get JBC to recognize that our regions are for marshalling
      ClassLoader cl = Fqn.class.getClassLoader();
      for (Cache c : caches)
      {
         c.getRegion(fqnA, true).registerContextClassLoader(cl);
         c.getRegion(fqnD, true).registerContextClassLoader(cl);
      }

      for (Cache c : caches) c.getRegion(fqnA, true).activate();

      caches.get(0).getRegion(fqnD, true).activate();
      caches.get(1).getRegion(fqnD, true).activate();

      Fqn mainA = Fqn.fromString("/a/b/c");
      caches.get(0).put(mainA, "name", "Joe");

      Fqn mainD = Fqn.fromString("/d/e/f");
      caches.get(0).put(mainD, "name", "Joe");

      Fqn testA = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), mainA);
      assertEquals("/a replicated", "Joe", caches.get(1).get(testA, "name"));
      assertNull("No backup of /a", caches.get(2).get(testA, "name"));

      Fqn testD = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), mainD);
      assertEquals("/d replicated", "Joe", caches.get(1).get(testD, "name"));
      assertNull("No backup of /d", caches.get(2).get(testD, "name"));

      // Make 2 the buddy of 0 -- this should cause a push from 0 to 2
      latch = new CountDownLatch(1);
      replaceLatch(caches.get(2), latch);
      replaceLatch(caches.get(0), latch);
      caches.get(1).stop();

      assert latch.await(getSleepTimeout(), TimeUnit.MILLISECONDS) : "Buddy groups not formed after " + getSleepTimeout() + " millis!";

      assertEquals("/a state transferred", "Joe", caches.get(2).get(testA, "name"));
      assertNull("/d state not transferred", caches.get(2).get(testD, "name"));

      // JBCACHE-1234 -- Activate region on 2 and 3.  This should cause
      // a pull from 0 by and from 2 by 3.
      caches.get(2).getRegion(fqnD, true).activate();
      caches.get(3).getRegion(fqnD, true).activate();
      assertEquals("/d transferred to cache 2", "Joe", caches.get(2).get(testD, "name"));
      assertNull("/d state not transferred to cache 3", caches.get(3).get(testD, "name"));
   }

   public void testPersistentStateTransfer() throws Exception
   {
      List<CacheSPI<Object, Object>> caches = new ArrayList<CacheSPI<Object, Object>>();
      cachesTL.set(caches);
      CountDownLatch latch = new CountDownLatch(2);

      caches.add(createCacheWithCacheLoader(false, false, false, true, false));
      caches.get(0).getConfiguration().setFetchInMemoryState(false);

      caches.get(0).create();
      caches.get(0).addCacheListener(new BuddyJoinedListener(latch));
      caches.get(0).start();
      Fqn main = Fqn.fromString("/a/b/c");
      caches.get(0).put(main, "name", "Joe");

      caches.add(createCacheWithCacheLoader(false, false, false, true, false));
      caches.get(1).getConfiguration().setFetchInMemoryState(false);

      caches.get(1).create();
      caches.get(1).addCacheListener(new BuddyJoinedListener(latch));

      caches.get(1).start();

      assert latch.await(getSleepTimeout(), TimeUnit.MILLISECONDS) : "Buddy groups not formed after " + getSleepTimeout() + " millis!";

      Fqn test = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), main);

      assertFalse("/a/b/c shld not be bin memory", caches.get(1).exists(test));
      assertNotNull("/a/b/c shld be in CL", caches.get(1).getCacheLoaderManager().getCacheLoader().get(test));
      assertEquals("/a/b/c shld in cache loader", "Joe", caches.get(1).get(test, "name"));
   }
}