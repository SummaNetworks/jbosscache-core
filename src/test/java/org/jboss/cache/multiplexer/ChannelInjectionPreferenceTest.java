package org.jboss.cache.multiplexer;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.RuntimeConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.Channel;
import org.jgroups.JChannel;
import org.jgroups.conf.XmlConfigurator;
import org.jgroups.conf.ConfiguratorFactory;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests that JBC prefers an injected Channel to creating one via
 * a configured JChannelFactory and stack name.
 *
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7422 $
 */
@Test(groups = {"functional", "jgroups"}, enabled = true, sequential = true, testName = "multiplexer.ChannelInjectionPreferenceTest")
public class ChannelInjectionPreferenceTest
{
   private MultiplexerTestHelper muxHelper;
   private Cache cache;
   private boolean cacheStarted;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      muxHelper = new MultiplexerTestHelper();
      Configuration config = UnitTestConfigurationFactory.getEmptyConfiguration();
      config.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      cache = new UnitTestCacheFactory<Object, Object>().createCache(config, false, getClass());
      cacheStarted = false;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      try
      {
         if (cacheStarted && cache != null)
         {
            TestingUtil.killCaches(cache);
            cache = null;
         }
      }
      finally
      {
         if (muxHelper != null)
         {
            muxHelper.tearDown();
            muxHelper = null;
         }
      }
   }

   public void testChannelInjectionPreference() throws Exception
   {
      muxHelper.configureCacheForMux(cache);

      Channel channel = new JChannel(new UnitTestCacheFactory().mangleClusterConfigurationUdp(
              getClusterConfigFromProperties(JChannel.DEFAULT_PROTOCOL_STACK)));

      RuntimeConfig rtcfg = cache.getConfiguration().getRuntimeConfig();
      rtcfg.setChannel(channel);

      // Start shouldn't fail and we shouldn't be using mulitplexer
      checkStart(false, false);

      assertEquals("Injected channel used", channel, rtcfg.getChannel());
   }

   private void checkStart(boolean expectFail, boolean expectMux)
   {
      try
      {
         cache.start();
         cacheStarted = true;
         if (expectFail)
         {
            fail("Start did not fail as expected");
         }

         if (expectMux)
         {
            assertTrue("Cache is using mux", cache.getConfiguration().isUsingMultiplexer());
         }
         else
         {
            assertFalse("Cache is not using mux ", cache.getConfiguration().isUsingMultiplexer());
         }
      }
      catch (Exception e)
      {
         if (!expectFail)
         {
            fail("Caught exception starting cache " + e.getLocalizedMessage());
         }
      }

   }


   /**
    * Helper method that takes a <b>JGroups</b> configuration file and creates an old-style JGroups config {@link String} that can be used
    * in {@link org.jboss.cache.config.Configuration#setClusterConfig(String)}.  Note that expressions
    * in the file - such as <tt>${jgroups.udp.mcast_port:45588}</tt> are expanded out accordingly.
    *
    * @param properties config properties
    * @return a String
    */
   public static String getClusterConfigFromProperties(String properties)
   {
      try
      {
         XmlConfigurator conf = XmlConfigurator.getInstance(ConfiguratorFactory.getConfigStream(properties));
         String tmp = conf.getProtocolStackString();
         // parse this string for ${} substitutions
         // Highly crappy approach!!
         tmp = tmp.replace("${jgroups.udp.mcast_addr:228.10.10.10}", "228.10.10.10");
         tmp = tmp.replace("${jgroups.udp.mcast_port:45588}", "45588");
         tmp = tmp.replace("${jgroups.udp.ip_ttl:2}", "2");
         return tmp;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Problems with properties " + properties, e);
      }
   }
}
