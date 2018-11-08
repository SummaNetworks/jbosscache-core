package org.jboss.cache.loader;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.interceptors.OrderedSynchronizationHandler;
import org.jboss.cache.loader.tcp.TcpCacheServer;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeCreated;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.transaction.Synchronization;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests the TcpDelegatingCacheLoader
 *
 * @author Bela Ban
 * @version $Id: TcpCacheLoaderTest.java 7573 2009-01-22 21:17:32Z mircea.markus $
 */
@Test(groups = "functional", testName = "loader.TcpCacheLoaderTest")
public class TcpCacheLoaderTest extends CacheLoaderTestsBase
{
   protected static final String TCP_CACHE_SERVER_HOST = "127.0.0.1";
   protected static final int TCP_CACHE_SERVER_PORT = 12121;
   protected static final int CACHE_SERVER_RESTART_DELAY_MS = 250;
   protected static final int TCP_CACHE_LOADER_TIMEOUT_MS = 1000;
   protected static int START_COUNT = 0;
   static volatile TcpCacheServer cacheServer = null;

   @Override
   @BeforeClass
   public void preCreate()
   {
      if (cacheServer != null) stopCacheServer();
      startCacheServer();
   }

   private static void startCacheServer()
   {
      final CountDownLatch startedSignal = new CountDownLatch(1);

      Thread t = new Thread()
      {
         public void run()
         {
            try
            {
               cacheServer = new TcpCacheServer();
               cacheServer.setBindAddress(TCP_CACHE_SERVER_HOST);
               cacheServer.setPort(TCP_CACHE_SERVER_PORT);
               Configuration config = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
               // disable eviction!!
               config.setEvictionConfig(null);
               CacheSPI cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(config, getClass());
               cacheServer.setCache(cache);
               cacheServer.create();
               cacheServer.start();
               START_COUNT++;
               startedSignal.countDown();
            }
            catch (Exception ex)
            {
               ex.printStackTrace();
            }
         }

      };
      t.setDaemon(true);
      t.start();

      // Wait for the cache server to start up.
      boolean started = false;
      try
      {
         started = startedSignal.await(120, TimeUnit.SECONDS);
      }
      catch (InterruptedException e)
      {
         // do nothing
      }

      if (!started)
      {
         // the TcpCacheServer was unable to start up for some reason!!
         throw new RuntimeException("Unable to start the TcpCacheServer after 120 seconds!!");
      }
   }

   @AfterClass
   public static void stopCacheServer()
   {
      if (cacheServer != null)
      {
         cacheServer.stop();
         cacheServer = null;
      }
   }

   @AfterMethod
   public void removeRestarters()
   {
      if (cache != null)
      {
         Set<Object> restarters = new HashSet<Object>();
         for (Object listener : cache.getCacheListeners())
         {
            if (listener instanceof CacheServerRestarter) restarters.add(listener);
         }
         try
         {
            for (Object restarter : restarters) cache.removeCacheListener(restarter);
         }
         catch (Exception ignored)
         {
            // ignored
         }
      }
   }

   protected static void restartCacheServer()
   {
      stopCacheServer();
      startCacheServer();
   }

   @Override
   public void testPartialLoadAndStore()
   {
      // do nothing
   }

   @Override
   public void testBuddyBackupStore()
   {
      // do nothing
   }

   protected void configureCache(CacheSPI cache) throws Exception
   {
      CacheLoaderConfig clc = new CacheLoaderConfig();
      TcpDelegatingCacheLoaderConfig tcpCfg = new TcpDelegatingCacheLoaderConfig(TCP_CACHE_SERVER_HOST, TCP_CACHE_SERVER_PORT, TCP_CACHE_LOADER_TIMEOUT_MS);
      tcpCfg.setReconnectWaitTime(CACHE_SERVER_RESTART_DELAY_MS);
      tcpCfg.setFetchPersistentState(false);
      clc.addIndividualCacheLoaderConfig(tcpCfg);
      cache.getConfiguration().setCacheLoaderConfig(clc);
   }

