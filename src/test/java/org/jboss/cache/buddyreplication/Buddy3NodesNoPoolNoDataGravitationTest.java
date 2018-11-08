package org.jboss.cache.buddyreplication;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.jboss.cache.util.SingleBuddyGravitationHelper.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", invocationCount = 10, testName = "buddyreplication.Buddy3NodesNoPoolNoDataGravitationTest")
public class Buddy3NodesNoPoolNoDataGravitationTest extends AbstractNodeBasedBuddyTest
{

   protected List<ReplicationListener> replListener = new ArrayList<ReplicationListener>();

   @BeforeClass
   public void createCaches() throws Exception
   {
      caches = createCaches(3, false, false);
      for (Cache c : caches)
      {
        ReplicationListener listener = ReplicationListener.getReplicationListener(c);
        replListener.add(listener);
      }
   }

   public void testSingleBuddy() throws Exception
   {
      waitForBuddy(caches.get(0), caches.get(1), true);
      waitForBuddy(caches.get(1), caches.get(2), true);
      waitForBuddy(caches.get(2), caches.get(0), true);
   }

   public void testSimplePut() throws Exception
   {
      String fqn = "/test";
      String backupFqn = "/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + fqnTransformer.getGroupNameFromAddress(caches.get(0).getLocalAddress()) + fqn;

      assertNoStaleLocks(caches);

      // put something in cache 1
      caches.get(0).put(fqn, key, value);

      assertNoStaleLocks(caches);

      // this should be in neither of the other cachePool' "main" trees
      assertEquals(value, caches.get(0).get(fqn, key));
      assertNull("Should be null", caches.get(1).get(fqn, key));
      assertNull("Should be null", caches.get(2).get(fqn, key));

      // check the backup trees

      assertEquals("Buddy should have data in backup tree", value, caches.get(1).get(backupFqn, key));
      assertNull("Should be null", caches.get(2).get(backupFqn, key));

      assertNoStaleLocks(caches);
   }


   public void testPutAndRemove() throws Exception
   {
      String fqn = "/test";
      String backupFqn = "/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + fqnTransformer.getGroupNameFromAddress(caches.get(0).getLocalAddress()) + fqn;

      assertNoStaleLocks(caches);

      // put something in cache 1
      caches.get(0).put(fqn, key, value);

      assertNoStaleLocks(caches);

      // this should be in neither of the other cachePool' "main" trees
      assertEquals(value, caches.get(0).get(fqn, key));
      assertNull("Should be null", caches.get(1).get(fqn, key));
      assertNull("Should be null", caches.get(2).get(fqn, key));

      // check the backup trees

      assertEquals("Buddy should have data in backup tree", value, caches.get(1).get(backupFqn, key));
      assertNull("Should be null", caches.get(2).get(backupFqn, key));

      assertNoStaleLocks(caches);

      // now remove
      caches.get(0).removeNode(fqn);
      assertNoStaleLocks(caches);


      assertNull("Should be null", caches.get(0).get(fqn, key));
      assertNull("Should be null", caches.get(1).get(fqn, key));
      assertNull("Should be null", caches.get(2).get(fqn, key));

      // check the backup trees
      assertNull("Should be null", caches.get(0).get(backupFqn, key));
      assertNull("Should be null", caches.get(1).get(backupFqn, key));
      assertNull("Should be null", caches.get(2).get(backupFqn, key));

      assertNoStaleLocks(caches);
   }

   public void testDataReplicationSuppression() throws Exception
   {
      Fqn fqn = Fqn.fromString("/test");
      Fqn backupFqn = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), fqn);

      replListener.get(1).expect(PutKeyValueCommand.class);
      caches.get(0).put(fqn, key, value);
      replListener.get(1).waitForReplicationToOccur();

      TestingUtil.dumpCacheContents(caches);

      assertEquals("value", caches.get(0).get(fqn, key));
      assertFalse(caches.get(0).exists(backupFqn));
      assertEquals("value", caches.get(1).get(backupFqn, key));
      assertFalse(caches.get(1).exists(fqn));
      assertFalse(caches.get(2).exists(fqn));
      assertFalse(caches.get(2).exists(backupFqn));

      assertNoLocks(caches);

      backupFqn = fqnTransformer.getBackupFqn(caches.get(1).getLocalAddress(), fqn);

      inReplicationListeners(replListener).dataWillGravitateFrom(0).to(1);
      caches.get(1).getInvocationContext().getOptionOverrides().setForceDataGravitation(true);
      log.info("Before gravitation call...");
      assertEquals("value", caches.get(1).get(fqn, key));
      expectGravitation();

      TestingUtil.dumpCacheContents(caches);

      assertFalse(caches.get(1).exists(backupFqn));
      assertEquals("value", caches.get(2).get(backupFqn, key));
      assertFalse(caches.get(2).exists(fqn));
      assertFalse(caches.get(0).exists(fqn));
      assertFalse(caches.get(0).exists(backupFqn));
   }

   @Test(dependsOnMethods = {"testSingleBuddy", "testSimplePut", "testPutAndRemove", "testDataReplicationSuppression"})
   public void testRemovalFromClusterSingleBuddy() throws Exception
   {
      try
      {
         waitForBuddy(caches.get(0), caches.get(1), true);
         waitForBuddy(caches.get(1), caches.get(2), true);
         waitForBuddy(caches.get(2), caches.get(0), true);

         // now remove a cache from the cluster
         caches.get(2).stop();

         TestingUtil.sleepThread(getSleepTimeout());

         // now test new buddy groups
         waitForBuddy(caches.get(0), caches.get(1), true);
         waitForBuddy(caches.get(1), caches.get(0), true);
         assertNoLocks(caches);
      }
      finally
      {
         caches.get(2).start();
         replListener.set(2, ReplicationListener.getReplicationListener(caches.get(2)));
      }
   }
}
