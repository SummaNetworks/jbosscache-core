package org.jboss.cache.lock;

import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

@Test(groups = {"unit", "mvcc"}, testName = "lock.MVCCLockManagerNoTxTest")
public class MVCCLockManagerNoTxTest extends MVCCLockManagerTest
{
   @Override
   protected TransactionManager getTransactionManager()
   {
      return null; // force the use of JDK ReentrantLocks.
   }
}
