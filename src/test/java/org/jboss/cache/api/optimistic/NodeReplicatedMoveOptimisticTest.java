/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.api.optimistic;

import org.jboss.cache.api.NodeReplicatedMoveTest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "optimistic"}, testName = "api.optimistic.NodeReplicatedMoveOptimisticTest")
public class NodeReplicatedMoveOptimisticTest extends NodeReplicatedMoveTest
{
   public NodeReplicatedMoveOptimisticTest()
   {
      nodeLockingScheme = NodeLockingScheme.OPTIMISTIC;
   }
}
