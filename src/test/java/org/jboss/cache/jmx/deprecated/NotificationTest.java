package org.jboss.cache.jmx.deprecated;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.jmx.CacheJmxWrapper;
import org.jboss.cache.jmx.JmxRegistrationManager;
import org.jboss.cache.jmx.CacheNotificationBroadcaster;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.CacheLoader;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import java.util.EnumSet;
import java.util.HashMap;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Functional tests for CacheJmxWrapper broadcast of cache event notifications
 *
 * @author Jerry Gauthier
 * @version $Id: NotificationTest.java 7422 2009-01-09 09:21:18Z mircea.markus $
 */
@Test(groups = {"functional"}, testName = "jmx.deprecated.NotificationTest")
public class NotificationTest
{
   protected static final String CLUSTER_NAME = "NotificationTestCluster";

   protected static final String CAPITAL = "capital";
   protected static final String CURRENCY = "currency";
   protected static final String POPULATION = "population";
   protected static final String EUROPE_NODE = "Europe";

   public enum Type
   {
      STARTED, STOPPED, PRECREATE, POSTCREATE, PREEVICT, POSTEVICT,
      PRELOAD, POSTLOAD, PREREMOVE, POSTREMOVE, PREVISIT, POSTVISIT,
      PREMODIFY, POSTMODIFY, PREACTIVATE, POSTACTIVATE, PREPASSIVATE,
      POSTPASSIVATE, VIEWCHANGE
   }

   protected MBeanServer m_server;
   protected EnumSet<Type> events = EnumSet.noneOf(Type.class);

   protected CacheSPI<Object, Object> cache = null;
   protected boolean optimistic = false;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      m_server = MBeanServerFactory.createMBeanServer();

      Object cacheMBean = createCacheAndJmxWrapper();

      //    bind manually for now.
      ObjectName mgmt = getWrapperObjectName();

