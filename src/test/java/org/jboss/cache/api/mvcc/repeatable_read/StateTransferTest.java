package org.jboss.cache.api.mvcc.repeatable_read;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.statetransfer.StateTransfer200Test;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.repeatable_read.StateTransferTest")
public class StateTransferTest extends StateTransfer200Test
{
   @Override
   protected void additionalConfiguration(Configuration c)
   {
      c.setNodeLockingScheme(NodeLockingScheme.MVCC);
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
   }
}