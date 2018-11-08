package org.jboss.cache.api.batch;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "api.batch.BatchWithoutTM")
public class BatchWithoutTM extends AbstractBatchTest
{

   @Test (enabled = true)
   public void testBatchWithoutCfg()
   {
      Cache<String, String> localCache = createCache(false);
      try
      {
         try
         {
            localCache.startBatch();
            assert false : "Should have failed";
         }
         catch (ConfigurationException good)
         {
            // do nothing
         }

         try
         {
            localCache.endBatch(true);
            assert false : "Should have failed";
         }
         catch (ConfigurationException good)
         {
            // do nothing
         }

         try
         {
            localCache.endBatch(false);
            assert false : "Should have failed";
         }
         catch (ConfigurationException good)
         {
            // do nothing
         }
      }
      finally
      {
         TestingUtil.killCaches(localCache);
      }
   }

   public void testStartBatchIdempotency()
   {
      assert cache.getCacheStatus().allowInvocations();
      cache.startBatch();
      cache.put("/a/b/c", "k", "v");
      cache.startBatch();     // again
      cache.put("/a/b/c", "k2", "v2");
      cache.endBatch(true);

      assert "v".equals(cache.get("/a/b/c", "k"));
      assert "v2".equals(cache.get("/a/b/c", "k2"));
   }

   public void testBatchVisibility() throws InterruptedException
   {
      cache = createCache(true);
      cache.startBatch();
      cache.put("/a/b/c", "k", "v");
      assert getOnDifferentThread(cache, "/a/b/c", "k") == null : "Other thread should not see batch update till batch completes!";
      cache.endBatch(true);
      assert "v".equals(getOnDifferentThread(cache, "/a/b/c", "k"));
   }

   public void testBatchRollback() throws Exception
   {
      cache.startBatch();
      cache.put("/a/b/c", "k", "v");
      cache.put("/a/b/c", "k2", "v2");

      assert getOnDifferentThread(cache, "/a/b/c", "k") == null;
      assert getOnDifferentThread(cache, "/a/b/c", "k2") == null;

      cache.endBatch(false);

      assert getOnDifferentThread(cache, "/a/b/c", "k") == null;
      assert getOnDifferentThread(cache, "/a/b/c", "k2") == null;
   }

   public CacheSPI<String, String> createCache()
   {
      return createCache(true);
   }

   private CacheSPI<String, String> createCache(boolean enableBatch)
   {
      UnitTestCacheFactory<String, String> cf = new UnitTestCacheFactory<String, String>();
      Configuration c = new Configuration();
      c.setNodeLockingScheme(NodeLockingScheme.MVCC);
      c.setInvocationBatchingEnabled(enableBatch);
      return (CacheSPI<String, String>) cf.createCache(c, getClass());
   }
}
