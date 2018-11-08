package org.jboss.cache.api.optimistic;

import org.testng.annotations.Test;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.api.NodeMoveAPIWithCLTest;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = {"functional", "optimistic"}, testName = "api.optimistic.NodeMoveAPIWithCLOptimisticTest")
public class NodeMoveAPIWithCLOptimisticTest extends NodeMoveAPIWithCLTest
{
   protected Configuration.NodeLockingScheme getNodeLockingScheme()
   {
      return Configuration.NodeLockingScheme.OPTIMISTIC;
   }

}
