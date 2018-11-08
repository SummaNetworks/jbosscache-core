package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.notifications.annotation.BuddyGroupChanged;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.util.CachePrinter;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * To test http://jira.jboss.org/jira/browse/JBCACHE-1349
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 */
@Test(groups = "functional", testName = "buddyreplication.EmptyRegionTest")
public class EmptyRegionTest extends BuddyReplicationTestsBase
{
   CacheSPI c1, c2;
   Fqn regionFqn = Fqn.fromString("/a/b/c");
   Fqn region2Fqn = Fqn.fromString("/d/e/f");
   Region region, region2;
   CountDownLatch buddyJoinLatch = new CountDownLatch(2);

   @BeforeMethod
   public void setUp() throws Exception
   {
      c1 = createCache(1, null, false, false, false);
      c1.getConfiguration().setUseRegionBasedMarshalling(true);
      c1.getConfiguration().setFetchInMemoryState(true);
      c2 = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(c1.getConfiguration().clone(), false, getClass());
      c1.start();
      region = c1.getRegion(regionFqn, true);
      region2 = c1.getRegion(region2Fqn, true);
      region.registerContextClassLoader(getClass().getClassLoader());
      region2.registerContextClassLoader(getClass().getClassLoader());
      c1.put(region2Fqn, "key", "value");

      c2.create();
      c2.addCacheListener(new BuddyJoinListener());
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(c1, c2);
      c1 = null;
      c2 = null;
   }

   public void testEmptyRegion() throws InterruptedException
   {
      // region on c1 is empty - with no root node.
      assert c1.getNode(regionFqn) == null : "Node should not exist";
      assert c1.getRegion(regionFqn, false) != null : "Region should exist";
      assert c1.getRegion(regionFqn, false).isActive() : "Region should be active";
      c1.addCacheListener(new BuddyJoinListener());

      // now start c2
      c2.start();

      // wait for buddy join notifications to complete.
      buddyJoinLatch.await(60, TimeUnit.SECONDS);

      // should not throw any exceptions!!

      // make sure region2 stuff did get transmitted!
      assert c2.peek(fqnTransformer.getBackupFqn(c1.getLocalAddress(), region2Fqn), false) != null : "Region2 state should have transferred!";
   }

   @CacheListener
   public class BuddyJoinListener
   {
      @BuddyGroupChanged
      public void buddyJoined(Event e)
      {
         buddyJoinLatch.countDown();
      }
   }
}
