package org.jboss.cache.buddyreplication.mvcc;

import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "buddyreplication.mvcc.GravitationCleanupTest")
public class GravitationCleanupTest extends org.jboss.cache.buddyreplication.GravitationCleanupTest
{
   @Override
   protected NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return NodeLockingScheme.MVCC;
   }
}
