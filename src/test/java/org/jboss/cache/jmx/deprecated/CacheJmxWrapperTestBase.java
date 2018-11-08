package org.jboss.cache.jmx.deprecated;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.jmx.CacheJmxWrapper;
import org.jboss.cache.jmx.CacheJmxWrapperMBean;
import org.jboss.cache.jmx.JmxRegistrationManager;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.*;

/**
 * Tests the JMX wrapper class around the cache.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @author Brian Stansberry
 */
@Test(groups = "functional", testName = "jmx.deprecated.CacheJmxWrapperTestBase")
public abstract class CacheJmxWrapperTestBase
{
   public static final String CLUSTER_NAME_BASE = "CacheMBeanTest";

   protected String clusterName;   
   protected Cache<String, String> cache;
   protected CacheJmxWrapperMBean<String, String> jmxWrapper;
   protected MBeanServer mBeanServer;
   protected ObjectName mBeanName;
   protected String mBeanNameStr;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      clusterName = CLUSTER_NAME_BASE + "-" + Thread.currentThread().getName();
      mBeanServer = MBeanServerFactory.createMBeanServer("CacheMBeanTest");

      mBeanNameStr = JmxRegistrationManager.REPLICATED_CACHE_PREFIX + clusterName;
      mBeanName = new ObjectName(mBeanNameStr);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      try
      {
         cleanup();
      }
      finally
      {
         if (mBeanServer != null)
         {
            MBeanServerFactory.releaseMBeanServer(mBeanServer);
            mBeanServer = null;
         }
      }
   }

   protected CacheJmxWrapperMBean<String, String> registerWrapper() throws Exception
   {
      if (cache == null)
         cache = createCache(createConfiguration());
      return registerWrapper(cache);
   }

   protected CacheJmxWrapperMBean<String, String> registerWrapper(Cache<String, String> toWrap) throws Exception
   {
      CacheJmxWrapper<String, String> wrapper = new CacheJmxWrapper<String, String>(toWrap);
      return registerWrapper(wrapper);
   }

   protected CacheJmxWrapperMBean<String, String> registerWrapper(Configuration config) throws Exception
   {
      CacheJmxWrapper<String, String> wrapper = new CacheJmxWrapper<String, String>();
      wrapper.setConfiguration(config);
      return registerWrapper(wrapper);
   }

   @SuppressWarnings("unchecked")
   protected CacheJmxWrapperMBean<String, String> registerWrapper(CacheJmxWrapperMBean<String, String> wrapper) throws Exception
   {
      ObjectName on = new ObjectName(mBeanNameStr);
      if (!mBeanServer.isRegistered(on))
      {
         mBeanServer.registerMBean(wrapper, on);
      }
      jmxWrapper = (CacheJmxWrapperMBean<String, String>) MBeanServerInvocationHandler.newProxyInstance(mBeanServer, mBeanName, CacheJmxWrapperMBean.class, false);
      return jmxWrapper;
   }

   protected void unregisterWrapper() throws Exception
   {
      mBeanServer.unregisterMBean(mBeanName);
   }

   protected CacheJmxWrapper<String, String> createWrapper(Configuration config)
   {
      CacheJmxWrapper<String, String> wrapper = new CacheJmxWrapper<String, String>();
      config.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      wrapper.setConfiguration(config);
      return wrapper;
   }

   protected Cache<String, String> createCache(Configuration config)
   {
      UnitTestCacheFactory<String, String> factory = new UnitTestCacheFactory<String, String>();
      cache = factory.createCache(config, false, getClass());
      return cache;
   }

   protected Configuration createConfiguration()
   {
      Configuration c = new Configuration();
      c.setClusterName(clusterName);
      c.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c.setExposeManagementStatistics(true);
      c.setCacheMode(Configuration.CacheMode.LOCAL);
      return c;
   }

   private void cleanup() throws Exception
   {
      if (cache != null)
      {
         try
         {
            cache.stop();
         }
         catch (Exception ignored)
         {
         }

         cache = null;
      }
      if (jmxWrapper != null)
      {
         try
         {
            jmxWrapper.stop();
            jmxWrapper.destroy();
         }
         catch (Exception ignored)
         {
         }

         jmxWrapper = null;
      }

      if (mBeanServer != null && mBeanName != null && mBeanServer.isRegistered(mBeanName))
         mBeanServer.unregisterMBean(mBeanName);
   }

   protected void interceptorRegistrationTest(boolean expectMbeans) throws MalformedObjectNameException, NullPointerException
   {
      interceptorRegistrationTest(mBeanNameStr, expectMbeans);
   }

   protected void interceptorRegistrationTest(String baseName, boolean expectMbeans) throws MalformedObjectNameException, NullPointerException
   {
      // should be 3 interceptor MBeans loaded:
      ObjectName[] interceptorMBeanNames = {
            new ObjectName(baseName + JmxRegistrationManager.JMX_RESOURCE_KEY + "TxInterceptor"),
            new ObjectName(baseName + JmxRegistrationManager.JMX_RESOURCE_KEY + "CacheMgmtInterceptor"),
      };

      for (ObjectName n : interceptorMBeanNames)
      {
         if (expectMbeans)
            assertTrue(n + " should be registered", mBeanServer.isRegistered(n));
         else
            assertFalse(n + " should not be registered", mBeanServer.isRegistered(n));
      }
   }
}
