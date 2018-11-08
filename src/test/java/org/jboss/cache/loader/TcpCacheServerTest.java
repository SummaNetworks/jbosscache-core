package org.jboss.cache.loader;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import static org.jboss.cache.factories.UnitTestConfigurationFactory.buildSingleCacheLoaderConfig;
import org.jboss.cache.jmx.CacheJmxWrapper;
import org.jboss.cache.loader.tcp.TcpCacheServer;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.net.UnknownHostException;

/**
 * Tests various ways of setting up the TcpCacheServer
 *
 * @author Brian Stansberry
 * @version $Id: TcpCacheServerTest.java 7467 2009-01-14 15:13:00Z manik.surtani@jboss.com $
 */
@Test(groups = {"functional"}, enabled = true, testName = "loader.TcpCacheServerTest")
public class TcpCacheServerTest
{
   static TcpCacheServer cache_server = null;
   private static final String SERVER_IP = "127.0.0.1";
   private static final int SERVER_PORT = 13131;

   private CacheSPI<Object, Object> cache;
   private CacheLoader loader;

   static
   {
      Runtime.getRuntime().addShutdownHook(new Thread()
      {
         public void run()
         {
            if (cache_server != null)
            {
               cache_server.stop();
            }
         }
      });
   }

   private void createCacheAndLoader() throws Exception
   {
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.LOCAL);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      c.setCacheLoaderConfig(getCacheLoaderConfig());
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());

      cache.start();
      loader = cache.getCacheLoaderManager().getCacheLoader();
   }

   protected CacheLoaderConfig getCacheLoaderConfig() throws Exception
   {
      return buildSingleCacheLoaderConfig(false, null, "org.jboss.cache.loader.TcpDelegatingCacheLoader",
            "host=" + SERVER_IP + "\nport=" + SERVER_PORT, false, true, true, false, false);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache != null)
      {
         cache.stop();
         cache.destroy();
         cache = null;
      }

      if (cache_server != null)
      {
         cache_server.stop();
         cache_server = null;
      }
   }


   private static void createTcpCacheServer() throws UnknownHostException
   {
      cache_server = new TcpCacheServer();
      cache_server.setBindAddress(SERVER_IP);
      cache_server.setPort(SERVER_PORT);
   }

   private void startTcpCacheServer()
   {
      Thread runner = new Thread()
      {
         public void run()
         {
            try
            {
               cache_server.create();
               cache_server.start();
            }
            catch (Exception ex)
            {
               ex.printStackTrace();
            }
         }
      };

      runner.setDaemon(true);
      runner.start();

      // give the tcp cache server time to start up
      TestingUtil.sleepThread(2000);
   }

   public void testInjectConfigFilePath() throws Exception
   {
      createTcpCacheServer();
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      CacheSPI cacheSPI = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(conf, getClass());
      cache_server.setCache(cacheSPI);
      startTcpCacheServer();
      createCacheAndLoader();
      cacheCheck();
      usabilityCheck();
   }

   public void testInjectCache() throws Exception
   {
      createTcpCacheServer();
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      CacheSPI cacheSPI = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(conf, getClass());
      cache_server.setCache(cacheSPI);
      startTcpCacheServer();
      createCacheAndLoader();
      cacheCheck();
      usabilityCheck();
   }

   public void testInjectCacheJmxWrapper() throws Exception
   {
      createTcpCacheServer();
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      CacheSPI cacheSPI = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(conf, getClass());
      CacheJmxWrapper wrapper = new CacheJmxWrapper<Object, Object>(cacheSPI);
      wrapper.start();
      cache_server.setCacheJmxWrapper(wrapper);
      startTcpCacheServer();
      createCacheAndLoader();
      cacheCheck();
      usabilityCheck();
   }

   private void cacheCheck()
   {
      Cache c = cache_server.getCache();
      assertNotNull("Cache exists", c);
      Configuration config = c.getConfiguration();
      // check a couple properties
      assertEquals("Correct mode", Configuration.CacheMode.LOCAL, config.getCacheMode());
      assertEquals("Correct cluster name", "JBossCache-Cluster", config.getClusterName());
   }

   private void usabilityCheck() throws Exception
   {
      Fqn fqn = Fqn.fromString("/key");
      assertFalse("Fqn does not exist in loader", loader.exists(fqn));

      /* put(Fqn,Object,Object) and get(Fqn,Object) */
      Object oldVal;
      oldVal = loader.put(fqn, "one", "two");
      assertNull("oldVal is null", oldVal);

      assertEquals("Got value from cache", "two", cache.get(fqn, "one"));
   }

}
