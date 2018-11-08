package org.jboss.cache.config.parsing;

import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.config.CustomInterceptorConfig;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.config.parsing.custominterceptors.AaaCustomInterceptor;
import org.jboss.cache.config.parsing.custominterceptors.BbbCustomInterceptor;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.eviction.MRUAlgorithmConfig;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Parses a 'normal' configuration file and makes sure that the resulted <b>Configuration</b> object has
 * the expected state.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "functional", testName = "config.parsing.XmlConfigurationParserTest")
public class XmlConfigurationParserTest
{
   Configuration syncConfig, asyncConfig;

   @BeforeTest
   public void setUp()
   {
      syncConfig = new XmlConfigurationParser(false, null).parseFile("configs/parser-test.xml");
      asyncConfig = new XmlConfigurationParser(false, null).parseFile("configs/parser-test-async.xml");
   }

   public void testParseOldConfigFile()
   {
      System.setProperty("jbosscache.config.validate", "false");
      XmlConfigurationParser parser = new XmlConfigurationParser();
      try
      {
         parser.parseFile("configs/conf2x/pess-local.xml");
         assert false : "exception expected";
      }
      catch (ConfigurationException e)
      {
         //expectd
      }
      finally
      {
         System.setProperty("jbosscache.config.validate", "true");
      }
   }

   public void testTransactionManagerLookupClass()
   {
      assert syncConfig.getTransactionManagerLookupClass().equals("org.jboss.cache.transaction.GenericTransactionManagerLookup");
   }

   public void testIsolationLevel()
   {
      assert syncConfig.getIsolationLevel().equals(IsolationLevel.REPEATABLE_READ);
   }

   public void testCacheMode()
   {
      assert syncConfig.getCacheMode().equals(Configuration.CacheMode.REPL_SYNC) : "Was " + syncConfig.getCacheMode();
      assert asyncConfig.getCacheMode().equals(Configuration.CacheMode.REPL_ASYNC): "Was " + syncConfig.getCacheMode();
   }

   public void testAsyncSerializationExecutorSize()
   {
      assert asyncConfig.getSerializationExecutorPoolSize() == 250;
      assert asyncConfig.getSerializationExecutorQueueSize() == 5000000;
   }

   public void testUseReplQueue()
   {
      assert !syncConfig.isUseReplQueue();
      assert !asyncConfig.isUseReplQueue();
   }

   public void testClusterName()
   {
      assert syncConfig.getClusterName().equals("JBossCache-cluster");
   }

   public void testGetClusterConfig()
   {
      assert asyncConfig.getClusterConfig().indexOf("MERGE2") >= 0;
   }

   public void testFetchInMemoryState()
   {
      assert syncConfig.isFetchInMemoryState();
   }

   public void testStateRetrievalTimeout()
   {
      assert syncConfig.getStateRetrievalTimeout() == 15124;
   }

   public void testNonBlockingStateTransfer()
   {
      assert syncConfig.isNonBlockingStateTransfer();
      assert !asyncConfig.isNonBlockingStateTransfer();
   }

   public void testSyncReplTimeout()
   {
      assert syncConfig.getSyncReplTimeout() == 15421;
   }

   public void testLockAcquisitionTimeout()
   {
      assert syncConfig.getLockAcquisitionTimeout() == 10234;
   }

   public void testUseLazyDeserialization()
   {
      assert syncConfig.isUseLazyDeserialization();
   }

   public void testObjectInputStreamPoolSize()
   {
      assert 12 == syncConfig.getObjectInputStreamPoolSize();
   }

   public void testObjectOutputStreamPoolSize()
   {
      assert 14 == syncConfig.getObjectOutputStreamPoolSize();
   }

   public void testShutdownHookBehavior()
   {
      assert Configuration.ShutdownHookBehavior.REGISTER == syncConfig.getShutdownHookBehavior();
   }

