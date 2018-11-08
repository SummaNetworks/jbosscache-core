package org.jboss.cache.cluster;

import org.jboss.cache.Cache;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.commands.tx.PrepareCommand;

@Test(groups = {"functional", "transaction"}, testName = "cluster.ReplicationQueueTxTest")
public class ReplicationQueueTxTest
{
   Cache cache, cache2;
   TransactionManager txManager;

   @BeforeMethod
   public void setUp() throws CloneNotSupportedException
   {
      Configuration c = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_ASYNC);
      cache = new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      cache.getConfiguration().setUseReplQueue(true);
      cache.getConfiguration().setReplQueueInterval(100);
      cache.getConfiguration().setReplQueueMaxElements(10);
      cache.start();
      cache2 = new UnitTestCacheFactory<Object, Object>().createCache(cache.getConfiguration().clone(), getClass());

      TestingUtil.blockUntilViewsReceived(60000, cache, cache2);
      txManager = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache, cache2);
      cache = null;
      cache2 = null;
   }

   public void testTransactionalReplication() throws Exception
   {
      ReplicationListener cache1Listener = ReplicationListener.getReplicationListener(cache);
      ReplicationListener cache2Listener = ReplicationListener.getReplicationListener(cache2);

      cache2Listener.expect(PutKeyValueCommand.class);
      // outside of tx scope
      cache.put("/a", "k", "v");
      cache2Listener.waitForReplicationToOccur(5000);

      assert cache2.get("/a", "k").equals("v");

      // now, a transactional call
      cache1Listener.expect(PrepareCommand.class);
      txManager.begin();
      cache2.put("/a", "k", "v2");
      txManager.commit();
      cache1Listener.waitForReplicationToOccur(5000);

      assert cache.get("/a", "k").equals("v2");
   }
}
