/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.cache.jmx.deprecated;

import org.jboss.cache.Version;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.BuddyReplicationConfig.BuddyLocatorConfig;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.config.RuntimeConfig;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.eviction.FIFOAlgorithm;
import org.jboss.cache.eviction.FIFOAlgorithmConfig;
import org.jboss.cache.eviction.LRUAlgorithm;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.eviction.MRUAlgorithm;
import org.jboss.cache.eviction.MRUAlgorithmConfig;
import org.jboss.cache.jmx.CacheJmxWrapper;
import org.jboss.cache.jmx.CacheJmxWrapperMBean;
import org.jboss.cache.loader.FileCacheLoader;
import org.jboss.cache.loader.SingletonStoreCacheLoader;
import org.jboss.cache.loader.jdbm.JdbmCacheLoader;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.multiplexer.MultiplexerTestHelper;
import org.jboss.cache.transaction.BatchModeTransactionManagerLookup;
import org.jgroups.ChannelFactory;
import org.jgroups.JChannelFactory;
import org.jgroups.jmx.JChannelFactoryMBean;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.transaction.TransactionManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Properties;

/**
 * Test of the CacheLegacyJmxWrapper.
 *
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7696 $
 */
@Test(groups = "functional", testName = "jmx.deprecated.LegacyConfigurationTest")
public class LegacyConfigurationTest extends CacheJmxWrapperTestBase
{
   public void testLocalCache() throws Exception
   {
      doTest(false);
   }

   public void testLocalCacheWithLegacyXML() throws Exception
   {
      doTest(true);
   }

   @SuppressWarnings({"deprecation", "unchecked"})
   private void doTest(boolean legacy) throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = new CacheJmxWrapper();
      registerWrapper(wrapper);

      wrapper = (CacheJmxWrapperMBean<String, String>) MBeanServerInvocationHandler.newProxyInstance(mBeanServer, mBeanName, CacheJmxWrapperMBean.class, false);

      wrapper.setBuddyReplicationConfig(getBuddyReplicationConfig(legacy));
      wrapper.setCacheLoaderConfig(getCacheLoaderConfig(legacy));
      wrapper.setCacheMode("REPL_SYNC");
      wrapper.setClusterName("LocalTest");
      wrapper.setClusterConfig(getClusterConfig());
      wrapper.setEvictionPolicyConfig(getEvictionPolicyConfig(legacy));
      wrapper.setFetchInMemoryState(false);
      wrapper.setInitialStateRetrievalTimeout(100);
      wrapper.setInactiveOnStartup(true);
      wrapper.setNodeLockingScheme("OPTIMISTIC");
      wrapper.setIsolationLevel("READ_UNCOMMITTED");
      wrapper.setLockAcquisitionTimeout(200);
      wrapper.setReplicationVersion("1.0.1");
      wrapper.setReplQueueInterval(15);
      wrapper.setReplQueueMaxElements(50);
      wrapper.setSyncReplTimeout(300);
      wrapper.setSyncCommitPhase(true);
      wrapper.setSyncRollbackPhase(true);
      wrapper.setTransactionManagerLookupClass(BatchModeTransactionManagerLookup.class.getName());
      wrapper.setExposeManagementStatistics(false);
      wrapper.setUseRegionBasedMarshalling(true);
      wrapper.setUseReplQueue(true);

      Configuration c = wrapper.getConfiguration();

