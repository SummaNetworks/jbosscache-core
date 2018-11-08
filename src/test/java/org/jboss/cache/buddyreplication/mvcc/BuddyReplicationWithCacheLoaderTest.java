package org.jboss.cache.buddyreplication.mvcc;

import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "buddyreplication.mvcc.BuddyReplicationWithCacheLoaderTest")
public class BuddyReplicationWithCacheLoaderTest extends org.jboss.cache.buddyreplication.BuddyReplicationWithCacheLoaderTest
{
   @Override
   protected NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return NodeLockingScheme.MVCC;
   }
}
