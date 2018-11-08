package org.jboss.cache.loader;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

@Test(groups = "functional", testName = "loader.CacheLoaderWithRollbackTest")
public class CacheLoaderWithRollbackTest
{
   final Fqn<String> fqn = Fqn.fromString("/a/b");
   final String key = "key";

   protected Configuration.NodeLockingScheme getNodeLockingScheme()
   {
      return Configuration.NodeLockingScheme.MVCC;
   }


   public Cache<String, String> init(boolean passivation) throws Exception
   {
      CacheLoaderConfig cacheLoaderConfig = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(passivation, "", DummyInMemoryCacheLoader.class.getName(), "", false, true, false, false, false);
      Configuration cfg = new Configuration();
      cfg.setNodeLockingScheme(getNodeLockingScheme());
      cfg.setCacheLoaderConfig(cacheLoaderConfig);
      cfg.getRuntimeConfig().setTransactionManager(new DummyTransactionManager());
      Cache<String, String> cache = new UnitTestCacheFactory().createCache(cfg, getClass());
      cache.put(fqn, key, "value");

      // evict the node, so we have to go to the loader to do anything with it
      cache.evict(fqn);
      return cache;
   }

   public void testWithPassivation() throws Exception
   {
      doTest(true);
   }

   public void testWithoutPassivation() throws Exception
   {
      doTest(false);
   }

   private void doTest(boolean passivation) throws Exception
   {
      Cache<String, String> cache = null;
      try
      {
         cache = init(passivation);
         TransactionManager tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

         tm.begin();
         assert cache.getNode(fqn.getParent().getParent()).getChildrenNames().size() == 1;
         assert cache.getNode(fqn.getParent()).getChildrenNames().size() == 1;
         tm.rollback();

         // in the fail scenario the rollback corrupts the parent node
         tm.begin();
         assert cache.getNode(fqn.getParent().getParent()).getChildrenNames().size() == 1;
         // watch here:
         int sz = cache.getNode(fqn.getParent()).getChildrenNames().size();
         assert sz == 1 : "Expecting 1, was " + sz;
         tm.commit();
      }
      finally
      {
         TestingUtil.killCaches(cache);
      }
   }
}
