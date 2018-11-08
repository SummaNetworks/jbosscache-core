/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.passivation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeActivated;
import org.jboss.cache.notifications.annotation.NodeLoaded;
import org.jboss.cache.notifications.annotation.NodePassivated;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.notifications.event.NodeEvent;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Ben Wang, Feb 11, 2004
 */
@Test(groups = {"functional"}, sequential = true, testName = "passivation.LocalPassivationIntegrationTest")
public class LocalPassivationIntegrationTest
{
   CacheSPI<String, String> cache;
   protected final static Log log = LogFactory.getLog(LocalPassivationIntegrationTest.class);
   long wakeupIntervalMillis = 0;
   PassivationListener listener_;
   private static final int LISTENER_WAIT_TIME = 200; // needed since notifications are delivered asynchronously

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(new XmlConfigurationParser().parseFile("configs/local-passivation.xml"), false, getClass());
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().getCacheLoaderConfig().getFirstCacheLoaderConfig().setClassName(DummyInMemoryCacheLoader.class.getName());
      cache.getConfiguration().setUseRegionBasedMarshalling(true);

      cache.start();

      listener_ = new PassivationListener();

      cache.getNotifier().addCacheListener(listener_);
      listener_.resetCounter();

      wakeupIntervalMillis = cache.getConfiguration().getEvictionConfig().getWakeupInterval();
      if (wakeupIntervalMillis <= 0)
      {
         fail("testEviction(): eviction thread wake up interval is illegal " + wakeupIntervalMillis);
      }
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   /**
    */
   public void testActivationEvent() throws Exception
   {
      String rootStr = "/org/jboss/test/data/";
      String str = rootStr + "0";
      cache.removeNode(Fqn.ROOT);
      listener_.resetCounter();

      cache.put(str, str, str);

      TestingUtil.sleepThread(20000);
      assertFalse("UnversionedNode should not exist", cache.exists(str));
      String val = cache.get(str, str);
      assertNotNull("DataNode should be activated ", val);
      TestingUtil.sleepThread(LISTENER_WAIT_TIME);
      assertEquals("Eviction counter ", 1, listener_.getCounter());
   }

   @CacheListener
   public class PassivationListener
   {
      int counter = 0;
      int loadedCounter = 0;

      public int getCounter()
      {
         return counter;
      }

      public void resetCounter()
      {
         counter = 0;
         loadedCounter = 0;
      }

      @NodeActivated
      public void nodeActivated(NodeEvent ne)
      {
         if (!ne.isPre())
         {
            counter++;
         }
      }

      @NodePassivated
      public void nodePassivated(NodeEvent ne)
      {
      }

      @NodeLoaded
      public void nodeLoaded(Event e)
      {
         if (!e.isPre())
         {
            loadedCounter++;
         }
      }

   }
}
