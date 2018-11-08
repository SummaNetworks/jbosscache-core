/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.transaction.GenericTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.UnitTestDatabaseManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Properties;

/**
 * UT for testing JDBCCacheLoader during state transfer.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "functional", testName = "loader.JDBCCacheLoaderStateTransferTest")
public class JDBCCacheLoaderStateTransferTest
{

   CacheSPI first;
   CacheSPI second;

   private Properties props1;
   private Properties props2;

   @BeforeTest
   public void createDatabase()
   {
      props1 = UnitTestDatabaseManager.getTestDbProperties();
      props2 = UnitTestDatabaseManager.getTestDbProperties();
   }

   @AfterTest
   public void shutDownDatabase()
   {
      UnitTestDatabaseManager.shutdownInMemoryDatabase(props1);
      UnitTestDatabaseManager.shutdownInMemoryDatabase(props2);
   }


   @AfterMethod
   public void tearDown()
   {
      if (first != null) TestingUtil.killCaches(first);
      if (second != null) TestingUtil.killCaches(second);
   }

   private Configuration getConfiguration(Properties props) throws Exception
   {
      Configuration c = new Configuration();
      c.setTransactionManagerLookupClass(GenericTransactionManagerLookup.class.getName());
      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "/", JDBCCacheLoader.class.getName(), props, false, true, false, false, false);
      clc.setPassivation(false);
      clc.getFirstCacheLoaderConfig().setPurgeOnStartup(true);
      c.setCacheLoaderConfig(clc);
      c.setCacheMode(CacheMode.REPL_SYNC);
      c.setStateRetrievalTimeout(1000 * 120);//allow 2 minutes for before state transfer timeouts
      return c;
   }

   public void testSimpleStateTransfer() throws Exception
   {
      first = (CacheSPI) new UnitTestCacheFactory().createCache(getConfiguration(props1), getClass());
      first.put("/a/b/c", "key", "value");
      first.put("/a/b/d", "key", "value");
      first.put("/a/b/e", "key", "value");
      second = (CacheSPI) new UnitTestCacheFactory().createCache(getConfiguration(props2), getClass());
      assert second.get("/a/b/c", "key").equals("value");
      assert second.get("/a/b/d", "key").equals("value");
      assert second.get("/a/b/e", "key").equals("value");
      JDBCCacheLoader cacheLoader = (JDBCCacheLoader) second.getCacheLoaderManager().getCacheLoader();
      assert cacheLoader.exists(Fqn.fromString("/a"));
      assert cacheLoader.exists(Fqn.fromString("/a/b"));
   }


   //todo mmarkus this test takes forever on fedora 9/4 cpu. Fix it!
   @Test (enabled = false)
   public void testMoreState() throws Exception
   {
      long startTime = System.currentTimeMillis();

      first = (CacheSPI) new UnitTestCacheFactory().createCache(getConfiguration(props1), getClass());
      long cacheStartTime = System.currentTimeMillis() - startTime;
      for (int i = 0; i < 5012; i++)
      {
         first.put("a/b/" + i, "k", "v");
      }
      startTime = System.currentTimeMillis();
      second = (CacheSPI) new UnitTestCacheFactory().createCache(getConfiguration(props2), getClass());

      long stateTranferTime = System.currentTimeMillis() - startTime - cacheStartTime;
      System.out.println("stateTranferTime = " + stateTranferTime);
      for (int i = 0; i < 5012; i += 100)
      {
         assert second.get("a/b/" + i, "k").equals("v");
      }
   }
}
