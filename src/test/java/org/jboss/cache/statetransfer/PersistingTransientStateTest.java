package org.jboss.cache.statetransfer;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

/**
 * Transient state SHOULD be persisted on the receiving cache IF fetchPersistentState is FALSE and the cache loader
 * is NOT shared.
 * <p/>
 * Needs to be tested with PL, OL and MVCC.
 * <p/>
 * Pertains to JBCACHE-131
 * <p/>
 */
@Test(groups = "functional", testName = "statetransfer.PersistingTransientStateTest")
public class PersistingTransientStateTest
{
   protected NodeLockingScheme nls = NodeLockingScheme.PESSIMISTIC;
   protected Fqn fqn = Fqn.fromString("/a/b/c");
   protected String k = "k", v = "v";

   public void testPersistentStateTransfer() throws Exception
   {
      Cache<String, String> c1 = null, c2 = null;
      try
      {
         UnitTestCacheFactory<String, String> cf = new UnitTestCacheFactory<String, String>();
         Configuration cfg = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);
         cfg.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
         cfg.setFetchInMemoryState(true);
         // configure with CL
         IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
         iclc.setClassName(DummyInMemoryCacheLoader.class.getName());
         iclc.setFetchPersistentState(false);
         CacheLoaderConfig clc = new CacheLoaderConfig();
         clc.addIndividualCacheLoaderConfig(iclc);

         cfg.setCacheLoaderConfig(clc);
         cfg.setNodeLockingScheme(nls);

         c1 = cf.createCache(cfg.clone(), getClass());
         c1.put(fqn, k, v);

         assert c1.get(fqn, k).equals(v);
         assert getLoader(c1).get(fqn).get(k).equals(v);

         c2 = cf.createCache(cfg.clone(), getClass());
         assert c2.get(fqn, k).equals(v);
         assert getLoader(c2).get(fqn).get(k).equals(v);
      }
      finally
      {
         TestingUtil.killCaches(c1, c2);
      }
   }

   public void testPersistentStateTransferShared() throws Exception
   {
      Cache<String, String> c1 = null, c2 = null;
      try
      {
         Configuration cfg = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);
         cfg.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
         cfg.setFetchInMemoryState(true);
         // configure with CL
         IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
         iclc.setClassName(DummyInMemoryCacheLoader.class.getName());
         iclc.setFetchPersistentState(false);
         CacheLoaderConfig clc = new CacheLoaderConfig();
         clc.addIndividualCacheLoaderConfig(iclc);
         clc.setShared(true); // even though it isn't really a shared CL

         cfg.setCacheLoaderConfig(clc);
         cfg.setNodeLockingScheme(nls);

         c1 = new UnitTestCacheFactory<String, String>().createCache(cfg.clone(), getClass());
         c1.put(fqn, k, v);

         assert c1.get(fqn, k).equals(v);
         assert getLoader(c1).get(fqn).get(k).equals(v);

         c2 = new UnitTestCacheFactory<String, String>().createCache(cfg.clone(), getClass());
         assert c2.get(fqn, k).equals(v);
         assert getLoader(c2).get(fqn) == null;
      }
      finally
      {
         TestingUtil.killCaches(c1, c2);
      }
   }

   protected CacheLoader getLoader(Cache<?, ?> c)
   {
      return ((CacheSPI) c).getCacheLoaderManager().getCacheLoader();
   }

}
