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

package org.jboss.cache.integration;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheManagerImpl;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;

/**
 * CacheManager implementation that integrates with UnitTestCacheFactory.
 * 
 * @author Brian Stansberry
 */
public class UnitTestCacheFactoryCacheManager extends CacheManagerImpl
{
   private final String cacheConfigFileName;
   private final String stacksXMLFileName;
   
   /**
    * Create a new UnitTestCacheFactoryCacheManager.
    * 
    * @param configFileName
    * @param factory
    */
   public UnitTestCacheFactoryCacheManager(String cacheConfigFileName, String stacksXMLFileName)
   {
      super(new UnitTestCacheFactoryConfigurationRegistry(cacheConfigFileName, stacksXMLFileName), MockChannelFactory.INSTANCE);
      this.cacheConfigFileName = cacheConfigFileName;
      this.stacksXMLFileName = stacksXMLFileName;
   }

   public String getCacheConfigFileName()
   {
      return cacheConfigFileName;
   }
   
   public String getStacksXMLFileName()
   {
      return stacksXMLFileName;
   }

   @Override
   public void start() throws Exception
   {
      ((UnitTestCacheFactoryConfigurationRegistry) getConfigurationRegistry()).start();
      super.start();
   }

   /**
    * Overrides superclass to use UnitTestCacheFactory to create the cache.
    */
   @Override
   protected Cache<Object, Object> createCache(Configuration config)
   {
      return new UnitTestCacheFactory<Object, Object>().createCache(config, false, getClass());
   }
   
   

}