      assertEquals("CacheMode", "REPL_SYNC", wrapper.getCacheMode());
      assertEquals("CacheMode", CacheMode.REPL_SYNC, c.getCacheMode());
      assertEquals("ClusterName", "LocalTest", wrapper.getClusterName());
      assertEquals("ClusterName", "LocalTest", c.getClusterName());
      assertEquals("FetchInMemoryState", false, wrapper.getFetchInMemoryState());
      assertEquals("FetchInMemoryState", false, c.isFetchInMemoryState());
      assertEquals("InitialStateRetrievalTimeout", 100, wrapper.getInitialStateRetrievalTimeout());
      assertEquals("InitialStateRetrievalTimeout", 100, c.getStateRetrievalTimeout());
      assertEquals("InactiveOnStartup", true, wrapper.isInactiveOnStartup());
      assertEquals("InactiveOnStartup", true, c.isInactiveOnStartup());
      assertEquals("NodeLockingScheme", "OPTIMISTIC", wrapper.getNodeLockingScheme());
      assertEquals("NodeLockingScheme", NodeLockingScheme.OPTIMISTIC, c.getNodeLockingScheme());
      assertEquals("IsolationLevel", "READ_UNCOMMITTED", wrapper.getIsolationLevel());
      assertEquals("IsolationLevel", IsolationLevel.READ_UNCOMMITTED, c.getIsolationLevel());
      assertEquals("LockAcquisitionTimeout", 200, wrapper.getLockAcquisitionTimeout());
      assertEquals("LockAcquisitionTimeout", 200, c.getLockAcquisitionTimeout());
      assertEquals("ReplicationVersion", "1.0.1", wrapper.getReplicationVersion());
      assertEquals("ReplicationVersion", Version.getVersionShort("1.0.1"), c.getReplicationVersion());
      assertEquals("ReplQueueInterval", 15, wrapper.getReplQueueInterval());
      assertEquals("ReplQueueInterval", 15, c.getReplQueueInterval());
      assertEquals("ReplQueueMaxElements", 50, wrapper.getReplQueueMaxElements());
      assertEquals("ReplQueueMaxElements", 50, c.getReplQueueMaxElements());
      assertEquals("SyncReplTimeout", 300, wrapper.getSyncReplTimeout());
      assertEquals("SyncReplTimeout", 300, c.getSyncReplTimeout());
      assertEquals("SyncCommitPhase", true, wrapper.getSyncCommitPhase());
      assertEquals("SyncCommitPhase", true, c.isSyncCommitPhase());
      assertEquals("SyncRollbackPhase", true, wrapper.getSyncRollbackPhase());
      assertEquals("SyncRollbackPhase", true, c.isSyncRollbackPhase());
      assertEquals("TransactionManagerLookupClass", BatchModeTransactionManagerLookup.class.getName(), wrapper.getTransactionManagerLookupClass());
      assertEquals("TransactionManagerLookupClass", BatchModeTransactionManagerLookup.class.getName(), c.getTransactionManagerLookupClass());
      assertEquals("ExposeManagementStatistics", false, wrapper.getExposeManagementStatistics());
      assertEquals("ExposeManagementStatistics", false, c.getExposeManagementStatistics());
      assertEquals("UseRegionBasedMarshalling", true, wrapper.getUseRegionBasedMarshalling());
      assertEquals("UseRegionBasedMarshalling", true, c.isUseRegionBasedMarshalling());
      assertEquals("UseReplQueue", true, wrapper.getUseReplQueue());
      assertEquals("UseReplQueue", true, c.isUseReplQueue());

      assertEquals("ClusterConfig", getClusterConfig().toString(), wrapper.getClusterConfig().toString());

      assertEquals("BuddyReplicationConfig", getBuddyReplicationConfig(legacy).toString(), wrapper.getBuddyReplicationConfig().toString());
      BuddyReplicationConfig brc = c.getBuddyReplicationConfig();
      assertEquals("BR enabled", true, brc.isEnabled());
      assertEquals("BR auto grav", false, brc.isAutoDataGravitation());
      assertEquals("BR remove find", false, brc.isDataGravitationRemoveOnFind());
      assertEquals("BR search backup", false, brc.isDataGravitationSearchBackupTrees());
      assertEquals("BR comm timeout", 600000, brc.getBuddyCommunicationTimeout());
      assertEquals("BR poolname", "testpool", brc.getBuddyPoolName());
      BuddyLocatorConfig blc = brc.getBuddyLocatorConfig();
      assertEquals("BR locator", "org.jboss.cache.buddyreplication.TestBuddyLocator", blc.getBuddyLocatorClass());
      Properties props = blc.getBuddyLocatorProperties();
      assertEquals("BR props", "2", props.get("numBuddies"));

