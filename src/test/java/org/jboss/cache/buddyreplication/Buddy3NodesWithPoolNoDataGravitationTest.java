package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "buddyreplication.Buddy3NodesWithPoolNoDataGravitationTest")
public class Buddy3NodesWithPoolNoDataGravitationTest extends AbstractNodeBasedBuddyTest
{

   @BeforeClass
   public void createCaches() throws Exception
   {
      caches = createCaches(3, true, false);
   }
   
   public void testChangingBuddyPoolMembership() throws Exception
   {
      Map map = caches.get(0).getBuddyManager().buddyPool;

      // first test the values
      assertEquals("Failed on cache 1", "A", map.get(caches.get(0).getLocalAddress()));
      assertEquals("Failed on cache 1", "B", map.get(caches.get(1).getLocalAddress()));
      assertEquals("Failed on cache 1", "C", map.get(caches.get(2).getLocalAddress()));

      // now test against each other
      checkConsistentPoolState(caches);

      caches.get(2).stop();
      CacheSPI<Object, Object> newlyCreated = createCache(1, "Z");

      waitForSingleBuddy(caches.get(0), caches.get(1), newlyCreated);
//      TestingUtil.blockUntilViewsReceived(cachePool.toArray(new CacheSPI[0]), VIEW_BLOCK_TIMEOUT);
//      TestingUtil.sleepThread(getSleepTimeout());

      // first test the values
      assertEquals("Failed on cache 1", "A", map.get(caches.get(0).getLocalAddress()));
      assertEquals("Failed on cache 1", "B", map.get(caches.get(1).getLocalAddress()));
      assertEquals("Failed on cache 1", "Z", map.get(newlyCreated.getLocalAddress()));

      // now test against each other
      checkConsistentPoolState(caches);

      newlyCreated.stop();
      newlyCreated.destroy();

      waitForSingleBuddy(caches.get(0), caches.get(1));
      
      caches.get(2).start();

      waitForSingleBuddy(caches.get(0), caches.get(1), caches.get(2));
   }



   public void test3CachesWithPoolNames() throws Exception
   {
      long st = System.currentTimeMillis();
      st = System.currentTimeMillis();

      BuddyManager m = caches.get(0).getBuddyManager();
      Map groupMap = m.buddyPool;

      assertEquals("A", groupMap.get(caches.get(0).getLocalAddress()));
      assertEquals("B", groupMap.get(caches.get(1).getLocalAddress()));
      assertEquals("C", groupMap.get(caches.get(2).getLocalAddress()));
   }

   public void testBuddyPoolSync() throws Exception
   {
      Map map = caches.get(0).getBuddyManager().buddyPool;

      // first test the values
      assertEquals("Failed on cache 1", "A", map.get(caches.get(0).getLocalAddress()));
      assertEquals("Failed on cache 1", "B", map.get(caches.get(1).getLocalAddress()));
      assertEquals("Failed on cache 1", "C", map.get(caches.get(2).getLocalAddress()));

      // now test against each other
      checkConsistentPoolState(caches);
   }


}
