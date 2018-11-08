package org.jboss.cache.factories;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import static org.jboss.cache.config.Configuration.CacheMode.*;
import static org.jboss.cache.config.Configuration.NodeLockingScheme.MVCC;
import static org.jboss.cache.config.Configuration.NodeLockingScheme.OPTIMISTIC;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.interceptors.*;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;

@Test(groups = "unit", sequential = true, testName = "factories.InterceptorChainFactoryTest")
public class InterceptorChainFactoryTest extends InterceptorChainTestBase
{
   CacheSPI cache = null;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration configuration = new Configuration();
      configuration.setCacheMode(LOCAL);
      configuration.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      configuration.setUseLazyDeserialization(false);
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(configuration,false, getClass());
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testBareConfig() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);
      assertEquals(5, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testBatchingConfig() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setInvocationBatchingEnabled(true);
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);
      assertEquals(6, list.size());

      assertEquals(BatchingInterceptor.class, interceptors.next().getClass());
      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }


   public void testMvccConfig() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setNodeLockingScheme(MVCC);
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);
      assertEquals(5, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(MVCCLockingInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testTxConfig() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());

      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);
      assertEquals(5, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   protected CacheLoaderConfig getCacheLoaderConfig(boolean pasv, boolean fetchPersistentState) throws Exception
   {
      CacheLoaderConfig clc = new CacheLoaderConfig();
      IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
      iclc.setClassName(DummyInMemoryCacheLoader.class.getName());
      iclc.setFetchPersistentState(fetchPersistentState);
      clc.addIndividualCacheLoaderConfig(iclc);
      clc.setPassivation(pasv);
      return clc;
   }

   public void testSharedCacheLoaderConfig() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(false, false));
      cache.getConfiguration().setCacheMode(REPL_ASYNC);
      cache.getConfiguration().setFetchInMemoryState(false);
      cache.create();
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);

      assertEquals(8, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(ReplicationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyCacheLoaderInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyCacheStoreInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testSharedCacheLoaderMvccConfig() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(false, false));
      cache.getConfiguration().setCacheMode(REPL_ASYNC);
      cache.getConfiguration().setNodeLockingScheme(MVCC);
      cache.getConfiguration().setFetchInMemoryState(false);
      cache.create();
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);

      assertEquals(8, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(ReplicationInterceptor.class, interceptors.next().getClass());
      assertEquals(CacheLoaderInterceptor.class, interceptors.next().getClass());
      assertEquals(MVCCLockingInterceptor.class, interceptors.next().getClass());
      assertEquals(CacheStoreInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }


   public void testUnsharedCacheLoaderConfig() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(false, true));
      cache.getConfiguration().setCacheMode(REPL_ASYNC);
      cache.getConfiguration().setFetchInMemoryState(false);
      cache.create();
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);

      assertEquals(8, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(ReplicationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyCacheLoaderInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyCacheStoreInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testUnsharedCacheLoaderMvccConfig() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(false, true));
      cache.getConfiguration().setCacheMode(REPL_ASYNC);
      cache.getConfiguration().setNodeLockingScheme(MVCC);
      cache.getConfiguration().setFetchInMemoryState(false);
      cache.create();
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);

      assertEquals(8, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(ReplicationInterceptor.class, interceptors.next().getClass());
      assertEquals(CacheLoaderInterceptor.class, interceptors.next().getClass());
      assertEquals(MVCCLockingInterceptor.class, interceptors.next().getClass());
      assertEquals(CacheStoreInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testTxAndRepl() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setCacheMode(REPL_SYNC);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);

      assertEquals(6, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(ReplicationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }


   public void testOptimisticChain() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setNodeLockingScheme(OPTIMISTIC);

      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertEquals(8, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticTxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticLockingInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticValidatorInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticCreateIfNotExistsInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticNodeInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testOptimisticReplicatedChain() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setNodeLockingScheme(OPTIMISTIC);
      cache.getConfiguration().setCacheMode(REPL_SYNC);

      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertEquals(9, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticTxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticReplicationInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticLockingInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticValidatorInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticCreateIfNotExistsInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticNodeInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testOptimisticCacheLoaderChain() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setNodeLockingScheme(OPTIMISTIC);
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(false, false));
      cache.create();
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertEquals(10, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticTxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyCacheLoaderInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyCacheStoreInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticLockingInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticValidatorInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticCreateIfNotExistsInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticNodeInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testOptimisticPassivationCacheLoaderChain() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setNodeLockingScheme(OPTIMISTIC);
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(true, false));
      cache.create();
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertEquals(10, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticTxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyActivationInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyPassivationInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticLockingInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticValidatorInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticCreateIfNotExistsInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticNodeInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testPassivationMvccChain() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setNodeLockingScheme(MVCC);
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(true, false));
      cache.create();
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertEquals(7, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(ActivationInterceptor.class, interceptors.next().getClass());
      assertEquals(MVCCLockingInterceptor.class, interceptors.next().getClass());
      assertEquals(PassivationInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testInvalidationInterceptorChain() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setCacheMode(REPL_ASYNC);

      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertEquals(6, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(ReplicationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      // ok, my replication chain looks good.

      // now for my invalidation chain.
      cache.getConfiguration().setExposeManagementStatistics(false);
      cache.getConfiguration().setCacheMode(INVALIDATION_ASYNC);
      chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      list = chain.asList();
      interceptors = list.iterator();

      assertEquals(6, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(InvalidationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testCacheMgmtConfig() throws Exception
   {
      cache.getConfiguration().setExposeManagementStatistics(true);
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);
      assertEquals(6, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(CacheMgmtInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);

   }

   public void testEvictionInterceptorConfig() throws Exception
   {
      cache.getConfiguration().setEvictionConfig(new EvictionConfig()
      {
         private static final long serialVersionUID = -6644183636899605065L;

         public boolean isValidConfig()
         {
            return true;
         }
      }
      );
      InterceptorChainFactory factory = getInterceptorChainFactory(cache);
      InterceptorChain chain = factory.buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);
      assertEquals(7, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(CacheMgmtInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(EvictionInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testBuddyReplicationOptLocking() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);

      cache.getConfiguration().setCacheMode(REPL_SYNC);
      cache.getConfiguration().setNodeLockingScheme(OPTIMISTIC);
      cache.create();// initialise various subsystems such as BRManager
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);
      assertEquals(11, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(CacheMgmtInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticTxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticReplicationInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyDataGravitatorInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticLockingInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticValidatorInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticCreateIfNotExistsInterceptor.class, interceptors.next().getClass());
      assertEquals(OptimisticNodeInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   public void testBuddyReplicationPessLocking() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      cache.getConfiguration().setBuddyReplicationConfig(brc);
      cache.getConfiguration().setCacheMode(REPL_SYNC);
      cache.create();// initialise various subsystems such as BRManager
      InterceptorChain chain = getInterceptorChainFactory(cache).buildInterceptorChain();
      List<CommandInterceptor> list = chain.asList();
      Iterator<CommandInterceptor> interceptors = list.iterator();

      assertNotNull(list);
      assertEquals(8, list.size());

      assertEquals(InvocationContextInterceptor.class, interceptors.next().getClass());
      assertEquals(CacheMgmtInterceptor.class, interceptors.next().getClass());
      assertEquals(TxInterceptor.class, interceptors.next().getClass());
      assertEquals(NotificationInterceptor.class, interceptors.next().getClass());
      assertEquals(ReplicationInterceptor.class, interceptors.next().getClass());
      assertEquals(PessimisticLockInterceptor.class, interceptors.next().getClass());
      assertEquals(LegacyDataGravitatorInterceptor.class, interceptors.next().getClass());
      assertEquals(CallInterceptor.class, interceptors.next().getClass());

      assertInterceptorLinkage(list);
   }

   private InterceptorChainFactory getInterceptorChainFactory(Cache cache)
   {
      return InterceptorChainFactory.getInstance(TestingUtil.extractComponentRegistry(cache), cache.getConfiguration());
   }
}