      assertEquals("CacheLoaderConfig", getCacheLoaderConfig(legacy).toString(), wrapper.getCacheLoaderConfig().toString());
      CacheLoaderConfig clc = c.getCacheLoaderConfig();
      assertEquals("CL passivation", false, clc.isPassivation());
      assertEquals("CL passivation", true, clc.isShared());
      assertEquals("CL preload", "/foo", clc.getPreload());
      List<IndividualCacheLoaderConfig> iclcs = clc.getIndividualCacheLoaderConfigs();
      IndividualCacheLoaderConfig iclc = iclcs.get(0);
      assertEquals("CL0 class", FileCacheLoader.class.getName(), iclc.getClassName());
      assertEquals("CL0 async", false, iclc.isAsync());
      assertEquals("CL0 fetch", true, iclc.isFetchPersistentState());
      assertEquals("CL0 ignore", true, iclc.isIgnoreModifications());
      assertEquals("CL0 purge", true, iclc.isPurgeOnStartup());
      assertEquals("CL0 singleton", true, iclc.getSingletonStoreConfig().isSingletonStoreEnabled());
      assertEquals("CL0 singleton class", SingletonStoreCacheLoader.class.getName(), iclc.getSingletonStoreConfig().getSingletonStoreClass());
      iclc = iclcs.get(1);
      assertEquals("CL1 class", JdbmCacheLoader.class.getName(), iclc.getClassName());
      assertEquals("CL1 async", true, iclc.isAsync());
      assertEquals("CL1 fetch", false, iclc.isFetchPersistentState());
      assertEquals("CL1 ignore", false, iclc.isIgnoreModifications());
      assertEquals("CL1 purge", false, iclc.isPurgeOnStartup());
      assertEquals("CL1 singleton", false, iclc.getSingletonStoreConfig().isSingletonStoreEnabled());
      assertEquals("CL1 singleton class", SingletonStoreCacheLoader.class.getName(), iclc.getSingletonStoreConfig().getSingletonStoreClass());

