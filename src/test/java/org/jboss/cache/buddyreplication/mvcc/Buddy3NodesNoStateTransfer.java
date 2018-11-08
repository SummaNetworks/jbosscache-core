package org.jboss.cache.buddyreplication.mvcc;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "buddyreplication.mvcc.Buddy3NodesNoStateTransfer")
public class Buddy3NodesNoStateTransfer extends org.jboss.cache.buddyreplication.Buddy3NodesNoStateTransfer
{
   @Override
   protected Configuration.NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return Configuration.NodeLockingScheme.MVCC;
   }

}
