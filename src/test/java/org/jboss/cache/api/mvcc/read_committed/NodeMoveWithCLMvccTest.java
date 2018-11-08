package org.jboss.cache.api.mvcc.read_committed;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.api.NodeMoveAPIWithCLTest;
import org.testng.annotations.Test;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.read_committed.NodeMoveWithCLMvccTest")
public class NodeMoveWithCLMvccTest extends NodeMoveAPIWithCLTest
{
   @Override
   protected void configure(Configuration c)
   {
      c.setIsolationLevel(IsolationLevel.READ_COMMITTED);
   }

}