   public void testSyncRollbackPhase()
   {
      assert syncConfig.isSyncRollbackPhase();
   }

   public void testSyncCommitPhase()
   {
      assert syncConfig.isSyncCommitPhase();
   }

   public void testUseReplicationVersion()
   {
      assert syncConfig.getReplicationVersion() == 124;
   }

   public void testGetMultiplexerStack()
   {
      assert "file_name".equals(syncConfig.getMultiplexerStack());
   }

   public void testMarshallerClass()
   {
      assert "some.Clazz".equals(syncConfig.getMarshallerClass());
   }

   public void testLockParentForChildInsertRemove()
   {
      assert syncConfig.isLockParentForChildInsertRemove();
   }

   public void testInactiveOnStartup()
   {
      assert syncConfig.isInactiveOnStartup();
   }

   public void testExposeManagementStatistics()
   {
      assert !syncConfig.getExposeManagementStatistics();
   }

   public void testCacheLoaderConfiguration()
   {
      CacheLoaderConfig clc = syncConfig.getCacheLoaderConfig();
      assert null != clc;
      assert clc.isPassivation();
      assert clc.isShared();
      assert "/a/b/c,/f/r/s".equals(clc.getPreload());
      CacheLoaderConfig.IndividualCacheLoaderConfig first = clc.getFirstCacheLoaderConfig();
      assert "org.jboss.cache.loader.JDBCCacheLoader".equals(first.getClassName());
      assert first.isAsync();
      assert first.isFetchPersistentState();
      assert first.isIgnoreModifications();
      assert first.isPurgeOnStartup();
      assert first.getProperties().get("cache.jdbc.table.name").equals("jbosscache");
      assert first.getProperties().get("cache.jdbc.table.create").equals("true");
      assert first.getProperties().get("cache.jdbc.table.drop").equals("true");
   }

   public void testBuddyReplicationConfig()
   {
      BuddyReplicationConfig brConfig = syncConfig.getBuddyReplicationConfig();
      assert brConfig.isEnabled();
      BuddyReplicationConfig.BuddyLocatorConfig locatorConfig = brConfig.getBuddyLocatorConfig();
      assert "org.jboss.cache.buddyreplication.NextMemberBuddyLocator".equals(locatorConfig.getBuddyLocatorClass());
      assert locatorConfig.getBuddyLocatorProperties().get("numBuddies").equals("1");
      assert locatorConfig.getBuddyLocatorProperties().get("ignoreColocatedBuddies").equals("true");
      assert brConfig.getBuddyPoolName().equals("myBuddyPoolReplicationGroup");
      assert brConfig.getBuddyCommunicationTimeout() == 2000;
      assert brConfig.isAutoDataGravitation();
      assert brConfig.isDataGravitationRemoveOnFind();
      assert brConfig.isDataGravitationSearchBackupTrees();
   }

   public void testUseRegionBasedMarshalling()
   {
      assert syncConfig.isUseRegionBasedMarshalling();
   }

   public void testEvictionPolicyConfig()
   {
      EvictionConfig evictionConfig = syncConfig.getEvictionConfig();
      assert "org.jboss.cache.eviction.LRUAlgorithm".equals(evictionConfig.getDefaultEvictionRegionConfig().getEvictionAlgorithmConfig().getEvictionAlgorithmClassName());
      assert 200000 == evictionConfig.getDefaultEvictionRegionConfig().getEventQueueSize();
      assert 5 == evictionConfig.getWakeupInterval();

      List<EvictionRegionConfig> regionConfigs = evictionConfig.getEvictionRegionConfigs();
      assert regionConfigs.size() == 2;

      EvictionRegionConfig first = evictionConfig.getDefaultEvictionRegionConfig();
      assert first.getRegionName().equals("/");
      assert first.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig;
      LRUAlgorithmConfig firstConfiguration = (LRUAlgorithmConfig) first.getEvictionAlgorithmConfig();
      assert firstConfiguration.getMaxAge() <= 0;
      assert firstConfiguration.getTimeToLive() == 1000;
      assert firstConfiguration.getMaxNodes() == 5000;

      EvictionRegionConfig second = regionConfigs.get(0);
      LRUAlgorithmConfig secondConfiguration = (LRUAlgorithmConfig) second.getEvictionAlgorithmConfig();
      assert secondConfiguration.getMaxAge() == -1;
      assert secondConfiguration.getTimeToLive() == 1002;
      assert secondConfiguration.getMaxNodes() == 5000;

      EvictionRegionConfig third = regionConfigs.get(1);
      MRUAlgorithmConfig thirdConfiguration = (MRUAlgorithmConfig) third.getEvictionAlgorithmConfig();
      assert thirdConfiguration.getMaxNodes() == 2103;
      assert thirdConfiguration.getMinTimeToLive() == 22;
      assert third.getEventQueueSize() == 21;
   }

