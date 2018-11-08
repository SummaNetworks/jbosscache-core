package org.jboss.cache.api.mvcc.repeatable_read;

import org.jboss.cache.api.CacheAPITest;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;


/**
 * MVCC version of {@link org.jboss.cache.api.CacheAPITest}
 */
@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.repeatable_read.CacheAPIMVCCTest")
public class CacheAPIMVCCTest extends CacheAPITest
{
   @Override
   protected void configure(Configuration c)
   {
      super.configure(c);
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
   }

   @Override
   protected NodeLockingScheme getNodeLockingScheme()
   {
      return NodeLockingScheme.MVCC;
   }

   public void testWriteSkewOnRemovalOfNullNode() throws Exception
   {
      TransactionManager tm = cache.getTransactionManager();
      tm.begin();
      cache.getNode("/a");
      Transaction tx = tm.suspend();
      cache.put("/a", "k", "v2");
      assert cache.get("/a", "k").equals("v2");
      tm.resume(tx);
      cache.removeNode("/a");
      tx.commit();
      assert cache.getNode("/a") == null; // this fails
   }
}