package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.remote.DataGravitationCleanupCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.notifications.annotation.CacheBlocked;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.CacheUnblocked;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.*;
import static org.jboss.cache.util.SingleBuddyGravitationHelper.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "buddyreplication.Buddy3NodesNoPoolWithDataGravitationTest")
public class Buddy3NodesNoPoolWithDataGravitationTest extends AbstractNodeBasedBuddyTest
{
   private Fqn fqn = Fqn.fromString("test");

   private String key = "key";
   private String value = "value";

   ReplicationListener replicationListener0;
   ReplicationListener replicationListener1;
   ReplicationListener replicationListener2;
   List<ReplicationListener> listeners;

   @BeforeClass
   public void createCaches() throws Exception
   {
      caches = createCaches(3, false, true);
      replicationListener0 = ReplicationListener.getReplicationListener(caches.get(0));
      replicationListener1 = ReplicationListener.getReplicationListener(caches.get(1));
      replicationListener2 = ReplicationListener.getReplicationListener(caches.get(2));
      listeners = new ArrayList<ReplicationListener>();
      listeners.add(replicationListener0);
      listeners.add(replicationListener1);
      listeners.add(replicationListener2);
   }

   //this is needed here as tesng ignores the call from base class
   @AfterMethod
   @Override
   public void tearDown() throws Exception
   {
      super.tearDown();
   }

