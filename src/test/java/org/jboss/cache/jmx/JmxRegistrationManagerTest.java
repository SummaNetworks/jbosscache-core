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
package org.jboss.cache.jmx;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheFactory;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.interceptors.CacheMgmtInterceptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

/**
 * Tester class for {@link JmxRegistrationManager}.
 *
 * @author Mircea.Markus@jboss.com
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 * @since 3.0
 */
@Test (groups = "functional", testName = "jmx.JmxRegistrationManagerTest")
public class JmxRegistrationManagerTest
{
   private static final Log log = LogFactory.getLog(JmxRegistrationManagerTest.class);
   private UnitTestCacheFactory cacheFactory = new UnitTestCacheFactory();
   private MBeanServer mBeanServer;

   @BeforeMethod
   public void setUp()
   {
      mBeanServer = MBeanServerFactory.createMBeanServer();
   }

   @AfterMethod
   public void tearDown()
   {
      MBeanServerFactory.releaseMBeanServer(mBeanServer);
   }

   public void testRegisterLocalCache() throws Exception
   {
      registerLocalCache();
   }
   
   public void testRegisterLocalCacheWithDifferentThreadNames() throws Exception
   {
      String prevName = Thread.currentThread().getName(); 
      try
      {
         Thread.currentThread().setName("onecolon:");
         registerLocalCache();

         Thread.currentThread().setName("twocolons::");
         registerLocalCache();         
      }
      finally
      {
         Thread.currentThread().setName(prevName);
      }     
   }
   
   public void testRegisterReplicatedCache() throws Exception
   {
      Configuration localConfig = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC);
      localConfig.setExposeManagementStatistics(true);
      Cache cache = cacheFactory.createCache(localConfig, getClass());
      JmxRegistrationManager regManager = new JmxRegistrationManager(mBeanServer, cache, (ObjectName)null);
      assert regManager.getObjectNameBase().indexOf(JmxRegistrationManager.REPLICATED_CACHE_PREFIX) == 0;
      regManager.registerAllMBeans();
      String name = regManager.getObjectName(CacheMgmtInterceptor.class.getSimpleName());
      assert mBeanServer.isRegistered(new ObjectName(name));
      regManager.unregisterAllMBeans();
      assert !mBeanServer.isRegistered(new ObjectName(name));
      cache.stop();
   }
   
   protected void registerLocalCache() throws Exception
   {
      Configuration localConfig = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL);
      localConfig.setExposeManagementStatistics(true);
      Cache cache = cacheFactory.createCache(localConfig, getClass());
      JmxRegistrationManager regManager = new JmxRegistrationManager(mBeanServer, cache, (ObjectName)null);
      assert regManager.getObjectNameBase().indexOf(JmxRegistrationManager.LOCAL_CACHE_PREFIX) == 0;
      regManager.registerAllMBeans();
      String name = regManager.getObjectName(CacheMgmtInterceptor.class.getSimpleName());
      assert mBeanServer.isRegistered(new ObjectName(name));
      regManager.unregisterAllMBeans();
      assert !mBeanServer.isRegistered(new ObjectName(name));
      cache.stop();
   }

   /**
    * This is useful when wanting to startup jconsole... 
    */
   @Test(enabled = false)
   public static void main(String[] args)
   {
      Configuration localConfig = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC);
      localConfig.setExposeManagementStatistics(true);
      CacheFactory cacheFactory = new DefaultCacheFactory();
      Cache cache = cacheFactory.createCache(localConfig);
      JmxRegistrationManager regManager = new JmxRegistrationManager(cache);
      while (true){}
   }
}
