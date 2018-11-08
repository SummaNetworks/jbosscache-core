package org.jboss.cache.api.optimistic;

import org.jboss.cache.api.SyncReplTxTest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "jgroups", "transaction", "optimistic"}, testName = "api.optimistic.SyncReplTxOptimisticTest")
public class SyncReplTxOptimisticTest extends SyncReplTxTest
{
   public SyncReplTxOptimisticTest()
   {
      nodeLockingScheme = NodeLockingScheme.OPTIMISTIC;
   }
}
