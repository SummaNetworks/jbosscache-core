/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.cache.passivation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeActivated;
import org.jboss.cache.notifications.annotation.NodeLoaded;
import org.jboss.cache.notifications.annotation.NodePassivated;
import org.jboss.cache.notifications.event.NodeEvent;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "passivation.ReplicatedPassivationIntegrationTest")
public class ReplicatedPassivationIntegrationTest
{
   private CacheSPI<String, String> cache1;
   private CacheSPI<String, String> cache2;
   protected final static Log log = LogFactory.getLog(ReplicatedPassivationIntegrationTest.class);
   long wakeupIntervalMillis = 0;
   PassivationListener listener;
   Fqn base = Fqn.fromString("/org/jboss/test/data");

   public ReplicatedPassivationIntegrationTest()
   {
      listener = new ReplicatedPassivationIntegrationTest.PassivationListener();
   }

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache1 = (CacheSPI<String, String>) instance.createCache(getCfg(), false, getClass());
      cache1.getConfiguration().setUseRegionBasedMarshalling(true);
      cache1.start();

      cache2 = (CacheSPI<String, String>) instance.createCache(getCfg(), false, getClass());
      cache2.getConfiguration().setUseRegionBasedMarshalling(true);

      cache2.start();
      cache2.getNotifier().addCacheListener(listener);
      listener.resetCounter();

      wakeupIntervalMillis = cache2.getConfiguration().getEvictionConfig().getWakeupInterval();
      log("wakeupInterval is " + wakeupIntervalMillis);
      if (wakeupIntervalMillis <= 0)
      {
         fail("testEviction(): eviction thread wake up interval is illegal " + wakeupIntervalMillis);
      }
   }

   Configuration getCfg() throws Exception
   {
      Configuration cfg = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC);
      cfg.setEvictionConfig(buildEvictionConfig());
      cfg.setCacheLoaderConfig(buildCacheLoaderConfig());
      cfg.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cfg.getCacheLoaderConfig().getFirstCacheLoaderConfig().setClassName(DummyInMemoryCacheLoader.class.getName());
      return cfg;
   }

   private CacheLoaderConfig buildCacheLoaderConfig() throws Exception
   {
      CacheLoaderConfig clc = new CacheLoaderConfig();
      IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
      iclc.setClassName(DummySharedInMemoryCacheLoader.class.getName());
      clc.addIndividualCacheLoaderConfig(iclc);
      clc.setPassivation(true);
      return clc;
   }

   private EvictionConfig buildEvictionConfig() throws Exception
   {
      EvictionConfig cfg = new EvictionConfig();
      cfg.setWakeupInterval(1000);
      cfg.setDefaultEventQueueSize(200000);

      EvictionRegionConfig region1 = new EvictionRegionConfig();
      region1.setRegionFqn(Fqn.ROOT);
      LRUAlgorithmConfig epc1 = new LRUAlgorithmConfig();
      epc1.setMaxNodes(5000);
      epc1.setTimeToLive(3000);
      region1.setEvictionAlgorithmConfig(epc1);
      cfg.setDefaultEvictionRegionConfig(region1);

      EvictionRegionConfig region2 = new EvictionRegionConfig();
      region2.setRegionFqn(base);
      LRUAlgorithmConfig epc2 = new LRUAlgorithmConfig();
      epc2.setMaxNodes(100);
      epc2.setTimeToLive(3000);
      region2.setEvictionAlgorithmConfig(epc2);
      cfg.addEvictionRegionConfig(region2);

      return cfg;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   public void testActivationEvent() throws Exception
   {
      Fqn internalFqn = Fqn.fromString("/__JBossInternal__/5c4o12-pzhlhj-esnuy3sg-1-esnuy3sg-2");
      Fqn fqn = Fqn.fromRelativeElements(base, "0");
      cache1.removeNode(Fqn.ROOT);

      cache1.put(fqn, fqn.toString(), fqn.toString());
      cache1.put(internalFqn, fqn.toString(), fqn.toString());

      TestingUtil.sleepThread(wakeupIntervalMillis + 100);
      Node n = cache2.peek(fqn, false);
      assert n == null || !n.getKeys().contains(fqn) : "UnversionedNode should not exist";
      String val;
      val = cache2.get(fqn, fqn.toString());
      val = cache2.get(internalFqn, fqn.toString());
      assertNotNull("Node should be activated ", val);
   }

   void log(String msg)
   {
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
         if (ne.isPre())
         {
         }
      }

      @NodeLoaded
      public void nodeLoaded(NodeEvent ne)
      {
         if (!ne.isPre())
         {
            loadedCounter++;
         }
      }

   }
}
