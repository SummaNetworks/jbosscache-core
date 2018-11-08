/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.passivation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.notifications.annotation.NodeActivated;
import org.jboss.cache.notifications.annotation.NodePassivated;
import org.jboss.cache.notifications.event.NodeEvent;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Tests that the TreeCacheListener implementation used by EJB3 SFSBs works.
 *
 * @author Brian Stansberry
 */
@Test(groups = {"functional"}, sequential = true, testName = "passivation.PassivationActivationCallbacksTestCase")
public class PassivationActivationCallbacksTestCase
{
   private static final Fqn BASE = Fqn.fromString("/base");
   private static final Log log = LogFactory.getLog(PassivationActivationCallbacksTestCase.class);

   //Cache Loader fields
   private CacheSPI<String, String> cache;
   private CacheLoader loader = null;
   private CacheListener listener = null;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(false, getClass());
      cache.getConfiguration().setCacheMode("local");
      configureEviction();
      configureCacheLoader();
      cache.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache.getConfiguration().setIsolationLevel(IsolationLevel.SERIALIZABLE);
      listener = new CacheListener();
      cache.addCacheListener(listener);
      cache.create();
      cache.start();
      loader = cache.getCacheLoaderManager().getCacheLoader();
   }

   protected void configureCacheLoader() throws Exception
   {
      CacheLoaderConfig clc = new CacheLoaderConfig();
      clc.setPassivation(true);
      clc.setShared(false);
      clc.setPreload("/");

      CacheLoaderConfig.IndividualCacheLoaderConfig dummyConfig = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      dummyConfig.setAsync(false);
      dummyConfig.setFetchPersistentState(true);
      dummyConfig.setIgnoreModifications(false);
      dummyConfig.setClassName(DummyInMemoryCacheLoader.class.getName());
      clc.addIndividualCacheLoaderConfig(dummyConfig);
      cache.getConfiguration().setCacheLoaderConfig(clc);
   }

   protected void configureEviction() throws Exception
   {
      EvictionConfig ec = new EvictionConfig();
      ec.setWakeupInterval(1000);

      LRUAlgorithmConfig lru = new LRUAlgorithmConfig();
      lru.setMaxNodes(0);
      lru.setTimeToLive(5000);
      ec.setDefaultEvictionRegionConfig(new EvictionRegionConfig(Fqn.ROOT, lru));

      lru = new LRUAlgorithmConfig();
      lru.setMaxNodes(0);
      lru.setTimeToLive(1000);
      ec.addEvictionRegionConfig(new EvictionRegionConfig(BASE, lru));

      cache.getConfiguration().setEvictionConfig(ec);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      cache.removeNode(Fqn.ROOT);
      loader.remove(Fqn.fromString("/"));
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testSimpleLifecycle() throws Exception
   {
      Fqn fqn = Fqn.fromRelativeElements(BASE, "bean1");
      cache.put(fqn, "bean", "A bean");

      //TestingUtil.sleepThread(3000);
      cache.evict(fqn, false);

      assertNull("No activation exception", listener.activationException);
      assertNull("No passivation exception", listener.passivationException);
      assertTrue(listener.passivated.contains(fqn));
      assertFalse(listener.activated.contains(fqn));

      Object obj = cache.get(fqn, "bean");
      assertEquals("Got bean", "A bean", obj);

      if (listener.activationException != null) throw listener.activationException;
      if (listener.passivationException != null) throw listener.passivationException;

      assertNull("No activation exception", listener.activationException);
      assertNull("No passivation exception", listener.passivationException);
      assertTrue(listener.activated.contains(fqn));
   }

   /**
    * Mimics the CacheListener used by EJB3 SFSBs.
    */
   @org.jboss.cache.notifications.annotation.CacheListener
   public class CacheListener
   {
      protected Log log = LogFactory.getLog(CacheListener.class);

      protected Set<Fqn> passivated = new HashSet<Fqn>();
      protected Set<Fqn> activated = new HashSet<Fqn>();
      protected Exception passivationException;
      protected Exception activationException;

      @NodeActivated
      public void nodeActivated(NodeEvent e)
      {
         if (e.isPre())
            return; // we are not interested in preActivate event

         if (!e.getFqn().isChildOrEquals(BASE))
            return; // don't care about fqn that doesn't belong to me.

         Object bean = null;
         try
         {
            bean = cache.get(e.getFqn(), "bean");
         }
         catch (CacheException ex)
         {
            log.error("nodeActivate(): can't retrieve bean instance from: " + e.getFqn() + " with exception: " + ex);
            activationException = ex;
            return;
         }
         if (bean == null)
         {
            activationException = new IllegalStateException("nodeActivate(): null bean instance.");
            throw (IllegalStateException) activationException;
         }

         if (log.isTraceEnabled())
         {
            log.trace("nodeActivate(): saw postActivate event on fqn: " + e.getFqn());
         }

         activated.add(e.getFqn());
      }

      @NodePassivated
      public void nodePassivated(NodeEvent e)
      {
         if (!e.isPre())
            return; // we are not interested in postPassivate event
         Fqn fqn = e.getFqn();
         if (!fqn.isChildOrEquals(BASE))
            return; // don't care about fqn that doesn't belong to me.

         try
         {
            Object bean = cache.get(fqn, "bean");
            if (bean != null)
            {
               if (log.isTraceEnabled())
               {
                  log.trace("nodePassivate(): send prePassivate event on fqn: " + fqn);
               }
               passivated.add(fqn);
            }

         }
         catch (CacheException ex)
         {
            log.error("nodePassivate(): can't retrieve bean instance from: " + fqn + " with exception: " + ex);
            passivationException = ex;
         }

      }
   }

}
