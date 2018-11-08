package org.jboss.cache.api.mvcc.read_committed;

import org.jboss.cache.api.CacheAPITest;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;


/**
 * MVCC version of {@link org.jboss.cache.api.CacheAPITest}
 */
@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.read_committed.CacheAPIMVCCTest")
public class CacheAPIMVCCTest extends CacheAPITest
{
   @Override
   protected void configure(Configuration c)
   {
      super.configure(c);
      c.setIsolationLevel(IsolationLevel.READ_COMMITTED);
   }

   @Override
   protected NodeLockingScheme getNodeLockingScheme()
   {
      return NodeLockingScheme.MVCC;
   }
}