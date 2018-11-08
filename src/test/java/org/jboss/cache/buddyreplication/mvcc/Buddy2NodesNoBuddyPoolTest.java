package org.jboss.cache.buddyreplication.mvcc;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

/**
 * 
 * @author Mircea.Markus@jboss.com
 */
@Test (groups = "functional", testName = "buddyreplication.mvcc.Buddy2NodesNoBuddyPoolTest")
public class Buddy2NodesNoBuddyPoolTest extends org.jboss.cache.buddyreplication.Buddy2NodesNoBuddyPoolTest
{
   @Override
   protected Configuration.NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return Configuration.NodeLockingScheme.MVCC;
   }

}
