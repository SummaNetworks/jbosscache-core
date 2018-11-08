package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.util.CachePrinter;
import org.testng.annotations.Test;

import java.util.List;

/**
 * This is to test JBCACHE-1229
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", testName = "buddyreplication.Buddy3NodesNoStateTransfer")
public class Buddy3NodesNoStateTransfer extends BuddyReplicationTestsBase
{
   public void testCachesWithoutStateTransfer() throws Exception
   {
      List<CacheSPI<Object, Object>> caches = createCaches(1, 3, false, false, false, false);
      cachesTL.set(caches);

      int cacheNumber = 0;
      for (CacheSPI c : caches)
      {
         c.getConfiguration().setFetchInMemoryState(false);
         c.start();
         c.put("/" + cacheNumber++, "k", "v");
      }

      waitForBuddy(caches.get(0), caches.get(1), true);
      waitForBuddy(caches.get(1), caches.get(2), true);
      waitForBuddy(caches.get(2), caches.get(0), true);
      Thread.sleep(2000);//wait for state transfer

      for (int i = 0; i < 3; i++)
      {
         int backupIndex = i == 2 ? 0 : i + 1;

         assert caches.get(i).exists("/" + i) : "Data should exist on owner (cache #" + i + ")";
         Fqn backup = fqnTransformer.getBackupFqn(caches.get(i).getLocalAddress(), Fqn.fromString("/" + i));

         assert caches.get(backupIndex).exists(backup.getParent()) : "Backup region should have been created on buddy (cache #" + backupIndex + ")";
         boolean backupStatePropagated = caches.get(backupIndex).exists(backup);
         boolean backupOlderThanOwner = backupIndex < i;
         assert (!backupStatePropagated && !backupOlderThanOwner) || (backupStatePropagated && backupOlderThanOwner) : "Backup state should NOT have been transferred to buddy (cache #" + backupIndex + ")";
      }

      // now NEW state should transfer just fine.

      cacheNumber = 0;
      for (CacheSPI c : caches)
      {
         c.put("/" + (cacheNumber++) + "_NEW", "k", "v");
      }

      for (int i = 0; i < 3; i++)
      {
         int backupIndex = i == 2 ? 0 : i + 1;

         assert caches.get(i).exists("/" + i + "_NEW") : "Data should exist on owner (cache #" + i + ")";
         Fqn backup = fqnTransformer.getBackupFqn(caches.get(i).getLocalAddress(), Fqn.fromString("/" + i + "_NEW"));

         assert caches.get(backupIndex).exists(backup.getParent()) : "Backup region should have been created on buddy (cache #" + backupIndex + ")";
         assert caches.get(backupIndex).exists(backup) : "Backup state should NOT have been transferred to buddy (cache #" + backupIndex + ")";
      }
   }
}
