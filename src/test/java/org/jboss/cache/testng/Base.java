package org.jboss.cache.testng;

import org.testng.annotations.*;
import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "Base")
public class Base
{
   Cache cache;

   @BeforeClass
   public void beforeTest()
   {
//      System.out.println("Base.beforeTest");
      Configuration config = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC, true);
      cache = new UnitTestCacheFactory().createCache(config, true, getClass());
      System.out.println("Base:::" + cache.getConfiguration().getClusterConfig());
   }

   @AfterClass
   public void afterTest()
   {
//      System.out.println("Base.afterTest");
      cache.stop();
   }

   @BeforeMethod
   public void beforeMethod()
   {
//      System.out.println("Base.beforeMethod");
   }

   public void testNoTest() {}
   protected String getThreadName()
   {
      return "[" + getClass() + " ************ -> " + Thread.currentThread().getName() + "] ";
   }
}

//Base:::UDP(discard_incompatible_packets=true;enable_bundling=false;enable_diagnostics=true;ip_ttl=2;loopback=false;m
//ax_bundle_size=64000;max_bundle_timeout=30;mcast_addr=228.10.10.11;mcast_port=45589;mcast_recv_buf_size=25000000;mca
//st_send_buf_size=640000;oob_thread_pool.enabled=true;oob_thread_pool.keep_alive_time=10000;oob_thread_pool.max_threa
//ds=4;oob_thread_pool.min_threads=1;oob_thread_pool.queue_enabled=false;oob_thread_pool.queue_max_size=10;oob_thread_
//pool.rejection_policy=Run;thread_naming_pattern=pl;thread_pool.enabled=true;thread_pool.keep_alive_time=30000;thread
//_pool.max_threads=25;thread_pool.min_threads=1;thread_pool.queue_enabled=false;thread_pool.queue_max_size=100;thread
//_pool.rejection_policy=Run;tos=8;ucast_recv_buf_size=20000000;ucast_send_buf_size=640000;use_concurrent_stack=true;u
//se_incoming_packet_handler=true):PING(num_initial_members=3;timeout=2000):MERGE2(max_interval=30000;min_interval=100
//00):FD_SOCK:FD(max_tries=2;shun=true;timeout=1000):VERIFY_SUSPECT(timeout=250):pbcast.NAKACK(discard_delivered_msgs=
//true;gc_lag=0;retransmit_timeout=300,600,900,1200;use_mcast_xmit=false):UNICAST(timeout=300,600,900,1200):pbcast.STA
//BLE(desired_avg_gossip=50000;max_bytes=400000;stability_delay=1000):pbcast.GMS(join_timeout=1000;print_local_addr=fa
//lse;shun=false;view_ack_collection_timeout=1000;view_bundling=true):FRAG2(frag_size=60000):pbcast.STREAMING_STATE_TR
//ANSFER:pbcast.FLUSH(timeout=0)
//Other:::UDP(discard_incompatible_packets=true;enable_bundling=false;enable_diagnostics=true;ip_ttl=2;loopback=false;
//max_bundle_size=64000;max_bundle_timeout=30;mcast_addr=228.10.10.12;mcast_port=45590;mcast_recv_buf_size=25000000;mc
//ast_send_buf_size=640000;oob_thread_pool.enabled=true;oob_thread_pool.keep_alive_time=10000;oob_thread_pool.max_thre
//ads=4;oob_thread_pool.min_threads=1;oob_thread_pool.queue_enabled=false;oob_thread_pool.queue_max_size=10;oob_thread
//_pool.rejection_policy=Run;thread_naming_pattern=pl;thread_pool.enabled=true;thread_pool.keep_alive_time=30000;threa
//d_pool.max_threads=25;thread_pool.min_threads=1;thread_pool.queue_enabled=false;thread_pool.queue_max_size=100;threa
//d_pool.rejection_policy=Run;tos=8;ucast_recv_buf_size=20000000;ucast_send_buf_size=640000;use_concurrent_stack=true;
//use_incoming_packet_handler=true):PING(num_initial_members=3;timeout=2000):MERGE2(max_interval=30000;min_interval=10
//000):FD_SOCK:FD(max_tries=2;shun=true;timeout=1000):VERIFY_SUSPECT(timeout=250):pbcast.NAKACK(discard_delivered_msgs
//=true;gc_lag=0;retransmit_timeout=300,600,900,1200;use_mcast_xmit=false):UNICAST(timeout=300,600,900,1200):pbcast.ST
//ABLE(desired_avg_gossip=50000;max_bytes=400000;stability_delay=1000):pbcast.GMS(join_timeout=1000;print_local_addr=f
//alse;shun=false;view_ack_collection_timeout=1000;view_bundling=true):FRAG2(frag_size=60000):pbcast.STREAMING_STATE_T
//RANSFER:pbcast.FLUSH(timeout=0)