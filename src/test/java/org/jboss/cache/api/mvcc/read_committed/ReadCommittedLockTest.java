package org.jboss.cache.api.mvcc.read_committed;

import org.jboss.cache.api.mvcc.LockTestBase;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.read_committed.ReadCommittedLockTest")
public class ReadCommittedLockTest extends LockTestBase
{
   public ReadCommittedLockTest()
   {
      repeatableRead = false;
   }
}
