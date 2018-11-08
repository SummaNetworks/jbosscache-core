package org.jboss.cache.api.mvcc.repeatable_read;

import org.jboss.cache.api.DeletedChildResurrectionTest;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.repeatable_read.DeletedChildResurrectionMvccTest")
public class DeletedChildResurrectionMvccTest extends DeletedChildResurrectionTest
{
   public DeletedChildResurrectionMvccTest()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }

   @Override
   protected void configure(Configuration c)
   {
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
   }
}
