package org.jboss.cache.api.nodevalidity;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.nodevalidity.LocalMvccNodeValidityTest")
public class LocalMvccNodeValidityTest extends LocalPessNodeValidityTest
{
   public LocalMvccNodeValidityTest()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }

   @Override
   protected void nodeLockingSchemeSpecificSetup(Configuration c)
   {
      c.setNodeLockingScheme(nodeLockingScheme);
      c.setIsolationLevel(IsolationLevel.READ_COMMITTED);
   }
}