      assertEquals("EvictionPolicyConfig", getEvictionPolicyConfig(legacy).toString(), wrapper.getEvictionPolicyConfig().toString());
      EvictionConfig ec = c.getEvictionConfig();
      assertEquals("EC queue size", 1000, ec.getDefaultEvictionRegionConfig().getEventQueueSize());
      assertEquals("EC wakeup", 5000, ec.getWakeupInterval());
      assertEquals("EC default pol", LRUAlgorithm.class.getName(), ec.getDefaultEvictionRegionConfig().getEvictionAlgorithmConfig().getEvictionAlgorithmClassName());
      List<EvictionRegionConfig> ercs = ec.getEvictionRegionConfigs();
      EvictionRegionConfig erc = ercs.get(0);
      assertEquals("ERC1 name", "/org/jboss/data", erc.getRegionName());
      assertEquals("ERC1 queue size", 1000, erc.getEventQueueSize());
      FIFOAlgorithmConfig fifo = (FIFOAlgorithmConfig) erc.getEvictionAlgorithmConfig();
      assertEquals("EPC1 pol", FIFOAlgorithm.class.getName(), fifo.getEvictionAlgorithmClassName());
      assertEquals("EPC1 maxnodes", 5000, fifo.getMaxNodes());
      erc = ercs.get(1);
      assertEquals("ERC2 name", "/test", erc.getRegionName());
      assertEquals("ERC2 queue size", 1000, erc.getEventQueueSize());
      MRUAlgorithmConfig mru = (MRUAlgorithmConfig) erc.getEvictionAlgorithmConfig();
      assertEquals("EPC2 pol", MRUAlgorithm.class.getName(), mru.getEvictionAlgorithmClassName());
      assertEquals("EPC2 maxnodes", 10000, mru.getMaxNodes());
      erc = ercs.get(2);
      assertEquals("ERC3 name", "/maxAgeTest", erc.getRegionName());
      assertEquals("ERC3 queue size", 1000, erc.getEventQueueSize());
      LRUAlgorithmConfig lru = (LRUAlgorithmConfig) erc.getEvictionAlgorithmConfig();
      assertEquals("EPC3 maxnodes", 10000, lru.getMaxNodes());
      assertEquals("EPC3 maxage", 10000, lru.getMaxAge());
      assertEquals("EPC3 ttl", 8000, lru.getTimeToLive());

   }

   @SuppressWarnings("unchecked")
   public void testRuntimeConfig() throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = new CacheJmxWrapper<String, String>();
      registerWrapper(wrapper);

      wrapper = (CacheJmxWrapperMBean<String, String>) MBeanServerInvocationHandler.newProxyInstance(mBeanServer, mBeanName, CacheJmxWrapperMBean.class, false);

      // Fake a TM by making a bogus proxy
      TransactionManager tm = (TransactionManager) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class[]{TransactionManager.class}, new MockInvocationHandler());
      wrapper.setTransactionManager(tm);
      ChannelFactory cf = new JChannelFactory();
      wrapper.setMuxChannelFactory(cf);

      RuntimeConfig rc = wrapper.getConfiguration().getRuntimeConfig();

      assertSame("Same TM", tm, wrapper.getTransactionManager());
      assertSame("Same TM", tm, rc.getTransactionManager());
      assertSame("Same ChannelFactory", cf, wrapper.getMuxChannelFactory());
      assertSame("Same ChannelFactory", cf, rc.getMuxChannelFactory());
   }

   @SuppressWarnings("unchecked")
   public void testLegacyMuxChannelCreation() throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = new CacheJmxWrapper<String, String>();
      registerWrapper(wrapper);

      wrapper = (CacheJmxWrapperMBean<String, String>) MBeanServerInvocationHandler.newProxyInstance(mBeanServer, mBeanName, CacheJmxWrapperMBean.class, false);
      wrapper.setMultiplexerStack(MultiplexerTestHelper.MUX_STACK + Thread.currentThread().getName());

      JChannelFactory factory = new JChannelFactory();
      factory.setDomain("jbc.mux.test");
      factory.setExposeChannels(false);
      factory.setMultiplexerConfig(MultiplexerTestHelper.getClusterConfigElement(getDefaultProperties()));

      ObjectName on = new ObjectName("jgroups:service=Mux");
      mBeanServer.registerMBean(new org.jgroups.jmx.JChannelFactory(factory), on);

      wrapper.setMultiplexerService((JChannelFactoryMBean) MBeanServerInvocationHandler.newProxyInstance(mBeanServer, on, JChannelFactoryMBean.class, false));

      wrapper.start();

      RuntimeConfig rc = wrapper.getConfiguration().getRuntimeConfig();
      assertNotNull("Channel created", rc.getChannel());
      
      //wrapper.stop();
      //wrapper.destroy();
            
   }

   protected static Element getBuddyReplicationConfig(boolean legacy) throws Exception
   {
      if (legacy)
      {
         String xmlStr = "<config>\n" +
               "      <buddyReplicationEnabled>true</buddyReplicationEnabled>\n" +
               "      <buddyLocatorClass>org.jboss.cache.buddyreplication.TestBuddyLocator</buddyLocatorClass>\n" +
               "      <buddyLocatorProperties>\n" +
               "         numBuddies = 2\n" +
               "      </buddyLocatorProperties>\n" +
               "      <buddyPoolName>testpool</buddyPoolName>\n" +
               "      <buddyCommunicationTimeout>600000</buddyCommunicationTimeout>\n" +
               "      <dataGravitationRemoveOnFind>false</dataGravitationRemoveOnFind>\n" +
               "      <dataGravitationSearchBackupTrees>false</dataGravitationSearchBackupTrees>\n" +
               "      <autoDataGravitation>false</autoDataGravitation>\n" +
               "   </config>";
         return XmlConfigHelper.stringToElement(xmlStr);
      }
      else
      {
         String xmlStr =
            "      <buddy enabled=\"true\" poolName=\"testpool\" communicationTimeout=\"600000\">\n" +
                  "         <dataGravitation auto=\"false\" removeOnFind=\"false\" searchBackupTrees=\"false\"/>\n" +
                  "         <locator class=\"org.jboss.cache.buddyreplication.TestBuddyLocator\">\n" +
                  "            <properties>\n" +
                  "               numBuddies = 2\n" +
                  "            </properties>\n" +
                  "         </locator>\n" +
                  "      </buddy>";
         return XmlConfigHelper.stringToElementInCoreNS(xmlStr);
      }
   }

   protected static Element getCacheLoaderConfig(boolean legacy) throws Exception
   {
      if (legacy)
      {
         String xmlStr = "<config>\n" +
               "      <passivation>false</passivation>\n" +
               "      <preload>/foo</preload>\n" +
               "      <shared>true</shared>\n" +
               "      <cacheloader>\n" +
               "         <class>org.jboss.cache.loader.FileCacheLoader</class>\n" +
               "         <properties>\n" +
               "            location=/tmp\n " +
               "         </properties>\n" +
               "         <async>false</async>\n" +
               "         <fetchPersistentState>true</fetchPersistentState>\n" +
               "         <ignoreModifications>true</ignoreModifications>\n" +
               "         <purgeOnStartup>true</purgeOnStartup>\n" +
               "         <singletonStore>\n" +
               "            <enabled>true</enabled>\n" +
               "         </singletonStore>\n" +
               "      </cacheloader> \n " +
               "      <cacheloader>\n" +
               "         <class>org.jboss.cache.loader.jdbm.JdbmCacheLoader</class>\n" +
               "         <properties>\n" +
               "            location=/home/bstansberry\n " +
               "         </properties>\n" +
               "         <async>true</async>\n" +
               "         <fetchPersistentState>false</fetchPersistentState>\n" +
               "         <ignoreModifications>false</ignoreModifications>\n" +
               "         <purgeOnStartup>false</purgeOnStartup>\n" +
               "         <singletonStore>\n" +
               "            <enabled>false</enabled>\n" +
               "         </singletonStore>\n" +
               "      </cacheloader>\n" +
               "   </config>";
         return XmlConfigHelper.stringToElement(xmlStr);

      }
      else
      {
         String xmlStr =
               "   <loaders passivation=\"false\" shared=\"true\">\n" +
                     "      <preload>\n" +
                     "         <node fqn=\"/foo\"/>\n" +
                     "      </preload>\n" +
                     "      <loader class=\"org.jboss.cache.loader.FileCacheLoader\" async=\"false\" fetchPersistentState=\"true\"\n" +
                     "                   ignoreModifications=\"true\" purgeOnStartup=\"true\">\n" +
                     "         <properties>\n" +
                     "             location=/tmp\n " +
                     "         </properties>\n" +
                     "         <singletonStore enabled=\"true\" /> \n" +
                     "      </loader>\n" +
                     "      <loader class=\"org.jboss.cache.loader.jdbm.JdbmCacheLoader\" async=\"true\" fetchPersistentState=\"false\"\n" +
                     "                   ignoreModifications=\"false\" purgeOnStartup=\"false\">\n" +
                     "         <properties>\n" +
                     "             location=/home/bstansberry\n" +
                     "         </properties>\n" +
                     "         <singletonStore enabled=\"false\" /> \n" +
                     "      </loader>\n" +
                     "   </loaders>";
         return XmlConfigHelper.stringToElementInCoreNS(xmlStr);
      }
   }

   protected static Element getEvictionPolicyConfig(boolean legacy) throws Exception
   {
      if (legacy)
      {
         String xmlStr = "   <config>\n" +
               "            <attribute name=\"wakeUpIntervalSeconds\">5</attribute>\n" +
               "            <attribute name=\"eventQueueSize\">1000</attribute>\n" +
               "            <attribute name=\"policyClass\">org.jboss.cache.eviction.LRUPolicy</attribute>\n" +
               "            <region name=\"/_default_\">\n" +
               "               <attribute name=\"maxNodes\">5000</attribute>\n" +
               "               <attribute name=\"timeToLiveSeconds\">1000</attribute>\n" +
               "            </region>\n" +
               "            <region name=\"/org/jboss/data\" policyClass=\"org.jboss.cache.eviction.FIFOPolicy\">\n" +
               "               <attribute name=\"maxNodes\">5000</attribute>\n" +
               "            </region>\n" +
               "            <region name=\"/test\" policyClass=\"org.jboss.cache.eviction.MRUPolicy\">\n" +
               "               <attribute name=\"maxNodes\">10000</attribute>\n" +
               "            </region>\n" +
               "            <region name=\"/maxAgeTest\">\n" +
               "               <attribute name=\"maxNodes\">10000</attribute>\n" +
               "               <attribute name=\"timeToLiveSeconds\">8</attribute>\n" +
               "               <attribute name=\"maxAgeSeconds\">10</attribute>\n" +
               "            </region>\n" +
               "         </config>";
         return XmlConfigHelper.stringToElement(xmlStr);
      }
      else
      {
         String xmlStr =
               "   <eviction wakeUpInterval=\"5000\">\n" +
                     "      <default eventQueueSize=\"1000\" algorithmClass=\"org.jboss.cache.eviction.LRUAlgorithm\">\n" +
                     "         <property name=\"maxNodes\" value=\"5000\"></property>\n" +
                     "         <property name=\"timeToLive\" value=\"1000000\"></property>\n" +
                     "      </default>\n" +
                     "<region name=\"/org/jboss/data\" algorithmClass=\"org.jboss.cache.eviction.FIFOAlgorithm\">\n" +
                     "   <property name=\"maxNodes\" value=\"5000\"></property>\n" +
                     "</region>\n" +
                     "<region name=\"/test/\" algorithmClass=\"org.jboss.cache.eviction.MRUAlgorithm\">\n" +
                     "   <property name=\"maxNodes\" value=\"10000\"></property>\n" +
                     "</region>\n" +
                     "<region name=\"/maxAgeTest/\">\n" +
                     "   <property name=\"maxNodes\" value=\"10000\"></property>\n" +
                     "   <property name=\"timeToLive\" value=\"8000\"></property>\n" +
                     "   <property name=\"maxAge\" value=\"10000\"></property>\n" +
                     "</region>\n" +
                     "   </eviction>";
         return XmlConfigHelper.stringToElementInCoreNS(xmlStr);
      }
   }

   protected static Element getClusterConfig() throws Exception
   {
      String xml =
            "<jgroupsConfig>\n" +
                  "<UDP mcast_addr=\"228.10.10.10\"\n" +
                  "         mcast_port=\"45588\"\n" +
                  "         tos=\"8\"\n" +
                  "         ucast_recv_buf_size=\"20000000\"\n" +
                  "         ucast_send_buf_size=\"640000\"\n" +
                  "         mcast_recv_buf_size=\"25000000\"\n" +
                  "         mcast_send_buf_size=\"640000\"\n" +
                  "         loopback=\"false\"\n" +
                  "         discard_incompatible_packets=\"true\"\n" +
                  "         max_bundle_size=\"64000\"\n" +
                  "         max_bundle_timeout=\"30\"\n" +
                  "         use_incoming_packet_handler=\"true\"\n" +
                  "         ip_ttl=\"2\"\n" +
                  "         enable_bundling=\"false\"\n" +
                  "         enable_diagnostics=\"true\"\n" +
                  "         use_concurrent_stack=\"true\"\n" +
                  "         thread_naming_pattern=\"pl\"\n" +
                  "         thread_pool.enabled=\"true\"\n" +
                  "         thread_pool.min_threads=\"1\"\n" +
                  "         thread_pool.max_threads=\"25\"\n" +
                  "         thread_pool.keep_alive_time=\"30000\"\n" +
                  "         thread_pool.queue_enabled=\"true\"\n" +
                  "         thread_pool.queue_max_size=\"10\"\n" +
                  "         thread_pool.rejection_policy=\"Run\"\n" +
                  "         oob_thread_pool.enabled=\"true\"\n" +
                  "         oob_thread_pool.min_threads=\"1\"\n" +
                  "         oob_thread_pool.max_threads=\"4\"\n" +
                  "         oob_thread_pool.keep_alive_time=\"10000\"\n" +
                  "         oob_thread_pool.queue_enabled=\"true\"\n" +
                  "         oob_thread_pool.queue_max_size=\"10\"\n" +
                  "         oob_thread_pool.rejection_policy=\"Run\"/>\n" +
                  "    <PING timeout=\"2000\" num_initial_members=\"3\"/>\n" +
                  "    <MERGE2 max_interval=\"30000\" min_interval=\"10000\"/>\n" +
                  "    <FD_SOCK/>\n" +
                  "    <FD timeout=\"10000\" max_tries=\"5\" shun=\"true\"/>\n" +
                  "    <VERIFY_SUSPECT timeout=\"1500\"/>\n" +
                  "    <pbcast.NAKACK max_xmit_size=\"60000\"\n" +
                  "                   use_mcast_xmit=\"false\" gc_lag=\"0\"\n" +
                  "                   retransmit_timeout=\"300,600,1200,2400,4800\"\n" +
                  "                   discard_delivered_msgs=\"true\"/>\n" +
                  "    <UNICAST timeout=\"300,600,1200,2400,3600\"/>\n" +
                  "    <pbcast.STABLE stability_delay=\"1000\" desired_avg_gossip=\"50000\"\n" +
                  "                   max_bytes=\"400000\"/>\n" +
                  "    <pbcast.GMS print_local_addr=\"true\" join_timeout=\"5000\"\n" +
                  "                join_retry_timeout=\"2000\" shun=\"false\"\n" +
                  "                view_bundling=\"true\" view_ack_collection_timeout=\"5000\"/>\n" +
                  "    <FRAG2 frag_size=\"60000\"/>\n" +
                  "    <pbcast.STREAMING_STATE_TRANSFER use_reading_thread=\"true\"/>\n" +
                  "    <pbcast.FLUSH timeout=\"0\"/>\n" +
                  "</jgroupsConfig>";
      return XmlConfigHelper.stringToElementInCoreNS(xml);
   }

   protected String getDefaultProperties()
   {
      return "UDP(mcast_addr=224.0.0.36;mcast_port=55566;ip_ttl=32;" +
            "mcast_send_buf_size=150000;mcast_recv_buf_size=80000):" +
            "PING(timeout=1000;num_initial_members=2):" +
            "MERGE2(min_interval=5000;max_interval=10000):" +
            "FD_SOCK:" +
            "VERIFY_SUSPECT(timeout=1500):" +
            "pbcast.NAKACK(gc_lag=50;max_xmit_size=8192;retransmit_timeout=600,1200,2400,4800):" +
            "UNICAST(timeout=600,1200,2400,4800):" +
            "pbcast.STABLE(desired_avg_gossip=20000):" +
            "FRAG(frag_size=8192;down_thread=false;up_thread=false):" +
            "pbcast.GMS(join_timeout=5000;join_retry_timeout=2000;" +
            "shun=false;print_local_addr=true):" +
            "pbcast.STATE_TRANSFER";
   }

   class MockInvocationHandler implements InvocationHandler
   {

      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
      {
         return null;
      }

   }
}
