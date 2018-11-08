package org.jboss.cache.buddyreplication.mvcc;

import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "buddyreplication.mvcc.BuddyReplicationWithPassivationTest")
public class BuddyReplicationWithPassivationTest extends org.jboss.cache.buddyreplication.BuddyReplicationWithPassivationTest
{
   @Override
   protected NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return NodeLockingScheme.MVCC;
   }
}
