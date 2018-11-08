package org.jboss.cache.buddyreplication.mvcc;

import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.buddyreplication.Buddy3NodesWithFailoverTest;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "buddyreplication.mvcc.MvccBuddy3NodesWithFailoverTest")
public class MvccBuddy3NodesWithFailoverTest extends Buddy3NodesWithFailoverTest
{
   @Override
   protected NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return NodeLockingScheme.MVCC;
   }
}
