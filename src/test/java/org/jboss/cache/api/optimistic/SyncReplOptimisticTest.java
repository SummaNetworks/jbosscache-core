package org.jboss.cache.api.optimistic;

import org.jboss.cache.api.SyncReplTest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "jgroups", "optimistic"}, testName = "api.optimistic.SyncReplOptimisticTest")
public class SyncReplOptimisticTest extends SyncReplTest
{
   public SyncReplOptimisticTest()
   {
      nodeLockingScheme = NodeLockingScheme.OPTIMISTIC;
   }
}
