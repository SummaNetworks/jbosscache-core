package org.jboss.cache.multiplexer;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests that JBC starts correctly even if the multiplexer
 * configuration is incorrect.
 *
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7422 $
 */
@Test(groups = {"functional", "jgroups"}, enabled = true, sequential = true, testName = "multiplexer.BadMuxConfigTest")
public class BadMuxConfigTest
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
         if (cache != null)
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

   public void testValidMuxConfig() throws Exception
   {
      // TODO: 2.2.0: this test hangs on forever. Uncomment it and make it work...
//      muxHelper.configureCacheForMux(cache);
//
//      checkStart(false, true);
   }

   public void testMissingMuxChannelFactory() throws Exception
   {
      muxHelper.configureCacheForMux(cache);
      cache.getConfiguration().getRuntimeConfig().setMuxChannelFactory(null);

      checkStart(false, false);
   }

   public void testInvalidStackName() throws Exception
   {
      muxHelper.configureCacheForMux(cache);
      cache.getConfiguration().setMultiplexerStack("bogus");

      checkStart(true, false);
   }

   public void testMissingStackName() throws Exception
   {
      muxHelper.configureCacheForMux(cache);
      cache.getConfiguration().setMultiplexerStack(null);

      checkStart(true, false);
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
}
