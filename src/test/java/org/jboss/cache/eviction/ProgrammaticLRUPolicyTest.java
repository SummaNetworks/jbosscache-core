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

package org.jboss.cache.eviction;


import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.RegionManager;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;

/**
 * Unit tests for programmatic configuration of LRU policy
 *
 * @author Ben Wang, Oct, 2006
 * @version $Revision: 7422 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.ProgrammaticLRUPolicyTest")
public class ProgrammaticLRUPolicyTest extends EvictionTestsBase
{
   CacheSPI<Object, Object> cache;
   EvictionController evController;
   long wakeupIntervalMillis = 0;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      initCache();
      wakeupIntervalMillis = cache.getConfiguration().getEvictionConfig().getWakeupInterval();
      evController = new EvictionController(cache);
   }

   private void initCache()
   {
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      UnitTestCacheFactory<Object, Object> instance = new UnitTestCacheFactory<Object, Object>();
      cache = (CacheSPI<Object, Object>) instance.createCache(conf, false, getClass());
      EvictionConfig erc = new EvictionConfig(new EvictionRegionConfig(Fqn.ROOT, new LRUAlgorithmConfig(0, 0, 10)), -1);
      conf.setEvictionConfig(erc);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setIsolationLevel(IsolationLevel.SERIALIZABLE);

      cache.create();
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   private void addStringBasedRegion() throws Exception
   {
      LRUAlgorithmConfig lru = new LRUAlgorithmConfig(150, 0, 1000);
      EvictionRegionConfig regConfig = new EvictionRegionConfig(Fqn.fromString("/dummy"), lru);

      RegionManager regionManager = cache.getRegionManager();
      EvictionConfig topConfig = cache.getConfiguration().getEvictionConfig();
      topConfig.addEvictionRegionConfig(regConfig);
      regionManager.setEvictionConfig(topConfig);
      // Fqn is the region name
      regionManager.getRegion("/programmatic", true).setEvictionRegionConfig(regConfig);
   }

   public void testStringBasedFqnEviction() throws Exception
   {
      addStringBasedRegion();

      String rootStr = "/programmatic/";
      for (int i = 0; i < 10; i++)
      {
         String str = rootStr + i;
         Fqn fqn = Fqn.fromString(str);
         cache.put(fqn, str, str);
      }

      String val = (String) cache.get(rootStr + "3", rootStr + "3");
      assertNotNull("DataNode should be empty ", val);

      evController.startEviction();

      val = (String) cache.get(rootStr + "3", rootStr + "3");
      assertNull("DataNode should be empty ", val);
   }

   private void addObjectBasedRegion() throws Exception
   {
      LRUAlgorithmConfig lru = new LRUAlgorithmConfig(150, 1000);
      EvictionRegionConfig regConfig = new EvictionRegionConfig(Fqn.fromElements(1), lru);

      RegionManager regionManager = cache.getRegionManager();
      EvictionConfig topConfig = cache.getConfiguration().getEvictionConfig();
      topConfig.addEvictionRegionConfig(regConfig);
      regionManager.setEvictionConfig(topConfig);
      regionManager.getRegion(Fqn.fromElements(1), true).setEvictionRegionConfig(regConfig);
   }

   public void testObjectBasedFqnEviction1() throws Exception
   {
      addStringBasedRegion();

      String rootStr = "programmatic";
      for (int i = 0; i < 10; i++)
      {
         String str = rootStr;
         Integer in = i;
         Fqn fqn = Fqn.fromElements(rootStr, in);
         try
         {
            cache.put(fqn, str, str);
         }
         catch (Exception e)
         {
            fail("Failed to insert data" + e);
            e.printStackTrace();
         }
      }

      Integer in = 3;
      Fqn fqn = Fqn.fromElements(rootStr, in);
      try
      {
         String val = (String) cache.get(fqn, in);
         assertNull("DataNode should be empty ", val);
      }
      catch (Exception e)
      {
         e.printStackTrace();
         fail("Failed to get" + e);
      }

      evController.startEviction();

      try
      {
         String val = (String) cache.get(fqn, in);
         assertNull("DataNode should be empty ", val);
      }
      catch (Exception e)
      {
         e.printStackTrace();
         fail("Failed to get" + e);
      }
   }

   public void testObjectBasedFqnEviction2() throws Exception
   {
      addObjectBasedRegion();

      Fqn rootfqn = Fqn.fromElements((Integer) 1);
      for (int i = 0; i < 10; i++)
      {
         Fqn fqn = Fqn.fromRelativeElements(rootfqn, i);
         try
         {
            cache.put(fqn, i, i);
         }
         catch (Exception e)
         {
            fail("Failed to insert data" + e);
            e.printStackTrace();
         }
      }

      try
      {
         Integer in = 3;
         Fqn fqn = Fqn.fromRelativeElements(rootfqn, in);
         Object val = cache.get(fqn, in);
         assertNotNull("DataNode should not be empty ", val);
      }
      catch (Exception e)
      {
         e.printStackTrace();
         fail("Failed to get" + e);
      }

      Integer in = 3;
      Fqn fqn = Fqn.fromRelativeElements(rootfqn, in);
      Thread.sleep(1500);//max age is 1000, so this should expire
      evController.startEviction();

      try
      {
         Object val = cache.get(fqn, in);
         assertNull("DataNode should be empty ", val);
      }
      catch (Exception e)
      {
         e.printStackTrace();
         fail("Failed to get" + e);
      }
   }
}
