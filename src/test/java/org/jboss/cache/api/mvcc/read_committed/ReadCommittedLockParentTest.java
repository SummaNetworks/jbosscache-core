package org.jboss.cache.api.mvcc.read_committed;

import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.read_committed.ReadCommittedLockParentTest")
public class ReadCommittedLockParentTest extends ReadCommittedLockTest
{
   public ReadCommittedLockParentTest()
   {
      lockParentForChildInsertRemove = true;
   }

   @Override
   public void testOverwritingOnInsert()
   {
      // no op since a locked parent makes this test irrelevant.
   }

   @Override
   public void testOverwritingOnInsert2()
   {
      // no op since a locked parent makes this test irrelevant.
   }

   @Override
   public void testOverwritingOnInsert3()
   {
      // no op since a locked parent makes this test irrelevant.
   }

   @Override
   public void testConcurrentInsertRemove1()
   {
      // no op since a locked parent makes this test irrelevant.
   }

   @Override
   public void testConcurrentInsertRemove2()
   {
      // no op since a locked parent makes this test irrelevant.
   }
}
