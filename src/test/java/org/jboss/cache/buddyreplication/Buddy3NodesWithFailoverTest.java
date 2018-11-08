/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.remote.DataGravitationCleanupCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.jgroups.JChannel;
import org.jgroups.Address;
import org.jgroups.protocols.DISCARD;
import static org.testng.AssertJUnit.*;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Tests behaviour when data owners fail - essentially this tests data gravitation
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = "functional", testName = "buddyreplication.Buddy3NodesWithFailoverTest")
public class Buddy3NodesWithFailoverTest extends BuddyReplicationTestsBase
{
   protected boolean optimisticLocks = false;
   protected String key = "key";
   protected String value = "value";
   BuddyFqnTransformer fqnTransformer = new BuddyFqnTransformer();

   public void testSubtreeRetrieval() throws Exception
   {
      List<CacheSPI<Object, Object>> caches = createCaches(3, false, true, optimisticLocks);
      cachesTL.set(caches);

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
      ReplicationListener replListener0 = ReplicationListener.getReplicationListener(caches.get(0));
      ReplicationListener replListener1 = ReplicationListener.getReplicationListener(caches.get(1));
      replListener0.expect(DataGravitationCleanupCommand.class);
      replListener1.expect(DataGravitationCleanupCommand.class);
      caches.get(2).getNode(fqn);  // expectWithTx entire subtree to gravitate.
      replListener0.waitForReplicationToOccur(); // cleanup commands are async
      replListener1.waitForReplicationToOccur(); // also wait untill the backup is cleaned

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

      TestingUtil.dumpCacheContents(caches);

      for (CacheSPI<Object, Object> cache : caches)
      {
         assertTrue(!cache.exists(backupFqn));
         assertTrue(!cache.exists(backupFqn2));
      }

      assertNoLocks(caches);
   }

   public void testDataGravitationKillOwner() throws Exception
   {
      List<CacheSPI<Object, Object>> caches = createCaches(3, false, true);
      Fqn fqn = Fqn.fromString("/test");
      Fqn backupFqn = fqnTransformer.getBackupFqn(caches.get(2).getLocalAddress(), fqn);

      TestingUtil.dumpCacheContents(caches);

      ReplicationListener replListener0 = ReplicationListener.getReplicationListener(caches.get(0));

      replListener0.expect(PutKeyValueCommand.class);
      caches.get(2).put(fqn, key, value);
      replListener0.waitForReplicationToOccur();


      TestingUtil.dumpCacheContents(caches);

      assertEquals("Value should exist", value, caches.get(2).get(fqn, key));

      TestingUtil.dumpCacheContents(caches);

      // use exists instead of get() to prevent going up the interceptor stack
      assertTrue("Should be false", !caches.get(0).exists(fqn));
      assertTrue("Should be false", !caches.get(1).exists(fqn));

      assertTrue("Value be true", caches.get(0).exists(backupFqn));
      assertFalse("Should be false", caches.get(1).exists(backupFqn));
      assertFalse("Should be false", caches.get(2).exists(backupFqn));

      Address cache2Addr = caches.get(2).getLocalAddress();
      // forcefully kill data owner.
      caches.get(2).stop();
      caches.get(2).destroy();
      TestingUtil.blockUntilViewsReceived(10000, caches.get(0), caches.get(1));
      waitForSingleBuddy(caches.get(0), caches.get(1));

//      TestingUtil.dumpCacheContents(cachePool);
      // assert that the remaining cachePool have picked new buddies.  Cache 1 should have cache 2's backup data.
      assert caches.get(0).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) != null : "Should have new buddy's backup root.";
      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) == null : "Should not have self as a backup root.";
      assert caches.get(0).peek(fqnTransformer.getBackupRoot(cache2Addr), false) == null : "Should not have dead node as a backup root.";
      assert caches.get(0).peek(Fqn.fromRelativeElements(fqnTransformer.getDeadBackupRoot(cache2Addr), 1), false) != null : "Should have dead node as a defunct backup root.";

      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) == null : "Should not have self as a backup root.";
      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(0).getLocalAddress()), false) != null : "Should have new buddy's backup root.";
      assert caches.get(1).peek(fqnTransformer.getBackupRoot(cache2Addr), false) == null : "Should not have dead node as a backup root.";
      assert caches.get(1).peek(Fqn.fromRelativeElements(fqnTransformer.getDeadBackupRoot(cache2Addr), 1), false) == null : "Should not have dead node as a defunct backup root.";


      replListener0.expect(DataGravitationCleanupCommand.class);

      // according to data gravitation, a call to *any* cache should retrieve the data, and move the data to the new cache.
      assertEquals("Value should have gravitated", value, caches.get(1).get(fqn, key));
      replListener0.waitForReplicationToOccur();

      // use exists instead of get() to prevent going up the interceptor stack
      assert caches.get(0).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) != null : "Should have new buddy's backup root.";
      assert caches.get(0).peek(fqnTransformer.getBackupRoot(caches.get(0).getLocalAddress()), false) == null : "Should not have self as a backup root.";
      assert caches.get(0).peek(fqnTransformer.getBackupRoot(cache2Addr), false) == null : "Should not have dead node as a backup root.";
      assert caches.get(0).peek(Fqn.fromRelativeElements(fqnTransformer.getDeadBackupRoot(cache2Addr), 1), false) == null : "Should not have dead node as a defunct backup root.";
      assert caches.get(0).peek(fqnTransformer.getDeadBackupRoot(cache2Addr), false) == null : "Should not have dead node as a defunct backup root.";

      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(1).getLocalAddress()), false) == null : "Should not have self as a backup root.";
      assert caches.get(1).peek(fqnTransformer.getBackupRoot(caches.get(0).getLocalAddress()), false) != null : "Should have new buddy's backup root.";
      assert caches.get(1).peek(fqnTransformer.getBackupRoot(cache2Addr), false) == null : "Should not have dead node as a backup root.";
      assert caches.get(1).peek(Fqn.fromRelativeElements(fqnTransformer.getDeadBackupRoot(cache2Addr), 1), false) == null : "Should not have dead node as a defunct backup root.";
      assertTrue("Should be false", !caches.get(0).exists(fqn));

      // the old backup should no longer exist
      assertFalse("Should be null", caches.get(0).exists(backupFqn));
      assertFalse("Should be null", caches.get(1).exists(backupFqn));
   }
}
