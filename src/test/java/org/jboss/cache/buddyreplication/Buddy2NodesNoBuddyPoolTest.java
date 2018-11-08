package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Cache;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.CachePrinter;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import org.testng.annotations.*;

import java.util.List;
import java.util.ArrayList;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "buddyreplication.Buddy2NodesNoBuddyPoolTest")
public class Buddy2NodesNoBuddyPoolTest extends AbstractNodeBasedBuddyTest
{
   @BeforeClass
   public void createCaches() throws Exception
   {
      caches = createCaches(2, false);
   }

   @AfterMethod
   @Override
   public void tearDown() throws Exception
   {
      if (caches.get(2) != null)
      {
         Cache cache = caches.get(2);
         caches.remove(2);
         cache.stop();
         cache.destroy();
      }
      super.tearDown();
   }

   @Test(dependsOnMethods = "testBuddyJoin")
   public void testAddingNewCaches() throws Exception
   {
      // get some data in there.

      caches.get(0).put("/cache0", "k", "v");
      caches.get(1).put("/cache1", "k", "v");

      waitForBuddy(caches.get(0), caches.get(1), true);
      waitForBuddy(caches.get(1), caches.get(0), true);

      assert caches.get(0).peek(fqnTransformer.getBackupRoot(caches.get(0).getLocalAddress()), false) == null : "Should not have backup region for self";
      assert caches.get(0).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) != null : "Should have backup region for buddy";

      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(0).getLocalAddress()), false) != null : "Should have backup region for buddy";
      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) == null : "Should not have backup region for self";

      caches.add(createCache(1, null));

      TestingUtil.blockUntilViewsReceived(60000, caches);

      waitForBuddy(caches.get(0), caches.get(1), true);
      waitForBuddy(caches.get(1), caches.get(2), true);
      waitForBuddy(caches.get(2), caches.get(0), true);


      assert caches.get(0).peek(fqnTransformer.getBackupRoot(caches.get(0).getLocalAddress()), false) == null : "Should not have backup region for self";
      assert caches.get(0).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) == null : "Should have backup region for non-buddy";
      assert caches.get(0).peek(fqnTransformer.getBackupRoot(caches.get(2).getLocalAddress()), false) != null : "Should have backup region for buddy";

      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(0).getLocalAddress()), false) != null : "Should have backup region for buddy";
      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) == null : "Should not have backup region for self";
      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(2).getLocalAddress()), false) == null : "Should not have backup region for non-buddy";

      assert caches.get(2).peek(fqnTransformer.getBackupRoot(caches.get(0).getLocalAddress()), false) == null : "Should not have backup region for non-buddy";
      assert caches.get(2).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) != null : "Should have backup region for buddy";
      assert caches.get(2).peek(fqnTransformer.getBackupRoot(caches.get(2).getLocalAddress()), false) == null : "Should not have backup region for self";

      // ensure no state transfer has happened!!
      assert caches.get(2).peek(Fqn.fromString("/cache0"), false) == null : "Unnecessary state should not have been transferred!";
      assert caches.get(2).peek(Fqn.fromString("/cache1"), false) == null : "Unnecessary state should not have been transferred!";

      // ensure backup state has been transferred.
      assert caches.get(2).peek(fqnTransformer.getBackupFqn(caches.get(1).getLocalAddress(), Fqn.fromString("/cache1")), false) != null : "Backup state should have transferred!";
   }


   public void testBuddyJoin() throws Exception
   {
      String key = "key";
      String value = "value";

      Fqn fqn = Fqn.fromString("/test");
      Fqn backupFqn = fqnTransformer.getBackupFqn(caches.get(1).getLocalAddress(), fqn);

      assertNoStaleLocks(caches);

      // put something in cache 1
      caches.get(1).put(fqn, key, value);

      assertNoStaleLocks(caches);

      // this should be in neither of the other cachePool' "main" trees
      assertEquals(value, caches.get(1).get(fqn, key));
      assertFalse("Should be false", caches.get(0).exists(fqn));

      // check the backup trees
      assertEquals("Buddy should have data in backup tree", value, caches.get(0).get(backupFqn, key));

      assertNoStaleLocks(caches);

      // now add a new cache to the cluster
      caches.add(createCache(1, null));

      // allow this cache a few msecs to join
      TestingUtil.blockUntilViewsReceived(3000, caches.get(0), caches.get(1), caches.get(2));

      TestingUtil.sleepThread(2000); // allow buddy group reorg

      List<CacheSPI<Object, Object>> dump = new ArrayList<CacheSPI<Object, Object>>(caches);
      dump.add(caches.get(2));

      // now cachePool.get(1)'s buddy should be cache2, not cache[0]
      assertIsBuddy(caches.get(1), caches.get(2), true);
      // this should still be the same
      assertIsBuddy(caches.get(0), caches.get(1), true);
      // and cache2's buddy should be cache[0]
      assertIsBuddy(caches.get(2), caches.get(0), true);

      // so now the backup data we saw on cache[0] should have been removed.
      assertFalse("This backup data should have been removed", caches.get(0).exists(backupFqn));

      // while cache2 should now posess this backup (due to a state transfer)
      assertEquals("Backup state should have been transferred to this new cache instance", value, caches.get(2).get(backupFqn, key));

      caches.get(1).removeNode(fqn);
      assertNoStaleLocks(caches);


      assertFalse("Should be null", caches.get(0).exists(fqn));
      assertFalse("Should be null", caches.get(1).exists(fqn));
      assertFalse("Should be null", caches.get(2).exists(fqn));

      // check the backup trees
      assertFalse("Should be null", caches.get(0).exists(backupFqn));
      assertFalse("Should be null", caches.get(1).exists(backupFqn));
      assertFalse("Should be null", caches.get(2).exists(backupFqn));
      assertNoStaleLocks(caches);
   }
}
