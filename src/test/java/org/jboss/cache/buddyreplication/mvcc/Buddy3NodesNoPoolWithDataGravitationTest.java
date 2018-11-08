package org.jboss.cache.buddyreplication.mvcc;

import org.testng.annotations.Test;
import org.jboss.cache.config.Configuration;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "buddyreplication.mvcc.Buddy3NodesNoPoolWithDataGravitationTest")
public class Buddy3NodesNoPoolWithDataGravitationTest extends org.jboss.cache.buddyreplication.Buddy3NodesNoPoolWithDataGravitationTest 
{
   @Override
   protected Configuration.NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return Configuration.NodeLockingScheme.MVCC;
   }

}