   public void testDataGravitationDontKillOwner() throws Exception
   {
      Fqn fqn = Fqn.fromString("/test");
      Fqn backupFqn = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), fqn);

      TestingUtil.dumpCacheContents(caches);

      caches.get(0).put(fqn, key, value);

      assertEquals("Value should exist", value, caches.get(0).get(fqn, key));

      // use exists instead of get() to prevent going up the interceptor stack
      assertTrue("Should be false", !caches.get(1).exists(fqn));
      assertTrue("Should be false", !caches.get(2).exists(fqn));

      assertFalse("Should be false", caches.get(0).exists(backupFqn));
      assertTrue("Value be true", caches.get(1).exists(backupFqn));
      assertFalse("Should be false", caches.get(2).exists(backupFqn));

      inReplicationListeners(listeners).dataWillGravitateFrom(0).to(2);
      // according to data gravitation, a call to *any* cache should retrieve the data, and move the data to the new cache.
      assertEquals("Value should have gravitated", value, caches.get(2).get(fqn, key));
      expectGravitation();

      // now lets test the eviction part of gravitation
      Fqn newBackupFqn = fqnTransformer.getBackupFqn(caches.get(2).getLocalAddress(), fqn);

      // use exists instead of get() to prevent going up the interceptor stack
      assertTrue("Should be false", !caches.get(0).exists(fqn));
      assertTrue("Should be false", !caches.get(1).exists(fqn));

      // the old backup should no longer exist
      assertFalse("Should be null", caches.get(0).exists(backupFqn));
      assertFalse("Should be null", caches.get(1).exists(backupFqn));
      assertFalse("Should be null", caches.get(2).exists(backupFqn));

      // and the backup should now exist in caches.get(2)'s buddy which is caches.get(0)
      assertEquals("Value should exist", value, caches.get(0).get(newBackupFqn, key));
      assertFalse("Should be null", caches.get(1).exists(newBackupFqn));
      assertFalse("Should be null", caches.get(2).exists(newBackupFqn));
   }


   public void testTransactionsCommit() throws Exception
   {
      caches.get(0).put(fqn, key, value);
      Fqn oldBackupFqn = Fqn.fromString("/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + fqnTransformer.getGroupNameFromAddress(caches.get(0).getLocalAddress()) + "/test");
      Fqn newBackupFqn = Fqn.fromString("/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + fqnTransformer.getGroupNameFromAddress(caches.get(2).getLocalAddress()) + "/test");

      TransactionManager txman = caches.get(2).getTransactionManager();


      assertTrue(caches.get(0).exists(fqn));
      assertTrue(!caches.get(1).exists(fqn));
      assertTrue(!caches.get(2).exists(fqn));
      assertTrue(!caches.get(0).exists(oldBackupFqn));
      assertTrue(caches.get(1).exists(oldBackupFqn));
      assertTrue(!caches.get(2).exists(oldBackupFqn));
      assertTrue(!caches.get(0).exists(newBackupFqn));
      assertTrue(!caches.get(1).exists(newBackupFqn));
      assertTrue(!caches.get(2).exists(newBackupFqn));


      replicationListener0.expect(DataGravitationCleanupCommand.class);
      replicationListener1.expect(DataGravitationCleanupCommand.class);

      txman.begin();

      caches.get(2).get(fqn, key);

      assertTrue(caches.get(0).exists(fqn));
      assertTrue(!caches.get(1).exists(fqn));
      assertTrue(caches.get(2).exists(fqn));
      assertTrue(!caches.get(0).exists(oldBackupFqn));
      assertTrue(caches.get(1).exists(oldBackupFqn));
      assertTrue(!caches.get(2).exists(oldBackupFqn));
      assertTrue(!caches.get(0).exists(newBackupFqn));
      assertTrue(!caches.get(1).exists(newBackupFqn));
      assertTrue(!caches.get(2).exists(newBackupFqn));

      txman.commit();

      replicationListener0.waitForReplicationToOccur();
      replicationListener1.waitForReplicationToOccur();
      assertTrue(!caches.get(0).exists(fqn));
      assertTrue(!caches.get(1).exists(fqn));
      assertTrue(caches.get(2).exists(fqn));
      assertTrue(!caches.get(0).exists(oldBackupFqn));
      assertTrue(!caches.get(1).exists(oldBackupFqn));
      assertTrue(!caches.get(2).exists(oldBackupFqn));
      assertTrue(caches.get(0).exists(newBackupFqn));
      assertTrue(!caches.get(1).exists(newBackupFqn));
      assertTrue(!caches.get(2).exists(newBackupFqn));

      assertNoLocks(caches);
   }

   public void testTransactionsRollback() throws Exception
   {
      TestingUtil.dumpCacheContents(caches.get(0));
      TestingUtil.dumpCacheContents(caches.get(1));
      TestingUtil.dumpCacheContents(caches.get(2));

      caches.get(0).put(fqn, key, value);
      Fqn oldBackupFqn = Fqn.fromString("/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + fqnTransformer.getGroupNameFromAddress(caches.get(0).getLocalAddress()) + "/test");
      Fqn newBackupFqn = Fqn.fromString("/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + fqnTransformer.getGroupNameFromAddress(caches.get(2).getLocalAddress()) + "/test");

      TransactionManager txman = caches.get(2).getTransactionManager();


      assertTrue(caches.get(0).exists(fqn));
      assertTrue(!caches.get(1).exists(fqn));
      assertTrue(!caches.get(2).exists(fqn));
      assertTrue(!caches.get(0).exists(oldBackupFqn));
      assertTrue(caches.get(1).exists(oldBackupFqn));
      assertTrue(!caches.get(2).exists(oldBackupFqn));
      assertTrue(!caches.get(0).exists(newBackupFqn));
      assertTrue(!caches.get(1).exists(newBackupFqn));
      assertTrue(!caches.get(2).exists(newBackupFqn));


      txman.begin();

      caches.get(2).get(fqn, key);

      assertTrue(caches.get(0).exists(fqn));
      assertTrue(!caches.get(1).exists(fqn));
      assertTrue(caches.get(2).exists(fqn));
      assertTrue(!caches.get(0).exists(oldBackupFqn));
      assertTrue(caches.get(1).exists(oldBackupFqn));
      assertTrue(!caches.get(2).exists(oldBackupFqn));
      assertTrue(!caches.get(0).exists(newBackupFqn));
      assertTrue(!caches.get(1).exists(newBackupFqn));
      assertTrue(!caches.get(2).exists(newBackupFqn));

      txman.rollback();

      assertTrue(caches.get(0).exists(fqn));
      assertTrue(!caches.get(1).exists(fqn));
      assertTrue(!caches.get(2).exists(fqn));
      assertTrue(!caches.get(0).exists(oldBackupFqn));
      assertTrue(caches.get(1).exists(oldBackupFqn));
      assertTrue(!caches.get(2).exists(oldBackupFqn));
      assertTrue(!caches.get(0).exists(newBackupFqn));
      assertTrue(!caches.get(1).exists(newBackupFqn));
      assertTrue(!caches.get(2).exists(newBackupFqn));

      assertNoLocks(caches);
   }

   public void testSubtreeRetrieval() throws Exception
   {
      Fqn fqn = Fqn.fromString("/test");
      Fqn fqn2 = Fqn.fromString("/test/subtree");

      Fqn backupFqn = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), fqn);
      Fqn backupFqn2 = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), fqn2);

      caches.get(0).put(fqn, key, value);
      caches.get(0).put(fqn2, key, value);

      // test backup replication to buddy
      assertEquals(value, caches.get(0).get(fqn, key));
      assertEquals(value, caches.get(0).get(fqn2, key));
      assertEquals(value, caches.get(1).get(backupFqn, key));
      assertEquals(value, caches.get(1).get(backupFqn2, key));

      assertTrue(!caches.get(0).exists(backupFqn));
      assertTrue(!caches.get(0).exists(backupFqn2));
      assertTrue(!caches.get(1).exists(fqn));
      assertTrue(!caches.get(1).exists(fqn2));
      assertTrue(!caches.get(2).exists(fqn));
      assertTrue(!caches.get(2).exists(fqn2));
      assertTrue(!caches.get(2).exists(backupFqn));
      assertTrue(!caches.get(2).exists(backupFqn2));

      assertNoLocks(caches);

      // gravitate to 2:
      inReplicationListeners(listeners).dataWillGravitateFrom(0).to(2);
      caches.get(2).getNode(fqn);  // expect entire subtree to gravitate.
      expectGravitation();

      Fqn newBackupFqn = fqnTransformer.getBackupFqn(caches.get(2).getLocalAddress(), fqn);
      Fqn newBackupFqn2 = fqnTransformer.getBackupFqn(caches.get(2).getLocalAddress(), fqn2);

      assertEquals(value, caches.get(2).get(fqn, key));
      assertTrue(caches.get(2).exists(fqn2));
      assertEquals(value, caches.get(0).get(newBackupFqn, key));
      assertTrue(caches.get(0).exists(newBackupFqn2));

      assertTrue(!caches.get(2).exists(newBackupFqn));
      assertTrue(!caches.get(2).exists(newBackupFqn2));
      assertTrue(!caches.get(0).exists(fqn));
      assertTrue(!caches.get(0).exists(fqn2));
      assertTrue(!caches.get(1).exists(fqn));
      assertTrue(!caches.get(1).exists(fqn2));
      assertTrue(!caches.get(1).exists(newBackupFqn));
      assertTrue(!caches.get(1).exists(newBackupFqn2));

      for (CacheSPI<Object, Object> cache : caches)
      {
         assertTrue(!cache.exists(backupFqn));
         assertTrue(!cache.exists(backupFqn2));
      }

      assertNoLocks(caches);
   }

   public void testStaleRegionOnDataOwner() throws Exception
   {
      // add some stuff on the primary
      CacheSPI first = caches.get(0);
      CacheSPI second = caches.get(1);
      CacheSPI third = caches.get(2);

      first.put(fqn, key, value);

      assert first.peek(fqn, false) != null : "Should have data";
      assert first.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(third.getLocalAddress())), false) != null : "Should have backup node for second";
      assert first.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(first.getLocalAddress())), false) == null : "Should NOT have backup node for self!";

      assert second.peek(fqn, false) == null : "Should not have data";
      assert second.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(second.getLocalAddress())), false) == null : "Should NOT have backup node for self!";
      assert second.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(first.getLocalAddress())), false) != null : "Should have backup node for second";
      assert second.peek(fqnTransformer.getBackupFqn(first.getLocalAddress(), fqn), false) != null : "Should have backup data";

      inReplicationListeners(listeners).dataWillGravitateFrom(0).to(1);
      // now do a gravitate call.
      assert second.get(fqn, key).equals(value) : "Data should have gravitated!";
      // gravitation cleanup calls are async.
      expectGravitation();

      assert second.peek(fqn, false) != null : "Should have data";
      assert second.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(first.getLocalAddress())), false) != null : "Should have backup node for second";
      assert second.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(second.getLocalAddress())), false) == null : "Should NOT have backup node for self!";

      assert third.peek(fqn, false) == null : "Should not have data";
      assert third.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(third.getLocalAddress())), false) == null : "Should NOT have backup node for self!";
      assert third.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(second.getLocalAddress())), false) != null : "Should have backup node for second";
      assert third.peek(fqnTransformer.getBackupFqn(second.getLocalAddress(), fqn), false) != null : "Should have backup data";
   }

   public void testStaleRegionOnBuddy() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b/c");
      Object key = "key", value = "value";

      // add some stuff on the primary
      CacheSPI dataOwner = caches.get(0);
      CacheSPI buddy = caches.get(1);
      CacheSPI thirdInstance = caches.get(2);

      assertIsBuddy(dataOwner, buddy, true);
      assertIsBuddy(buddy, thirdInstance, true);
      assertIsBuddy(thirdInstance, dataOwner, true);

      dataOwner.put(fqn, key, value);

      assert dataOwner.peek(fqn, false) != null : "Should have data";
      assert dataOwner.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(thirdInstance.getLocalAddress())), false) != null : "Should have backup node for buddy";
      assert dataOwner.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(dataOwner.getLocalAddress())), false) == null : "Should NOT have backup node for self!";
      assert dataOwner.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(buddy.getLocalAddress())), false) == null : "Should NOT have backup node for 2nd instance!";

      assert buddy.peek(fqn, false) == null : "Should not have data";
      assert buddy.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(buddy.getLocalAddress())), false) == null : "Should NOT have backup node for self!";
      assert buddy.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(dataOwner.getLocalAddress())), false) != null : "Should have backup node for buddy";
      assert buddy.peek(fqnTransformer.getBackupFqn(dataOwner.getLocalAddress(), fqn), false) != null : "Should have backup data";

      inReplicationListeners(listeners).dataWillGravitateFrom(0).to(2);
      // now do a gravitate call.
      assert thirdInstance.get(fqn, key).equals(value) : "Data should have gravitated!";
      expectGravitation();
      
      assert thirdInstance.peek(fqn, false) != null : "Should have data";
      assert thirdInstance.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(buddy.getLocalAddress())), false) != null : "Should have backup node for buddy";
      assert thirdInstance.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(thirdInstance.getLocalAddress())), false) == null : "Should NOT have backup node for self!";

      assert dataOwner.peek(fqn, false) == null : "Should not have data";
      assert dataOwner.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(dataOwner.getLocalAddress())), false) == null : "Should NOT have backup node for self!";
      assert dataOwner.peek(Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, fqnTransformer.getGroupNameFromAddress(thirdInstance.getLocalAddress())), false) != null : "Should have backup node for buddy";
      assert dataOwner.peek(fqnTransformer.getBackupFqn(thirdInstance.getLocalAddress(), fqn), false) != null : "Should have backup data";
      assert buddy.peek(fqn, false) == null : "Should not have data";
      assert buddy.peek(fqn.getParent(), false) == null : "Should not have any part of the data";
      assert buddy.peek(fqnTransformer.getBackupFqn(dataOwner.getLocalAddress(), fqn), false) == null : "Should NOT have backup data";
   }


   public void testConcurrency() throws Exception
   {
      Fqn f = Fqn.fromString("/a/b/c");
      String k = "k";
      String v = "v";

      for (int i = 0; i < 10; i++)
      {
         caches.get(0).put(Fqn.fromRelativeElements(f, i), k, v);
         assert v.equals(caches.get(1).get(Fqn.fromRelativeElements(f, i), k));
         assert v.equals(caches.get(1).get(Fqn.fromRelativeElements(f, i), k));
      }
   }


   @CacheListener
   public static class CacheBlockListener
   {
      private int blocks = 0;

      @CacheBlocked
      public void processBlock(Event e)
      {
         if (e.isPre())
         {
            synchronized (this)
            {
               blocks++;
               notifyAll();
            }
         }
      }

      @CacheUnblocked
      public void processUnblock(Event e)
      {
         if (e.isPre())
         {
            synchronized (this)
            {
               blocks--;
               notifyAll();
            }
         }
      }

      public void blockUntilAllCachesAreUnblocked(long maxWait) throws InterruptedException
      {
         synchronized (this)
         {
            if (blocks > 1)
            {
               wait(maxWait);
            }
            // try returning anyway?
            blocks = 0;
            /*
            if (blocks > 1)
               throw new RuntimeException("Timed out waiting for unblocks.  Number of blocks = " + blocks);
            if (blocks == 1) blocks = 0;
            */
         }
      }
   }

   @Test(enabled = true, dependsOnMethods = {"testConcurrency", "testStaleRegionOnBuddy", "testStaleRegionOnDataOwner",
         "testDataGravitationDontKillOwner", "testTransactionsCommit", "testTransactionsRollback",
         "testSubtreeRetrieval"})
   public void testCompleteStateSurvival() throws Exception
   {
      replicationListener1.expect(PutKeyValueCommand.class);
      caches.get(0).put("/0", "key", "value");
      replicationListener1.waitForReplicationToOccur();

      replicationListener2.expect(PutKeyValueCommand.class);
      caches.get(1).put("/1", "key", "value");
      replicationListener2.waitForReplicationToOccur();

      replicationListener0.expect(PutKeyValueCommand.class);
      caches.get(2).put("/2", "key", "value");
      replicationListener0.waitForReplicationToOccur();

      caches.get(2).stop();
      TestingUtil.blockUntilViewsReceived(60000, true, caches.get(0), caches.get(1));

      assertEquals("value", caches.get(0).get("/2", "key"));

      caches.get(1).stop();

      // cache[0] is all thats left!!

      assertEquals("value", caches.get(0).get("/0", "key"));

      try
      {
         assertEquals("value", caches.get(0).get("/1", "key"));
      }
      catch (RuntimeException e)
      {
         // may barf the first time around since we are unable to contact our buddy and store this data.
         assertEquals(IllegalArgumentException.class, e.getCause().getClass());
      }

      // now try the assertion again since the local gravitation would have worked.

      assertEquals("value", caches.get(0).get("/1", "key"));
      assertEquals("value", caches.get(0).get("/2", "key"));
      caches.get(0).stop();

      //uncoment this if you want to run this test more than once
//      caches = createCaches(3, false, true);
//      replicationListener0 = ReplicationListener.getReplicationListener(caches.get(0));
//      replicationListener1 = ReplicationListener.getReplicationListener(caches.get(1));
//      replicationListener2 = ReplicationListener.getReplicationListener(caches.get(2));
//      waitForSingleBuddy(caches);
   }

}
