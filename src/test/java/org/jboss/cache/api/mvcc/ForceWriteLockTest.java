package org.jboss.cache.api.mvcc;

import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.lock.LockType;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.ForceWriteLockTest")
public class ForceWriteLockTest extends org.jboss.cache.options.ForceWriteLockTest
{
   public ForceWriteLockTest()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }

   @Override
   protected void assertNotLocked(Fqn fqn)
   {
      assert !TestingUtil.extractLockManager(cache).isLocked(cache.peek(fqn, true)) : "Node " + fqn + " is locked!!";
   }

   @Override
   protected void assertLocked(Object owner, Fqn fqn, boolean write_locked)
   {
      if (write_locked)
      {
         LockManager lm = TestingUtil.extractLockManager(cache);
         NodeSPI<String, String> n = cache.peek(fqn, true);
         if (owner == null) owner = Thread.currentThread();
         assertTrue("node " + fqn + " is not locked", lm.isLocked(n));
         assertTrue("node " + fqn + " is not write-locked by owner " + owner, lm.ownsLock(fqn, LockType.WRITE, owner));
      }
   }
}
