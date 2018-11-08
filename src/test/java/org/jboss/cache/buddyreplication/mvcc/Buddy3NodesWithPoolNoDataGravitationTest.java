package org.jboss.cache.buddyreplication.mvcc;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "buddyreplication.mvcc.Buddy3NodesWithPoolNoDataGravitationTest")
public class Buddy3NodesWithPoolNoDataGravitationTest extends org.jboss.cache.buddyreplication.Buddy3NodesWithPoolNoDataGravitationTest
{
   @Override
   protected Configuration.NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return Configuration.NodeLockingScheme.MVCC;
   }
   
}
