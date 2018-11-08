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

package org.jboss.cache.jmx.deprecated;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.jmx.CacheJmxWrapperMBean;
import org.jboss.cache.jmx.CacheJmxWrapper;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

/**
 * Tests the interceptor registration function of CacheJmxWrapper.
 *
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7696 $
 */
@Test(groups = "functional", testName = "jmx.deprecated.InterceptorRegistrationTest")
public class InterceptorRegistrationTest extends CacheJmxWrapperTestBase
{

   /**
    * Confirms interceptor mbeans are registered if the following events
    * occur:
    * <p/>
    * cache.start();
    * wrapper creation and registration.
    *
    * @throws Exception
    */
   public void testInterceptorMBeans1() throws Exception
   {
      // have to start the cache to have any interceptors
      createCache(createConfiguration());
      cache.start();

      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper(cache);
      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      interceptorRegistrationTest(true);

      // These should be ignored because we
      // never did wrapper.create()/start()
      wrapper.stop();
      wrapper.destroy();

      // Should still be registered
      interceptorRegistrationTest(true);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   /**
    * Confirms interceptor mbeans are registered if the following events
    * occur:
    * <p/>
    * cache.start();
    * wrapper creation and and start
    * wrapper registration.
    *
    * @throws Exception
    */
   public void testInterceptorMBeans2() throws Exception
   {
      // have to start the cache to have any interceptors
      createCache(createConfiguration());
      cache.start();

      CacheJmxWrapperMBean<String, String> wrapper = new CacheJmxWrapper<String, String>(cache);
      wrapper.start();
      wrapper = registerWrapper(wrapper);
      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      interceptorRegistrationTest(true);

      wrapper.stop();
      wrapper.destroy();

      // Should still no longer be registered
      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   /**
    * Confirms interceptor mbeans are registered if the following events
    * occur:
    * <p/>
    * Cache not injected
    * wrapper registered;
    * wrapper created and started.
    *
    * @throws Exception
    */
   public void testInterceptorMBeans3() throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper(createConfiguration());
      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      // have to start the cache to have any interceptors
      wrapper.create();
      wrapper.start();

      interceptorRegistrationTest(true);

      wrapper.stop();
      wrapper.destroy();

      // Destroy should unregister if we are managing
      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   /**
    * Confirms interceptor mbeans are registered if the following events
    * occur:
    * <p/>
    * Cache not injected
    * wrapper created and started.
    * wrapper registered
    *
    * @throws Exception
    */
   public void testInterceptorMBeans4() throws Exception
   {
      CacheJmxWrapper<String, String> wrapper = createWrapper(createConfiguration());

      // have to start the cache to have any interceptors
      wrapper.create();
      wrapper.start();

      registerWrapper(wrapper);

      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      interceptorRegistrationTest(true);

      wrapper.stop();
      wrapper.destroy();

      // Destroy should unregister if we are managing
      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   /**
    * Confirms interceptor mbeans are registered if the following events
    * occur:
    * <p/>
    * cache constructed;
    * wrapper constructed and registered with manageCacheLifecycle=true
    * wrapper created and started
    *
    * @throws Exception
    */
   public void testInterceptorMBeans5() throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper();
//      wrapper.setManageCacheLifecycle(true);
      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      // have to start the cache to have any interceptors
      wrapper.create();
      wrapper.start();

      interceptorRegistrationTest(true);

      wrapper.stop();
      wrapper.destroy();

      // Destroy should unregister if we are managing
      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   /**
    * Confirms interceptor mbeans are registered if the following events
    * occur:
    * <p/>
    * cache constructed;
    * wrapper constructed and registered
    * wrapper created and started
    *
    * @throws Exception
    */
   public void testInterceptorMBeans6() throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper();
      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      // have to start the cache to have any interceptors
      wrapper.create();
      wrapper.start();

      interceptorRegistrationTest(true);

      wrapper.stop();
      wrapper.destroy();

      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   /**
    * Confirms interceptor mbeans are registered if the following events
    * occur:
    * <p/>
    * cache constructed;
    * wrapper created and started
    * wrapper registered
    *
    * @throws Exception
    */
   public void testInterceptorMBeans7() throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = new CacheJmxWrapper<String, String>(createCache(createConfiguration()));

      // have to start the cache to have any interceptors
      wrapper.create();
      wrapper.start();

      wrapper = registerWrapper(wrapper);
      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      interceptorRegistrationTest(true);

      wrapper.stop();
      wrapper.destroy();

      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   /**
    * Tests that setting registerInterceptors=false disables interceptor
    * registration when the wrapper is registered before create/start
    * are called.
    *
    * @throws Exception
    */
   public void testRegisterInterceptors1() throws Exception
   {
      CacheJmxWrapper<String, String> wrapper = createWrapper(createConfiguration());
      wrapper.setRegisterJmxResource(false);

      registerWrapper(wrapper);

      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      wrapper.create();
      wrapper.start();

      interceptorRegistrationTest(false);

      wrapper.stop();
      wrapper.destroy();

      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   /**
    * Tests that setting registerInterceptors=false disables interceptor
    * registration when the wrapper is registered after create/start
    * are called.
    *
    * @throws Exception
    */
   public void testRegisterInterceptors2() throws Exception
   {
      CacheJmxWrapper<String, String> wrapper = createWrapper(createConfiguration());
      wrapper.setRegisterJmxResource(false);

      wrapper.create();
      wrapper.start();

      registerWrapper(wrapper);

      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      interceptorRegistrationTest(false);

      wrapper.stop();
      wrapper.destroy();

      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   public void testExposeManagementStatistics1() throws Exception
   {
      Configuration cfg = createConfiguration();
      cfg.setExposeManagementStatistics(false);

      CacheJmxWrapper<String, String> wrapper = createWrapper(cfg);
      registerWrapper(cfg);

      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      wrapper.create();
      wrapper.start();

      interceptorRegistrationTest(false);

      wrapper.stop();
      wrapper.destroy();

      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);
   }

   public void testExposeManagementStatistics2() throws Exception
   {
      Configuration cfg = createConfiguration();
      cfg.setExposeManagementStatistics(false);

      CacheJmxWrapper<String, String> wrapper = createWrapper(cfg);

      wrapper.create();
      wrapper.start();

      registerWrapper(wrapper);

      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));

      interceptorRegistrationTest(false);

      wrapper.stop();
      wrapper.destroy();

      interceptorRegistrationTest(false);

      unregisterWrapper();

      interceptorRegistrationTest(false);

   }

}
