package org.jboss.cache.api.mvcc.read_committed;

import org.jboss.cache.api.DeletedChildResurrectionTest;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.read_committed.DeletedChildResurrectionMvccTest")
public class DeletedChildResurrectionMvccTest extends DeletedChildResurrectionTest
{
   public DeletedChildResurrectionMvccTest()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }

   @Override
   protected void configure(Configuration c)
   {
      c.setIsolationLevel(IsolationLevel.READ_COMMITTED);
   }
}