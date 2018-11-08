package org.jboss.cache.api.mvcc.read_committed;

import org.jboss.cache.api.SyncReplTest;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

@Test(groups = {"functional", "jgroups", "mvcc"}, testName = "api.mvcc.read_committed.SyncReplMvccTest")
public class SyncReplMvccTest extends SyncReplTest
{
   public SyncReplMvccTest()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }

   @Override
   protected void configure(Configuration c)
   {
      c.setIsolationLevel(IsolationLevel.READ_COMMITTED);
   }
}