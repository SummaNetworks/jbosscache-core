package org.jboss.cache.api.optimistic;

import org.jboss.cache.api.DeletedChildResurrectionTest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "optimistic"}, testName = "api.optimistic.DeletedChildResurrectionOptimisticTest")
public class DeletedChildResurrectionOptimisticTest extends DeletedChildResurrectionTest
{
   public DeletedChildResurrectionOptimisticTest()
   {
      nodeLockingScheme = NodeLockingScheme.OPTIMISTIC;
   }
}
