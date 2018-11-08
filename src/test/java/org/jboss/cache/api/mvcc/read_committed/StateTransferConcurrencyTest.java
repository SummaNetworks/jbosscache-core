package org.jboss.cache.api.mvcc.read_committed;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.read_committed.StateTransferConcurrencyTest")
public class StateTransferConcurrencyTest extends org.jboss.cache.statetransfer.StateTransferConcurrencyTest
{
   @Override
   protected void additionalConfiguration(Configuration c)
   {
      c.setNodeLockingScheme(NodeLockingScheme.MVCC);
      c.setIsolationLevel(IsolationLevel.READ_COMMITTED);
   }
}
