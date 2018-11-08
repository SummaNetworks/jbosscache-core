package org.jboss.cache.api.pfer;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.NodeLock;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.transaction.Transaction;

public abstract class PFERPessimisticTestBase extends PutForExternalReadTestBase
{
   protected PFERPessimisticTestBase()
   {
      nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;
   }

   @Override
   protected void assertLocked(Fqn fqn, CacheSPI cache, boolean writeLocked)
   {
      NodeLock lock = cache.peek(fqn, true, true).getLock();
      assertTrue("node " + fqn + " is not locked", lock.isLocked());
      if (writeLocked)
      {
         assertTrue("node " + fqn + " is not write-locked" + (lock.isReadLocked() ? " but is read-locked instead!" : "!"), lock.isWriteLocked());
      }
      else
      {
         assertTrue("node " + fqn + " is not read-locked" + (lock.isWriteLocked() ? " but is write-locked instead!" : "!"), lock.isReadLocked());
      }
   }

   private static Log log = LogFactory.getLog(PFERPessimisticTestBase.class);

   /**
    * Locks could only occur on the parent node is write locked since if the child node exists it is a no-op anyway.
    * If the parent node is read locked as well, there is no issue.
    */
   public void testNoOpWhenLockedAnd0msTimeout() throws Exception
   {
      log.warn("******** Here is where it is not going to happen");
      // create the parent node first ...
      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(parentFqn, key, value);
      try
      {
         replListener2.waitForReplicationToOccur(10000);
      } finally
      {
         log.warn("******** Here is where it did not happen!!!");
      }

      replListener2.expectWithTx(PutKeyValueCommand.class);
      tm1.begin();
      cache1.put(parentFqn, key, value2);

      Transaction t = tm1.suspend();

      assertLocked(parentFqn, cache1, true);

      // parentFqn should be write-locked.
      long startTime = System.currentTimeMillis();
      cache1.putForExternalRead(fqn, key, value);

      long waited = System.currentTimeMillis() - startTime;
      // crappy way to test that pFER does not block, but it is effective.
      assertTrue("Should not wait " + waited + " millis for lock timeout, should attempt to acquire lock with 0ms!", waited < cache1.getConfiguration().getLockAcquisitionTimeout());
      // should not block.

      tm1.resume(t);
      tm1.commit();

      replListener2.waitForReplicationToOccur(1000);

      assertEquals("Parent node write should have succeeded", value2, cache1.get(parentFqn, key));
      if (isUsingInvalidation())
         assertNull("Parent node write should have invalidated", cache2.get(parentFqn, key));
      else
         assertEquals("Parent node write should have replicated", value2, cache2.get(parentFqn, key));

      assertNull("PFER should have been a no-op", cache1.get(fqn, key));
      assertNull("PFER should have been a no-op", cache2.get(fqn, key));
   }
}