package org.jboss.cache.profiling;

import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.testng.annotations.Test;

/**
 * Slave to go with ProfileTest.  Should be done in a different VM.  Can be profiled as well to profile receiving
 * messages.
 * Importnat - make sure you inly enable these tests locally!
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "profiling", testName = "profiling.ProfileSlaveTest", enabled = false)
public class ProfileSlaveTest extends AbstractProfileTest
{
   private void waitForTest() throws Exception
   {
      System.out.println("Slave listening for remote connections.  Hit Enter when done.");
      System.in.read();
   }

   @Test(enabled = false)
   public void testReplSync() throws Exception
   {
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.MVCC);
      cache.getConfiguration().setExposeManagementStatistics(true);
      cache.start();
      waitForTest();
   }

   @Test(enabled = false)
   public void testReplAsync() throws Exception
   {
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.MVCC);
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.REPL_ASYNC);
      cache.getConfiguration().setConcurrencyLevel(5000);
      cache.getConfiguration().setClusterConfig(getJGroupsConfig());
      cache.start();
      waitForTest();
   }

   @Test(enabled = false)
   public void testReplSyncOptimistic() throws Exception
   {
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.start();
      waitForTest();
   }

   @Test(enabled = false)
   public void testReplAsyncOptimistic() throws Exception
   {
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.REPL_ASYNC);
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.start();
      waitForTest();
   }

   @Test(enabled = false)
   public void testReplSyncBR() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);
      testReplSync();
   }

   @Test(enabled = false)
   public void testReplAsyncBR() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.MVCC);
      cache.getConfiguration().setConcurrencyLevel(500);
      cache.getConfiguration().setIsolationLevel(IsolationLevel.READ_COMMITTED);
//      cache.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);      
      testReplAsync();
   }

   @Test(enabled = false)
   public void testReplSyncOptBR() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);
      testReplSyncOptimistic();
   }

   @Test(enabled = false)
   public void testReplAsyncOptBR() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);
      testReplAsyncOptimistic();
   }

   @Test(enabled = false)
   public void testStateTransfer() throws Exception
   {
      throw new Exception("Implement me");
   }

   @Test(enabled = false)
   public void testStartup() throws Exception
   {
      throw new Exception("Implement me");
   }

   @Test(enabled = false)
   public void testCacheLoading() throws Exception
   {
      throw new Exception("Implement me");
   }

   @Test(enabled = false)
   public void testPassivation() throws Exception
   {
      throw new Exception("Implement me");
   }
}
