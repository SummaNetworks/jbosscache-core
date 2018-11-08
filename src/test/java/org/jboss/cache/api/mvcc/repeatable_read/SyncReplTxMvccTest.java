package org.jboss.cache.api.mvcc.repeatable_read;

import org.jboss.cache.api.SyncReplTxTest;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

@Test(groups = {"functional", "jgroups", "transaction", "mvcc"}, testName = "api.mvcc.repeatable_read.SyncReplTxMvccTest")
public class SyncReplTxMvccTest extends SyncReplTxTest
{
   public SyncReplTxMvccTest()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }

   @Override
   protected void configure(Configuration c)
   {
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
   }
}