      m_server.registerMBean(cacheMBean, mgmt);
   }

   protected Object createCacheAndJmxWrapper() throws Exception
   {
      cache = createCache(CLUSTER_NAME);
      return new CacheJmxWrapper<Object, Object>(cache);
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
         // make sure we stop the mbean server
         if (m_server != null)
            MBeanServerFactory.releaseMBeanServer(m_server);
      }
   }

   protected void cleanup() throws Exception
   {
      events.clear();

      destroyCache();

      if (m_server != null)
      {
         ObjectName mgmt = getWrapperObjectName();
         if (m_server.isRegistered(mgmt))
            m_server.unregisterMBean(mgmt);
      }
   }

   protected void destroyCache()
   {
      if (cache != null)
      {
         // stop the cache before the listener is unregistered
         //cache1.stop();
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   protected ObjectName getWrapperObjectName() throws Exception
   {
      return new ObjectName(JmxRegistrationManager.REPLICATED_CACHE_PREFIX + CLUSTER_NAME);
   }

   public void testNotifications() throws Exception
   {
      assertNotNull("MBeanServer is null.", m_server);
      assertNotNull("Cache is null.", cache);

      ObjectName mgmt = getWrapperObjectName();
      MyListener listener = new MyListener(mgmt);


      m_server.addNotificationListener(mgmt, listener, null, null);

      // start the cache after registering listener - this will trigger CacheStarted
      // since cache is defined with cluster, thiswill also trigger ViewChange
      cache.start();

      // add a node - this will trigger NodeCreated, NodeModify(pre/post) and NodeModified
      HashMap<Object, Object> albania = new HashMap<Object, Object>(4);
      albania.put(CAPITAL, "Tirana");
      albania.put(CURRENCY, "Lek");
      cache.put("Europe/Albania", albania);

      // modify a node - this will trigger NodeModified and NodeModify(pre/post)
      cache.put("Europe/Albania", POPULATION, 3563112);

      // retrieve an attribute - this will trigger NodeVisited
      Fqn key = Fqn.fromString("Europe/Albania");
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + key, cache.get(key, CURRENCY));

      // evict the node - this will trigger NodePassivate, NodeEvicted and NodeEvict(pre/post)
      cache.evict(key);

      // retrieve the attribute again - this will trigger NodeVisited and NodeActivate
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + key, cache.get(key, CURRENCY));

      // remove the node - this will trigger NodeRemoved and NodeRemove(pre/post)
      cache.removeNode(key);

      // clean up before stopping  the cache
      CacheLoader cl = cache.getCacheLoaderManager().getCacheLoader();
      cl.remove(Fqn.fromString(EUROPE_NODE));

      // stop the cache
      cache.stop();
      m_server.removeNotificationListener(mgmt, listener);

      // run the tests
      assertTrue("Expected CacheStarted notification", events.contains(Type.STARTED));
      assertTrue("Expected CacheStopped notification", events.contains(Type.STOPPED));
      assertTrue("Expected NodeCreated notification", events.contains(Type.PRECREATE));
      assertTrue("Expected NodeCreated notification", events.contains(Type.POSTCREATE));
      assertTrue("Expected NodeEvicted notification", events.contains(Type.PREEVICT));
      assertTrue("Expected NodeEvicted notification", events.contains(Type.POSTEVICT));
      assertTrue("Expected NodeLoaded notification", events.contains(Type.PRELOAD));
      assertTrue("Expected NodeLoaded notification", events.contains(Type.POSTLOAD));
      assertTrue("Expected NodeVisited notification", events.contains(Type.PREVISIT));
      assertTrue("Expected NodeVisited notification", events.contains(Type.POSTVISIT));
      assertTrue("Expected NodeActivated notification", events.contains(Type.PREACTIVATE));
      assertTrue("Expected NodeActivated notification", events.contains(Type.POSTACTIVATE));
      assertTrue("Expected NodeModified notification", events.contains(Type.PREMODIFY));
      assertTrue("Expected NodeModified notification", events.contains(Type.POSTMODIFY));
      assertTrue("Expected NodePassivated notification", events.contains(Type.PREPASSIVATE));
      assertTrue("Expected NodePassivated notification", events.contains(Type.POSTPASSIVATE));
      assertTrue("Expected NodeRemoved notification", events.contains(Type.PREREMOVE));
      assertTrue("Expected NodeRemoved notification", events.contains(Type.POSTREMOVE));
      validateHealthyListener(listener);
   }

   public void testEarlyRegistration() throws Exception
   {
      // undo setup
      cleanup();

      CacheJmxWrapper<Object, Object> wrapper = new CacheJmxWrapper<Object, Object>();
      ObjectName mgmt = getWrapperObjectName();
      m_server.registerMBean(wrapper, mgmt);
      MyListener listener = new MyListener(mgmt);
      m_server.addNotificationListener(mgmt, listener, null, null);

      cache = createCache(CLUSTER_NAME);
      wrapper.setCache(cache);
      cache.start();
      try
      {
         assertTrue("Expected CacheStarted notification", events.contains(Type.STARTED));
         validateHealthyListener(listener);
      }
      finally
      {
         cache.stop();
      }
   }

   public void testLateRegistration() throws Exception
   {
      assertNotNull("MBeanServer is null.", m_server);
      assertNotNull("Cache is null.", cache);

      // start the cache before registering listener
      cache.start();

      try
      {
         ObjectName mgmt = getWrapperObjectName();
         MyListener listener = new MyListener(mgmt);

         m_server.addNotificationListener(mgmt, listener, null, null);

         // add a node - this will trigger NodeCreated, NodeModify(pre/post) and NodeModified
         HashMap<Object, Object> albania = new HashMap<Object, Object>(4);
         albania.put(CAPITAL, "Tirana");
         albania.put(CURRENCY, "Lek");
         cache.put("Europe/Albania", albania);

         // run the tests
         assertTrue("Expected NodeModified notification", events.contains(Type.PREMODIFY));
         assertTrue("Expected NodeModified notification", events.contains(Type.POSTMODIFY));
         validateHealthyListener(listener);
      }
      finally
      {
         cache.stop();
      }
   }

   public void testListenerRemoval() throws Exception
   {
      assertNotNull("MBeanServer is null.", m_server);
      assertNotNull("Cache is null.", cache);

      ObjectName mgmt = getWrapperObjectName();
      MyListener listener = new MyListener(mgmt);

      m_server.addNotificationListener(mgmt, listener, null, null);

      // start the cache after registering listener - this will trigger CacheStarted
      // since cache is defined with cluster, thiswill also trigger ViewChange
      cache.start();
      boolean ok = false;
      try
      {
         assertTrue("Expected CacheStarted notification", events.contains(Type.STARTED));

         m_server.removeNotificationListener(mgmt, listener);
         ok = true;
      }
      finally
      {
         cache.stop();
         if (ok)
         {
            assertFalse("Expected no CacheStopped notification", events.contains(Type.STOPPED));
            validateHealthyListener(listener);
         }
      }
   }

   private CacheSPI<Object, Object> createCache(String clusterName) throws Exception
   {
      Configuration config = createConfiguration(clusterName);
      config.setCacheMode(CacheMode.LOCAL);
      UnitTestCacheFactory<Object, Object> factory = new UnitTestCacheFactory<Object, Object>();
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) factory.createCache(config, false, getClass());

      cache.create();
      // start the cache after the listener has been registered
      //cache.start();
      return cache;
   }

   protected Configuration createConfiguration(String clusterName) throws Exception
   {
      Configuration config = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);
      config.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      config.setCacheLoaderConfig(getCacheLoaderConfig("location=" + getTempDir()));
      config.setExposeManagementStatistics(true);
      config.setClusterName(clusterName);
      if (optimistic)
      {
         config.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
         config.setNodeLockingScheme("OPTIMISTIC");
      }

      return config;
   }

   private static String getTempDir()
   {
      return System.getProperty("java.io.tempdir", "/tmp");
   }

   private static boolean getPre(Object data)
   {
      assertNotNull("User data is null, should be Object[]", data);
      assertTrue("User data is " + data.getClass().getName() + ", should be Object[]", data instanceof Object[]);

      Object[] parms = (Object[]) data;
      assertTrue("Parameter is " + parms[1].getClass().getName() + ", should be Boolean", parms[1] instanceof Boolean);
      return (Boolean) parms[1];
   }

   protected static CacheLoaderConfig getCacheLoaderConfig(String properties) throws Exception
   {
      return UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(true, "",
            "org.jboss.cache.loader.FileCacheLoader", properties, false, false, true, false, false);
   }

   private static void validateHealthyListener(MyListener listener)
   {
      if (listener.failure != null)
         throw listener.failure;
      if (listener.exception != null)
         throw listener.exception;
   }

   private class MyListener implements NotificationListener
   {
      private RuntimeException exception;
      private AssertionError failure;
      private final String emitterObjectName;

      MyListener(ObjectName emitter)
      {
         this.emitterObjectName = emitter.getCanonicalName();
      }

      public void handleNotification(Notification notification, Object handback)
      {
         try
         {
            String type = notification.getType();
            Object userData = notification.getUserData();

            if (type.equals(CacheNotificationBroadcaster.NOTIF_CACHE_STARTED))
            {
               events.add(Type.STARTED);
               assertEquals("Correct object name in start notification", emitterObjectName, userData);
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_CACHE_STOPPED))
            {
               events.add(Type.STOPPED);
               assertEquals("Correct object name in stop notification", emitterObjectName, userData);
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_NODE_CREATED))
            {
               if (getPre(userData))
               {
                  events.add(Type.PRECREATE);
               }
               else
               {
                  events.add(Type.POSTCREATE);
               }
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_NODE_EVICTED))
            {
               if (getPre(userData))
               {
                  events.add(Type.PREEVICT);
               }
               else
               {
                  events.add(Type.POSTEVICT);
               }
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_NODE_LOADED))
            {
               if (getPre(userData))
               {
                  events.add(Type.PRELOAD);
               }
               else
               {
                  events.add(Type.POSTLOAD);
               }
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_NODE_REMOVED))
            {
               if (getPre(userData))
               {
                  events.add(Type.PREREMOVE);
               }
               else
               {
                  events.add(Type.POSTREMOVE);
               }
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_NODE_VISITED))
            {
               if (getPre(userData))
               {
                  events.add(Type.PREVISIT);
               }
               else
               {
                  events.add(Type.POSTVISIT);
               }
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_VIEW_CHANGED))
            {
               events.add(Type.VIEWCHANGE);
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_NODE_ACTIVATED))
            {
               if (getPre(userData))
               {
                  events.add(Type.PREACTIVATE);
               }
               else
               {
                  events.add(Type.POSTACTIVATE);
               }
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_NODE_MODIFIED))
            {
               if (getPre(userData))
               {
                  events.add(Type.PREMODIFY);
               }
               else
               {
                  events.add(Type.POSTMODIFY);
               }
            }
            else if (type.equals(CacheNotificationBroadcaster.NOTIF_NODE_PASSIVATED))
            {
               if (getPre(userData))
               {
                  events.add(Type.PREPASSIVATE);
               }
               else
               {
                  events.add(Type.POSTPASSIVATE);
               }
            }
         }
         catch (RuntimeException e)
         {
            // Store so the test can rethrow
            exception = e;
         }
         catch (AssertionError e)
         {
            // Store so the test can rethrow
            failure = e;
         }
      }
   }

}
