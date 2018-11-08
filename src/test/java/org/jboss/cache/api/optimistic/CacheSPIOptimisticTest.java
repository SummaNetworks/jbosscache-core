package org.jboss.cache.api.optimistic;

import org.jboss.cache.api.CacheSPITest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "optimistic"}, testName = "api.optimistic.CacheSPIOptimisticTest")
public class CacheSPIOptimisticTest extends CacheSPITest
{
   public CacheSPIOptimisticTest()
   {
      nodeLockingScheme = NodeLockingScheme.OPTIMISTIC;
   }
}
