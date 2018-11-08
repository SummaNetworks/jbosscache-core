package org.jboss.cache.buddyreplication.mvcc;

import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "buddyreplication.mvcc.Buddy2NodesBackupActivationInactivationTest")
public class Buddy2NodesBackupActivationInactivationTest extends org.jboss.cache.buddyreplication.Buddy2NodesBackupActivationInactivationTest
{
   @Override
   protected NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return NodeLockingScheme.MVCC;
   }
}