   public void testCustomInterceptors()
   {
      List<CustomInterceptorConfig> interceptorConfigs = syncConfig.getCustomInterceptors();
      assert interceptorConfigs.size() == 5;
      assert interceptorConfigs.get(0).getInterceptor() instanceof AaaCustomInterceptor;
      AaaCustomInterceptor a = (AaaCustomInterceptor) interceptorConfigs.get(0).getInterceptor();
      assert a.getAttrOne().equals("value1");
      assert a.getAttrTwo().equals("value2");
      assert a.getAttrThree() == null;
      assert interceptorConfigs.get(1).getInterceptor() instanceof BbbCustomInterceptor;
      assert interceptorConfigs.get(2).getInterceptor() instanceof AaaCustomInterceptor;
      assert interceptorConfigs.get(3).getInterceptor() instanceof BbbCustomInterceptor;
      assert interceptorConfigs.get(4).getInterceptor() instanceof AaaCustomInterceptor;
      assert interceptorConfigs.get(0).isFirst();
      assert !interceptorConfigs.get(0).isLast();
      assert interceptorConfigs.get(1).isLast();
      assert interceptorConfigs.get(2).getIndex() == 3;
      assert interceptorConfigs.get(3).getBeforeClass().equals("org.jboss.cache.interceptors.CallInterceptor");
      assert interceptorConfigs.get(4).getAfterClass().equals("org.jboss.cache.interceptors.CallInterceptor");
   }

   public void testSingletonStore()
   {
      CacheLoaderConfig.IndividualCacheLoaderConfig clc = syncConfig.getCacheLoaderConfig().getFirstCacheLoaderConfig();
      assert clc != null;
      CacheLoaderConfig.IndividualCacheLoaderConfig.SingletonStoreConfig singlStoreConf = clc.getSingletonStoreConfig();
      assert singlStoreConf != null;
      assert singlStoreConf.isSingletonStoreEnabled();
      assert singlStoreConf.getSingletonStoreClass().equals("org.jboss.cache.loader.SingletonStoreCacheLoader");
      assert singlStoreConf.getProperties().size() == 2;
      assert singlStoreConf.getProperties().get("pushStateWhenCoordinator").equals("true");
      assert singlStoreConf.getProperties().get("pushStateWhenCoordinatorTimeout").equals("20000");
   }

   public void testMvccAttributes()
   {
      assert !syncConfig.isWriteSkewCheck();
      assert syncConfig.getConcurrencyLevel() == 21;
   }

   public void testListenerAsyncThreads()
   {
      assert syncConfig.getListenerAsyncPoolSize() == 5;
      assert syncConfig.getListenerAsyncQueueSize() == 50000; // the default

      assert asyncConfig.getListenerAsyncPoolSize() == 5;
      assert asyncConfig.getListenerAsyncQueueSize() == 100000;
   }

   public void testInvocationBatching()
   {
      assert syncConfig.isInvocationBatchingEnabled();
   }
}
