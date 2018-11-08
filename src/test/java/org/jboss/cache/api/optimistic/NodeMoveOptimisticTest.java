/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.api.optimistic;

import org.jboss.cache.api.NodeMoveAPITest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "optimistic"}, testName = "api.optimistic.NodeMoveOptimisticTest")
public class NodeMoveOptimisticTest extends NodeMoveAPITest
{
   public NodeMoveOptimisticTest()
   {
      nodeLockingScheme = NodeLockingScheme.OPTIMISTIC;
   }

   @Override
   protected boolean isOptimistic()
   {
      return true;
   }

   @Override
   public void testLocks()
   {
      // no op
   }

   @Override
   public void testLocksDeepMove()
   {
      // no op
   }

   @Override
   public void testConcurrency()
   {
      // no op
   }
}
