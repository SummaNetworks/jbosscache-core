/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.InputStream;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, sequential = true, testName = "CacheFactoryTest")
public class CacheFactoryTest
{
   Configuration expected;
   String configFile = "configs/replSync.xml";
   private CacheSPI cache;

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      XmlConfigurationParser parser = new XmlConfigurationParser();
      expected = parser.parseFile(configFile);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
      }
      cache = null;
      expected = null;
   }

   public void testLoadOldConfig()
   {
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache("configs/conf2x/buddy-replication-cache.xml", getClass());
      assert cache.getCacheStatus() == CacheStatus.STARTED : "Should have started";
   }

   public void testFromConfigFileStarted()
   {
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(configFile, getClass());
      // can't test for this anymore since the RuntimeConfig is attached to the running cache
      //assertEquals(expected, cache.getConfiguration());
      assert cache.getCacheStatus() == CacheStatus.STARTED : "Should have started";
      doSimpleConfTests(cache.getConfiguration());
   }

   public void testFromConfigFileUnstarted()
   {
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(configFile, false, getClass());
      // can't test for this anymore since the RuntimeConfig is attached to the running cache
//      assertEquals(expected, cache.getConfiguration());

      assert cache.getCacheStatus() != CacheStatus.STARTED : "Should not have started";

      doSimpleConfTests(cache.getConfiguration());
   }

   public void testFromConfigObjStarted()
   {
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(expected, getClass());

      assert cache.getCacheStatus() == CacheStatus.STARTED : "Should have started";

      doSimpleConfTests(cache.getConfiguration());
   }

   public void testFromConfigObjUnstarted()
   {
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(expected, false, getClass());

      assert cache.getCacheStatus() != CacheStatus.STARTED : "Should not have started";

      doSimpleConfTests(cache.getConfiguration());
   }

   private void doSimpleConfTests(Configuration tc)
   {
      assertEquals(Configuration.CacheMode.REPL_SYNC, tc.getCacheMode());
      assertEquals(10000, tc.getLockAcquisitionTimeout());
      assertEquals(IsolationLevel.REPEATABLE_READ, tc.getIsolationLevel());
      assertEquals(true, tc.isUseRegionBasedMarshalling());
      // test some of the XML content.
      // assertEquals("UDP(ip_mcast=true;ip_ttl=64;loopback=false;mcast_addr=228.1.2.3;mcast_port=48866;mcast_recv_buf_size=80000;mcast_send_buf_size=150000;ucast_recv_buf_size=80000;ucast_send_buf_size=150000):PING(down_thread=false;num_initial_members=3;timeout=2000;up_thread=false):MERGE2(max_interval=20000;min_interval=10000):FD_SOCK:VERIFY_SUSPECT(down_thread=false;timeout=1500;up_thread=false):pbcast.NAKACK(down_thread=false;gc_lag=50;max_xmit_size=8192;retransmit_timeout=600,1200,2400,4800;up_thread=false):UNICAST(down_thread=false;min_threshold=10;timeout=600,1200,2400;window_size=100):pbcast.STABLE(desired_avg_gossip=20000;down_thread=false;up_thread=false):FRAG(down_thread=false;frag_size=8192;up_thread=false):pbcast.GMS(join_retry_timeout=2000;join_timeout=5000;print_local_addr=true;shun=true):pbcast.STATE_TRANSFER(down_thread=true;up_thread=true)", tc.getClusterConfig());
   }

   public void testLifecycle() throws Exception
   {
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(expected, false, getClass());
      assert cache.getCacheStatus() != CacheStatus.STARTED : "Should not have started";
      cache.start();
      assert cache.getCacheStatus() == CacheStatus.STARTED : "Should have started";
      cache.stop();
      assert cache.getCacheStatus() != CacheStatus.STARTED : "Should not have started";
   }

   public void testCreationFromStreamStarted() throws Exception
   {
      InputStream is = getClass().getClassLoader().getResourceAsStream(configFile);
      UnitTestCacheFactory cf = new UnitTestCacheFactory<Object, Object>();
      cache = (CacheSPI) cf.createCache(is, getClass());
      assert cache.getCacheStatus() == CacheStatus.STARTED : "Should have started";
      doSimpleConfTests(cache.getConfiguration());
   }

   public void testCreationFromStream() throws Exception
   {
      InputStream is = getClass().getClassLoader().getResourceAsStream(configFile);
      UnitTestCacheFactory cf = new UnitTestCacheFactory<Object, Object>();
      cache = (CacheSPI) cf.createCache(is, false, getClass());
      assert cache.getCacheStatus() != CacheStatus.STARTED : "Should not have started";
      doSimpleConfTests(cache.getConfiguration());
   }

   public void testComponentsInjected() throws Exception
   {
      UnitTestCacheFactory cf = new UnitTestCacheFactory<Object, Object>();
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache = (CacheSPI) cf.createCache(c, getClass());

      assert TestingUtil.extractField(cache, "regionManager") != null;
      assert TestingUtil.extractField(cache, "notifier") != null;
      assert TestingUtil.extractField(cache, "marshaller") != null;
      assert TestingUtil.extractField(cache, "transactionManager") != null;
      assert TestingUtil.extractField(cache, "transactionTable") != null;
      assert TestingUtil.extractField(cache, "stateTransferManager") != null;
   }
}
