package org.jboss.cache.buddyreplication.mvcc;

import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "buddyreplication.mvcc.BuddyAssignmentStateTransferTest")
public class BuddyAssignmentStateTransferTest extends org.jboss.cache.buddyreplication.BuddyAssignmentStateTransferTest
{
   @Override
   protected NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return NodeLockingScheme.MVCC;
   }
}
