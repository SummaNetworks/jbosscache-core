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

import java.util.ArrayList;
import java.util.List;

import org.jboss.cache.CacheManager;

/**
 * @author Brian Stansberry
 */
public class CacheManagerSupport
{
   /**
    * For each thread holds list of cache managers created using this factory.
    */
   private static final ThreadLocal<List<UnitTestCacheFactoryCacheManager>> threadCacheManagers =
         new ThreadLocal<List<UnitTestCacheFactoryCacheManager>>()
         {
            @Override
            protected List<UnitTestCacheFactoryCacheManager> initialValue()
            {
               return new ArrayList<UnitTestCacheFactoryCacheManager>();
            }
         };
         
  public static List<CacheManager> createCacheManagers(int count, String cacheConfigFileName, String stacksXmlFileName)
  {
     List<UnitTestCacheFactoryCacheManager> existing = threadCacheManagers.get();
     List<CacheManager> result = new ArrayList<CacheManager>(count);
     int added = 0;
     for (UnitTestCacheFactoryCacheManager cm : existing)
     {
        if (cacheConfigFileName.equals(cm.getCacheConfigFileName()) && stacksXmlFileName.equals(cm.getStacksXMLFileName()))
        {
           result.add(cm);
           added++;
        }
     }
     
     for (; added < count; added++)
     {
        UnitTestCacheFactoryCacheManager cm = new UnitTestCacheFactoryCacheManager(cacheConfigFileName, stacksXmlFileName);
        try
        {
            cm.start();
        }
        catch (Exception e)
        {
           throw new RuntimeException(e);
        }
        result.add(cm);
        existing.add(cm);
     }
     
     return result;
  }
  
  public static void tearDown()
  {
     List<UnitTestCacheFactoryCacheManager> existing = threadCacheManagers.get();
     for (UnitTestCacheFactoryCacheManager cm : existing)
     {
        cm.stop();
     }
     threadCacheManagers.remove();
  }
}
