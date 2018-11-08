package org.jboss.cache.api.batch;

import org.jboss.cache.Cache;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.CacheSPI;

@Test(groups = {"functional", "transaction"}, testName = "api.batch.BatchWithTM")
public class BatchWithTM extends AbstractBatchTest
{
   public void testBatchWithOngoingTM() throws Exception
   {
      TransactionManager tm = getTransactionManager(cache);
      tm.begin();
      cache.put("/a/b/c", "k", "v");
      cache.startBatch();
      cache.put("/a/b/c", "k2", "v2");
      tm.commit();

      assert "v".equals(cache.get("/a/b/c", "k"));
      assert "v2".equals(cache.get("/a/b/c", "k2"));

      cache.endBatch(false); // should be a no op
      assert "v".equals(cache.get("/a/b/c", "k"));
      assert "v2".equals(cache.get("/a/b/c", "k2"));
   }

   public void testBatchWithoutOngoingTMSuspension() throws Exception
   {
      TransactionManager tm = getTransactionManager(cache);
      assert tm.getTransaction() == null : "Should have no ongoing txs";
      cache.startBatch();
      cache.put("/a/b/c", "k", "v");
      assert tm.getTransaction() == null : "Should have no ongoing txs";
      cache.put("/a/b/c", "k2", "v2");

      assert getOnDifferentThread(cache, "/a/b/c", "k") == null;
      assert getOnDifferentThread(cache, "/a/b/c", "k2") == null;

      try
      {
         tm.commit(); // should have no effect
      }
      catch (Exception e)
      {
         // the TM may barf here ... this is OK.
      }

      assert tm.getTransaction() == null : "Should have no ongoing txs";

      assert getOnDifferentThread(cache, "/a/b/c", "k") == null;
      assert getOnDifferentThread(cache, "/a/b/c", "k2") == null;

      cache.endBatch(true); // should be a no op

      assert "v".equals(getOnDifferentThread(cache, "/a/b/c", "k"));
      assert "v2".equals(getOnDifferentThread(cache, "/a/b/c", "k2"));
   }

   public void testBatchRollback() throws Exception
   {
      TransactionManager tm = getTransactionManager(cache);
      cache.startBatch();
      cache.put("/a/b/c", "k", "v");
      cache.put("/a/b/c", "k2", "v2");

      assert getOnDifferentThread(cache, "/a/b/c", "k") == null;
      assert getOnDifferentThread(cache, "/a/b/c", "k2") == null;

      cache.endBatch(false);

      assert getOnDifferentThread(cache, "/a/b/c", "k") == null;
      assert getOnDifferentThread(cache, "/a/b/c", "k2") == null;
   }

   private TransactionManager getTransactionManager(Cache<String, String> c)
   {
      return c.getConfiguration().getRuntimeConfig().getTransactionManager();
   }

   @Override
   public CacheSPI<String, String> createCache()
   {
      UnitTestCacheFactory<String, String> cf = new UnitTestCacheFactory<String, String>();
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.LOCAL); // this should pick up any configured TM for the test
      c.setNodeLockingScheme(NodeLockingScheme.MVCC);
      c.setInvocationBatchingEnabled(true);
      assert c.getTransactionManagerLookupClass() != null : "Should have a transaction manager lookup class attached!!";
      return (CacheSPI<String, String>) cf.createCache(c, getClass());
   }

}
