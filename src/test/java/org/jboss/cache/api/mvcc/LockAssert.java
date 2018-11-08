package org.jboss.cache.api.mvcc;

import org.jboss.cache.Fqn;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.concurrent.locks.LockContainer;

/**
 * Helper class to assert lock status in MVCC
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 3.0
 */
public class LockAssert
{
   public static void assertLocked(Fqn fqn, LockManager lockManager, InvocationContextContainer icc)
   {
      assert lockManager.isLocked(fqn) : fqn + " not locked!";
      assert icc.get().getLocks().contains(fqn) : "Lock not recorded for " + fqn;
   }

   public static void assertNotLocked(Fqn fqn, LockManager lockManager, InvocationContextContainer icc)
   {
      // can't rely on the negative test since other nodes may share the same lock with lock striping.
//      assert !lockManager.isLocked(fqn) : fqn + " is locked!";
      assert !icc.get().getLocks().contains(fqn) : fqn + " lock recorded!";
   }

   public static void assertNoLocks(LockManager lockManager, InvocationContextContainer icc)
   {
      LockContainer lc = (LockContainer) TestingUtil.extractField(lockManager, "lockContainer");
      assert lc.getNumLocksHeld() == 0 : "Stale locks exist! NumLocksHeld is " + lc.getNumLocksHeld() + " and lock info is " + lockManager.printLockInfo();
      assert icc.get().getLocks().isEmpty() : "Stale (?) locks recorded! " + icc.get().getLocks();
   }
}
