package org.jboss.cache.config.parsing;

import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.jboss.cache.config.parsing.JGroupsStackParser;

/**
 * Tester class for {@link JGroupsStackParser}
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "unit", testName = "config.parsing.JGroupsStackParserTest")
public class JGroupsStackParserTest
{
   private JGroupsStackParser parser = new JGroupsStackParser();

   public void testSimpleParse() throws Exception
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
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);
      String result = parser.parseClusterConfigXml(element);
      assert result.indexOf("ucast_recv_buf_size=20000000") > 0;
      assert result.indexOf("num_initial_members=3") > 0;
      assert result.indexOf("min_interval=10000") > 0;
   }

   public void testParsingEmptyConfig() throws Exception
   {
      String xml = "<jgroupsConfig/>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);
      String result = parser.parseClusterConfigXml(element);
   }
}