   // restart tests
   public void testCacheServerRestartMidCall() throws Exception
   {
      CacheServerRestarter restarter = new CacheServerRestarter();
      restarter.restart = true;
      cache.addCacheListener(restarter);
      int oldStartCount = START_COUNT;
      // a restart of the cache server will happen before the cache loader interceptor is called.
      cache.put(FQN, "key", "value");

      assert oldStartCount + 1 == START_COUNT : "Cache server should have restarted!";
      assert loader.get(FQN).equals(Collections.singletonMap("key", "value"));
   }

   public void testCacheServerDelayedRestartMidCall() throws Exception
   {
      CacheServerRestarter restarter = new CacheServerRestarter();
      restarter.restart = false;
      restarter.delayedRestart = true;
      restarter.startAfter = CACHE_SERVER_RESTART_DELAY_MS;
      cache.addCacheListener(restarter);
      int oldStartCount = START_COUNT;

      // the cache server will STOP before the cache laoder interceptor is called.
      // it will be restarted in a separate thread, startAfter millis later.
      // this should be less than the TcpCacheLoader timeout.
      cache.put(FQN, "key", "value");

      assert oldStartCount < START_COUNT : "Cache server should have restarted! old = " + oldStartCount + " and count = " + START_COUNT;
      assert loader.get(FQN).equals(Collections.singletonMap("key", "value"));
   }

   public void testCacheServerTimeoutMidCall() throws Exception
   {
      CacheServerRestarter restarter = new CacheServerRestarter();
      restarter.restart = false;
      restarter.delayedRestart = true;
      restarter.startAfter = -1;
      cache.addCacheListener(restarter);
      int oldStartCount = START_COUNT;

      // the cache server will STOP before the cache laoder interceptor is called.
      // it will be restarted in a separate thread, startAfter millis later.
      // this should be less than the TcpCacheLoader timeout.
      try
      {
         cache.put(FQN, "key", "value");
         assert false : "Should have failed";
      }
      catch (CacheException expected)
      {

      }

      assert oldStartCount == START_COUNT : "Cache server should NOT have restarted!";
      // start the TCP server again
      startCacheServer();
      assert loader.get(FQN) == null;
   }

   public void testCacheServerRestartMidTransaction() throws Exception
   {
      int oldStartCount = START_COUNT;
      cache.getTransactionManager().begin();
      cache.put(FQN, "key", "value");
      restartCacheServer();
      cache.put(FQN, "key2", "value2");
      cache.getTransactionManager().commit();

      Map m = new HashMap();
      m.put("key", "value");
      m.put("key2", "value2");

      assert oldStartCount < START_COUNT : "Cache server should have restarted!";
      assert loader.get(FQN).equals(m);
   }

   public void testCacheServerRestartMidTransactionAfterPrepare() throws Exception
   {
      int oldStartCount = START_COUNT;
      cache.getTransactionManager().begin();

      cache.put(FQN, "key", "value");
      cache.put(FQN, "key2", "value2");

      GlobalTransaction gtx = cache.getTransactionTable().get(cache.getTransactionManager().getTransaction());
      OrderedSynchronizationHandler osh = cache.getTransactionTable().get(gtx).getOrderedSynchronizationHandler();

//      OrderedSynchronizationHandler.getInstance(cache.getTransactionManager().getTransaction()).registerAtTail(
      osh.registerAtTail(
            new Synchronization()
            {

               public void beforeCompletion()
               {
                  // this will be called after the cache's prepare() phase.  Restart the cache server.
                  restartCacheServer();
               }

               public void afterCompletion(int i)
               {
                  // do nothing
               }
            }
      );

      cache.getTransactionManager().commit();

      Map m = new HashMap();
      m.put("key", "value");
      m.put("key2", "value2");

      assert oldStartCount + 1 == START_COUNT : "Cache server should have restarted!";
      assert loader.get(FQN).equals(m);

   }

   @CacheListener
   public static class CacheServerRestarter
   {
      boolean restart;
      boolean delayedRestart;
      int startAfter;

      @NodeCreated
      public void restart(Event e) throws InterruptedException
      {
         if (e.isPre())
         {
            if (restart)
            {
               restartCacheServer();
            }
            else if (delayedRestart)
            {
               stopCacheServer();
            }
         }
         else
         {
            if (delayedRestart && startAfter>0)
            {
               Thread.sleep(startAfter);
               startCacheServer();
            }
         }
      }
   }
}
