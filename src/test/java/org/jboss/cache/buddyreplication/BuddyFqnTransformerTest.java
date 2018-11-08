package org.jboss.cache.buddyreplication;

import org.jboss.cache.Fqn;
import org.testng.annotations.Test;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.2.0
 */
@Test(groups = "unit", testName = "buddyreplication.BuddyFqnTransformerTest")
public class BuddyFqnTransformerTest
{
   private BuddyFqnTransformer buddyFqnTransformer = new BuddyFqnTransformer();

   public void testActualFqn()
   {
      Fqn backupFqn = Fqn.fromString("/_BUDDY_BACKUP_/1.2.3.4_5678/a/b/c/d");
      assert buddyFqnTransformer.getActualFqn(backupFqn).equals(Fqn.fromString("/a/b/c/d"));

      backupFqn = Fqn.fromString("/_BUDDY_BACKUP_/1.2.3.4_5678");

      Fqn actual = buddyFqnTransformer.getActualFqn(backupFqn);

      assert actual.equals(Fqn.ROOT);
   }

   public void testBackupRootFqn()
   {
      Fqn backupFqn = Fqn.fromString("/_BUDDY_BACKUP_/1.2.3.4_5678/a/b/c/d");
      assert buddyFqnTransformer.getBackupRootFromFqn(backupFqn).equals(Fqn.fromString("/_BUDDY_BACKUP_/1.2.3.4_5678")) : "Got " + buddyFqnTransformer.getBackupRootFromFqn(backupFqn);
   }

   public void testGetActualFqnOnBuddyBackupRoot()
   {
      assert Fqn.ROOT == buddyFqnTransformer.getActualFqn(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN);
   }

   public void testBackupRootFromFqnOnBuddyBackupRoot()
   {
      assert Fqn.ROOT == buddyFqnTransformer.getBackupRootFromFqn(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN);
   }

   public void testGetActualFqnOnDeadBackup()
   {
      Fqn deadBackup = Fqn.fromString("/_BUDDY_BACKUP_/1.2.3.4_5678:DEAD/5/a/b/c/d");
      Fqn actualFqn = Fqn.fromString("/a/b/c/d");
      assert actualFqn.equals(buddyFqnTransformer.getActualFqn(deadBackup));
   }

   public void testGetActualFqnOnIncompleteDeadBackup()
   {
      Fqn deadBackup = Fqn.fromString("/_BUDDY_BACKUP_/1.2.3.4_5678:DEAD");
      Fqn actualFqn = Fqn.ROOT;
      assert actualFqn.equals(buddyFqnTransformer.getActualFqn(deadBackup));
   }
}
