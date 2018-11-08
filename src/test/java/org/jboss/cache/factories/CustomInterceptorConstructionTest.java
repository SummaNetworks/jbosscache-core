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
package org.jboss.cache.factories;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.CustomInterceptorConfig;
import org.jboss.cache.config.parsing.custominterceptors.AaaCustomInterceptor;
import org.jboss.cache.interceptors.MVCCLockingInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.List;

/**
 * Tests how custom interceptor construction is handled.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "functional", sequential = true, testName = "factories.CustomInterceptorConstructionTest")
public class CustomInterceptorConstructionTest
{
   CacheSPI cache;
   int defaultInterceptroCount;

   @AfterMethod
   public void tearDown()
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
      }
      cache = null;
   }

   public void testAddFirst()
   {
      AaaCustomInterceptor interceptor = new AaaCustomInterceptor();
      CustomInterceptorConfig config = new CustomInterceptorConfig(interceptor);
      config.setFirst(true);
      buildCache(config);
      assert cache.getInterceptorChain().get(0).equals(interceptor);
      assert cache.getInterceptorChain().size() == defaultInterceptroCount + 1;
   }

   public void testAddLast()
   {
      AaaCustomInterceptor interceptor = new AaaCustomInterceptor();
      CustomInterceptorConfig config = new CustomInterceptorConfig(interceptor);
      config.setLast(true);
      buildCache(config);
      List chain = cache.getInterceptorChain();
      assert chain.get(chain.size() - 1).equals(interceptor);
      assert cache.getInterceptorChain().size() == defaultInterceptroCount + 1;
   }

   public void testAddAfter()
   {
      AaaCustomInterceptor interceptor = new AaaCustomInterceptor();
      CustomInterceptorConfig config = new CustomInterceptorConfig(interceptor);
      config.setAfterClass(MVCCLockingInterceptor.class.getName());
      buildCache(config);
      List<CommandInterceptor> chain = cache.getInterceptorChain();
      int occurenceCount = 0;
      for (CommandInterceptor ci : chain)
      {
         if (ci instanceof MVCCLockingInterceptor)
         {
            assert ci.getNext().equals(interceptor);
            occurenceCount++;
         }
      }
      assert occurenceCount == 1;
      assert cache.getInterceptorChain().size() == defaultInterceptroCount + 1;
   }

   public void testAddBefore()
   {
      AaaCustomInterceptor interceptor = new AaaCustomInterceptor();
      CustomInterceptorConfig config = new CustomInterceptorConfig(interceptor);
      config.setBeforeClass(MVCCLockingInterceptor.class.getName());
      buildCache(config);
      List<CommandInterceptor> chain = cache.getInterceptorChain();
      int occurenceCount = 0;
      for (CommandInterceptor ci : chain)
      {
         if (ci instanceof AaaCustomInterceptor)
         {
            assert ci.getNext() instanceof MVCCLockingInterceptor;
            occurenceCount++;
         }
      }
      assert occurenceCount == 1;
      assert cache.getInterceptorChain().size() == defaultInterceptroCount + 1;
   }

   @Test(enabled = true)
   public void testAddAtIndex()
   {
      AaaCustomInterceptor interceptor = new AaaCustomInterceptor();
      CustomInterceptorConfig config = new CustomInterceptorConfig(interceptor);
      config.setIndex(1);
      buildCache(config);
      List<CommandInterceptor> chain = cache.getInterceptorChain();
      assert chain.get(1).equals(interceptor);
      assert cache.getInterceptorChain().size() == defaultInterceptroCount + 1;
   }

   public void testAddAtInvalidIndex()
   {
      AaaCustomInterceptor interceptor = new AaaCustomInterceptor();
      CustomInterceptorConfig config = new CustomInterceptorConfig(interceptor);
      config.setIndex(1000);
      try
      {
         buildCache(config);
         assert false : "exception expected here";
      }
      catch (Exception e)
      {
         //expected
      }
   }

   private void buildCache(CustomInterceptorConfig interceptorConfig)
   {
      buildCache(Collections.singletonList(interceptorConfig));
   }

   private void buildCache(List<CustomInterceptorConfig> interceptorConfig)
   {
      Configuration config = new Configuration();
      config.setCacheMode(Configuration.CacheMode.LOCAL);
      config.setNodeLockingScheme(Configuration.NodeLockingScheme.MVCC);
      UnitTestCacheFactory cacheFactory2 = new UnitTestCacheFactory<Object, Object>();
      CacheSPI tmpCacheSPI = (CacheSPI) cacheFactory2.createCache(config, getClass());
      defaultInterceptroCount = tmpCacheSPI.getInterceptorChain().size();
      tmpCacheSPI.stop();

      UnitTestCacheFactory cacheFactory = new UnitTestCacheFactory<Object, Object>();
      config.setCustomInterceptors(interceptorConfig);
      cache = (CacheSPI) cacheFactory.createCache(config, true, getClass());
   }
}

