package org.jboss.cache.api.mvcc.repeatable_read;

import org.jboss.cache.api.SyncReplTest;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

@Test(groups = {"functional", "jgroups", "mvcc"}, testName = "api.mvcc.repeatable_read.SyncReplMvccTest")
public class SyncReplMvccTest extends SyncReplTest
{
   public SyncReplMvccTest()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }

   @Override
   protected void configure(Configuration c)
   {
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
   }
}
