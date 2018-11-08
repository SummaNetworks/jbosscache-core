package org.jboss.cache.profiling;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.parsing.JGroupsStackParser;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Importnat - make sure you inly enable these tests locally!
 */
@Test(groups = "profiling",enabled = false)
public abstract class AbstractProfileTest
{
   protected Cache cache;

   @BeforeTest
   public void setUp()
   {
      Configuration cfg = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC);
      cache = new UnitTestCacheFactory<Object, Object>().createCache(cfg, false, getClass());
   }

   @AfterTest
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public abstract void testReplSync() throws Exception;

   public abstract void testReplAsync() throws Exception;

   public abstract void testReplSyncOptimistic() throws Exception;

   public abstract void testReplAsyncOptimistic() throws Exception;

   public abstract void testReplSyncBR() throws Exception;

   public abstract void testReplAsyncBR() throws Exception;

   public abstract void testReplSyncOptBR() throws Exception;

   public abstract void testReplAsyncOptBR() throws Exception;

   public abstract void testStateTransfer() throws Exception;

   public abstract void testStartup() throws Exception;

   public abstract void testCacheLoading() throws Exception;

   public abstract void testPassivation() throws Exception;

   public String getJGroupsConfig() throws Exception
   {
      String udp = "  <jgroupsConfig>\n" +
            "         <UDP discard_incompatible_packets=\"true\" \n" +
            "              enable_bundling=\"true\" \n" +
            "              enable_diagnostics=\"false\" \n" +
            "              ip_ttl=\"2\"\n" +
            "              loopback=\"true\" \n" +
            "              max_bundle_size=\"64000\" \n" +
            "              max_bundle_timeout=\"30\" \n" +
            "              mcast_addr=\"232.5.5.5\"\n" +
            "              mcast_port=\"45588\" \n" +
            "              mcast_recv_buf_size=\"100000000\" \n" +
            "              mcast_send_buf_size=\"640000\"\n" +
            "              oob_thread_pool.enabled=\"true\" \n" +
            "              oob_thread_pool.keep_alive_time=\"10000\" \n" +
            "              oob_thread_pool.max_threads=\"20\"\n" +
            "              oob_thread_pool.min_threads=\"8\" \n" +
            "              oob_thread_pool.queue_enabled=\"false\"\n" +
            "              oob_thread_pool.rejection_policy=\"Run\" \n" +
            "              thread_naming_pattern=\"pl\" \n" +
            "              thread_pool.enabled=\"true\"\n" +
            "              thread_pool.keep_alive_time=\"10000\" \n" +
            "              thread_pool.max_threads=\"2\" \n" +
            "              thread_pool.min_threads=\"1\"\n" +
            "              thread_pool.queue_enabled=\"true\" \n" +
            "              thread_pool.queue_max_size=\"1000000\" \n" +
            "              thread_pool.rejection_policy=\"discard\"\n" +
            "              tos=\"8\" ucast_recv_buf_size=\"20000000\" \n" +
            "              ucast_send_buf_size=\"640000\" \n" +
            "              use_concurrent_stack=\"true\"/>\n" +
            "         <PING num_initial_members=\"3\" timeout=\"2000\"/>\n" +
            "         <MERGE2 max_interval=\"30000\" min_interval=\"10000\"/>\n" +
            "         <FD_SOCK/>\n" +
            "         <!--FD max_tries=\"5\" shun=\"true\" timeout=\"10000\"/-->\n" +
            "         <VERIFY_SUSPECT timeout=\"1500\"/>\n" +
            "         <pbcast.NAKACK discard_delivered_msgs=\"true\" gc_lag=\"0\" retransmit_timeout=\"300,600,1200,2400,4800\"\n" +
            "                        use_mcast_xmit=\"false\"/>\n" +
            "         <UNICAST timeout=\"300,600,1200,2400,3600\"/>\n" +
            "         <pbcast.STABLE desired_avg_gossip=\"50000\" max_bytes=\"400000\" stability_delay=\"1000\"/>\n" +
            "         <pbcast.GMS join_timeout=\"5000\" print_local_addr=\"true\" shun=\"false\" view_ack_collection_timeout=\"5000\"\n" +
            "                     view_bundling=\"true\"/>\n" +
            "         <FC max_credits=\"500000\" min_threshold=\"0.2\"\n" +
            "              max_block_time=\"20000\" />\n" +
            "         <FRAG2 frag_size=\"60000\"/>\n" +
            "         <pbcast.STREAMING_STATE_TRANSFER use_reading_thread=\"true\"/>\n" +
            "         <pbcast.FLUSH timeout=\"0\"/>\n" +
            "      </jgroupsConfig>";

      String tcp = "   <jgroupsConfig>\n" +
            "         <TCP discard_incompatible_packets=\"true\" enable_bundling=\"true\" enable_diagnostics=\"true\"\n" +
            "              enable_unicast_bundling=\"true\" loopback=\"false\" max_bundle_size=\"64000\" max_bundle_timeout=\"30\"\n" +
            "              oob_thread_pool.enabled=\"true\" oob_thread_pool.keep_alive_time=\"10000\" oob_thread_pool.max_threads=\"4\"\n" +
            "              oob_thread_pool.min_threads=\"2\" oob_thread_pool.queue_enabled=\"false\" oob_thread_pool.queue_max_size=\"10\"\n" +
            "              oob_thread_pool.rejection_policy=\"Run\" recv_buf_size=\"20000000\" thread_naming_pattern=\"pl\"\n" +
            "              thread_pool.enabled=\"true\" thread_pool.keep_alive_time=\"30000\" thread_pool.max_threads=\"4\"\n" +
            "              thread_pool.min_threads=\"1\" thread_pool.queue_enabled=\"true\" thread_pool.queue_max_size=\"50000\"\n" +
            "              thread_pool.rejection_policy=\"discard\" use_concurrent_stack=\"true\" use_incoming_packet_handler=\"true\"\n" +
            "              use_send_queues=\"false\" />\n" +
            "         <MPING mcast_addr=\"228.10.10.10\" num_initial_members=\"1\" timeout=\"2000\"/>\n" +
            "         <MERGE2 max_interval=\"30000\" min_interval=\"10000\"/>\n" +
            "         <FD_SOCK/>\n" +
            "         <FD max_tries=\"5\" shun=\"true\" timeout=\"10000\"/>\n" +
            "         <VERIFY_SUSPECT timeout=\"1500\"/>\n" +
            "         <pbcast.NAKACK discard_delivered_msgs=\"true\" gc_lag=\"0\" retransmit_timeout=\"300,600,1200,2400,4800\"\n" +
            "                        use_mcast_xmit=\"false\"/>\n" +
            "         <pbcast.STABLE desired_avg_gossip=\"50000\" max_bytes=\"400000\" stability_delay=\"1000\"/>\n" +
            "         <pbcast.GMS join_timeout=\"5000\" print_local_addr=\"true\" shun=\"false\"\n" +
            "                     view_ack_collection_timeout=\"5000\" view_bundling=\"true\"/>\n" +
            "         <FC max_credits=\"5000000\" min_threshold=\"0.20\"/>\n" +
            "         <FRAG2 frag_size=\"60000\"/>\n" +
            "         <pbcast.STREAMING_STATE_TRANSFER/>\n" +
            "         <pbcast.FLUSH timeout=\"0\"/>\n" +
            "      </jgroupsConfig>";

      return new JGroupsStackParser().parseClusterConfigXml(XmlConfigHelper.stringToElement(udp));
   }
}
