package org.jboss.cache.api.pfer;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.api.mvcc.LockAssert;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.util.TestingUtil;

public abstract class PFERMVCCTestBase extends PutForExternalReadTestBase
{
   protected PFERMVCCTestBase()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }

   @Override
   protected void assertLocked(Fqn fqn, CacheSPI cache, boolean writeLocked)
   {
      if (!writeLocked) return; // MVCC only does write locks.
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache);
      LockAssert.assertLocked(fqn, cr.getComponent(LockManager.class), cr.getComponent(InvocationContextContainer.class));
   }
}