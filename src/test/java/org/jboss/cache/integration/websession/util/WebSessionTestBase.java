/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.cache.integration.websession.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheManager;
import org.jboss.cache.CacheStatus;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.integration.CacheManagerSupport;
import org.jboss.cache.integration.UnitTestCacheFactoryConfigurationRegistry;
import org.jboss.cache.integration.websession.util.WebAppMetadata.Granularity;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

/**
 * Base class that handles standard before/after class/method stuff.
 * 
 * @author Brian Stansberry
 */
public abstract class WebSessionTestBase
{
   public static final String DEFAULT_CACHE_CONFIG_FILE_NAME = "configs/integration/web-session-cache-configs.xml";

   private AtomicInteger testCount = new AtomicInteger();
   private List<CacheManager> cacheManagers;
   private List<SessionManager> sessionManagers;
   protected ReplicationListener[] replListeners;
   
   @BeforeClass(alwaysRun = true)
   public void beforeClass() throws Exception
   {
      cacheManagers = CacheManagerSupport.createCacheManagers(getNumCacheManagers(), getCacheConfigFileName(), getStacksXmlFileName());
      if (getStartCachesInBeforeClass() && getCacheConfigName() != null)
      {
         String inUseProtocolStack = UnitTestConfigurationFactory.getEmptyConfiguration().getClusterConfig();
         replListeners = new ReplicationListener[getNumCacheManagers()];
         for (int i =0; i < cacheManagers.size(); i++)
         {
            CacheManager cm = cacheManagers.get(i);
            Cache<Object, Object> cache = cm.getCache(getCacheConfigName(), true);
            amendCacheBeforeStartup(cache);
            if (cache.getCacheStatus() != CacheStatus.STARTED)
            {
               cache.getConfiguration().setClusterConfig(inUseProtocolStack);
               cache.start();
            }
            replListeners[i] = ReplicationListener.getReplicationListener(cache);
         }
      }
   }

   protected void amendCacheBeforeStartup(Cache<Object, Object> cache)
   {
      //do nothing
   }

   @AfterClass(alwaysRun = true)
   public void afterClass()
   {

      CacheManagerSupport.tearDown();
   }
   
   @BeforeMethod(alwaysRun = true)
   public void setup()
   {
      testCount.incrementAndGet();
      
      if (getCreateManagersInSetup() && getWebAppMetaData() != null)
      {
         sessionManagers = SessionManagerSupport.createSessionManagers(cacheManagers, getWebAppMetaData());
         for (SessionManager mgr : sessionManagers)
         {
            mgr.start();
         }
      }      
   }
   
   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      SessionManagerSupport.tearDown();
   }
   
   protected abstract int getNumCacheManagers();
   
   protected String getCacheConfigFileName()
   {
      return DEFAULT_CACHE_CONFIG_FILE_NAME;
   }
   
   protected String getStacksXmlFileName()
   {
      return UnitTestCacheFactoryConfigurationRegistry.DEFAULT_STACKS_XML_RESOURCE;
   }
   
   protected boolean getStartCachesInBeforeClass()
   {
      return true;      
   }
   
   protected abstract String getCacheConfigName();
   
   protected boolean getCreateManagersInSetup()
   {
      return false;
   }
   
   protected boolean getUsePassivation()
   {
      return true;
   }
   
   protected Granularity getGranularity()
   {
      return Granularity.SESSION;
   }
   
   protected WebAppMetadata getWebAppMetaData()
   {
      return new WebAppMetadata(getWarNameForTest(), getCacheConfigName(), getUsePassivation(), getGranularity());
   }
   
   protected List<CacheManager> getCacheManagers()
   {
      return cacheManagers;
   }
   
   protected List<SessionManager> getSessionManagers()
   {
       return sessionManagers;
   }
   
   protected int getTestCount()
   {
      return testCount.get();
   }
   
   protected String getWarNameForTest()
   {
      return getClass().getSimpleName() + getTestCount();
   }
   
   protected Object getAttributeValue(int value)
   {
      return Integer.valueOf(value);
   }
}
