package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.remote.DataGravitationCleanupCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.CachePrinter;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.List;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", testName = "buddyreplication.GravitationCleanupTest")
public class GravitationCleanupTest extends BuddyReplicationTestsBase
{
   Fqn fqn = Fqn.fromString("/a/b/c");
   Object key = "key", value = "value";
   BuddyFqnTransformer fqnTransformer = new BuddyFqnTransformer();

   /**
    * UT for https://jira.jboss.org/jira/browse/JBCACHE-1445.
    */
   public void testDataGravitationCleanup1Pc() throws Exception
   {
      CacheSPI cache0 = createCache(1, null, true, false);
      cache0.getConfiguration().setCacheMode(Configuration.CacheMode.REPL_ASYNC);
      CacheSPI cache1 = createCache(1, null, true, false);
      cache1.getConfiguration().setCacheMode(Configuration.CacheMode.REPL_ASYNC);
      cache0.start();
      cache1.start();
      try
      {
         waitForSingleBuddy(cache0, cache1);

         Fqn fqn = Fqn.fromString("/a/b/c");

         assert  null == cache0.get(fqn, "k");
         cache0.put(fqn, "key", "val");

         ReplicationListener replicationListener = ReplicationListener.getReplicationListener(cache0);
         replicationListener.expect(DataGravitationCleanupCommand.class);
         TransactionManager transactionManager = cache1.getTransactionManager();

         transactionManager.begin();
         assert cache1.get(fqn, "key").equals("val");
         transactionManager.commit();
         replicationListener.waitForReplicationToOccur(1000);

         assert !cache0.exists(fqn);

      } finally
      {
         TestingUtil.killCaches(cache0, cache1);
      }
   }



   private void cleanupDelay()
   {
      TestingUtil.sleepThread(250);
   }
}
