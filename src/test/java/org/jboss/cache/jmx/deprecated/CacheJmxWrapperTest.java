package org.jboss.cache.jmx.deprecated;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheStatus;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.jmx.CacheJmxWrapper;
import org.jboss.cache.jmx.CacheJmxWrapperMBean;
import org.jboss.cache.jmx.JmxRegistrationManager;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.CacheStarted;
import org.jboss.cache.notifications.annotation.CacheStopped;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.CachePrinter;
import org.jgroups.Address;
import org.jgroups.stack.IpAddress;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.management.ObjectName;
import javax.transaction.TransactionManager;
import java.util.List;

/**
 * Tests the JMX wrapper class around the cache.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @author Brian Stansberry
 */
@Test(groups = "functional", testName = "jmx.deprecated.CacheJmxWrapperTest")
public class CacheJmxWrapperTest extends CacheJmxWrapperTestBase
{
   public void testCacheMBeanBinding() throws Exception
   {
      registerWrapper();
      assertTrue("Should be registered", mBeanServer.isRegistered(mBeanName));
   }

   public void testSetCacheObjectName() throws Exception
   {
      ObjectName on = new ObjectName("jboss.cache:test=SetCacheObjectName");
      boolean registered = false;
      try
      {
         CacheJmxWrapper<String, String> wrapper = createWrapper(createConfiguration());
         wrapper.setCacheObjectName(on.getCanonicalName());

         // Register under the standard name
         registerWrapper(wrapper);
         // Should be registered under 'on'
         registered = mBeanServer.isRegistered(on);

         assertTrue("Registered with configured name", registered);
         assertEquals("Configured name retained", on.getCanonicalName(), wrapper.getCacheObjectName());

         wrapper.create();
         wrapper.start();

         interceptorRegistrationTest(on.getCanonicalName(), true);

         wrapper.stop();
         wrapper.destroy();

         interceptorRegistrationTest(false);
      }
      finally
      {
         if (registered)
         {
            mBeanServer.unregisterMBean(on);
         }
      }
   }

   public void testGetCacheObjectName() throws Exception
   {
      ObjectName on = new ObjectName("jboss.cache:test=SetCacheObjectName");
      String str = on.getCanonicalName();
      CacheJmxWrapper<String, String> wrapper = createWrapper(createConfiguration());
      wrapper.setCacheObjectName(str);

      assertEquals("Setter and getter match", str, wrapper.getCacheObjectName());

      // Go back to the default
      wrapper.setCacheObjectName(null);
      assertEquals("Got default ObjectName", JmxRegistrationManager.REPLICATED_CACHE_PREFIX + clusterName, wrapper.getCacheObjectName());

      registerWrapper(wrapper);
      assertEquals("Returns standard name", mBeanName, new ObjectName(wrapper.getCacheObjectName()));
   }

   public void testGetConfiguration1() throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper();
      Configuration cfgFromJmx = wrapper.getConfiguration();
      assertNotNull("Got a configuration", cfgFromJmx);
      assertSame(cache.getConfiguration(), cfgFromJmx);
   }

   public void testGetConfiguration2() throws Exception
   {
      Configuration cfg = createConfiguration();
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper(cfg);
      Configuration cfgFromJmx = wrapper.getConfiguration();
      assertNotNull("Got a configuration", cfgFromJmx);
      assertSame(cfg, cfgFromJmx);
   }

   /**
    * Note that this is a bit of a 'white box' test as it assumes that the
    * returned String equals Configuration.toString(). That could change and
    * break this test; if it does, and there's nothing wrong with the
    * change, just modify the test.
    *
    * @throws Exception
    */
   public void testPrintConfigurationAsString1() throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper();
      String cfgFromJmx = wrapper.printConfigurationAsString();
      assertEquals(cache.getConfiguration().toString(), cfgFromJmx);
   }

   /**
    * Note that this is a bit of a 'white box' test as it assumes that the
    * returned String equals Configuration.toString(). That could change and
    * break this test; if it does, and there's nothing wrong with the
    * change, just modify the test.
    *
    * @throws Exception
    */
   public void testPrintConfigurationAsString2() throws Exception
   {
      Configuration cfg = createConfiguration();
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper(cfg);
      wrapper.create();
      wrapper.start();
      String cfgFromJmx = wrapper.printConfigurationAsString();
      assertEquals(wrapper.getCache().getConfiguration().toString(), cfgFromJmx);
   }

   /**
    * Note that this is a bit of a 'white box' test as it checks
    * the currently coded HTML format and assumes that the HTML content is
    * derived from Configuration.toString(). That could change and break
    * this test; if it does, and there's nothing wrong with the
    * change, just modify the test.
    *
    * @throws Exception
    */
   public void testPrintConfigurationAsHtml1() throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper();
      String cfgFromJmx = wrapper.printConfigurationAsHtmlString();
      assertEquals(CachePrinter.formatHtml(cache.getConfiguration().toString()), cfgFromJmx);
      checkHtml(cfgFromJmx, false);
   }

   /**
    * Note that this is a bit of a 'white box' test as it checks
    * the currently coded HTML format and assumes that the HTML content is
    * derived from Configuration.toString(). That could change and break
    * this test; if it does, and there's nothing wrong with the
    * change, just modify the test.
    *
    * @throws Exception
    */
   public void testPrintConfigurationAsHtml2() throws Exception
   {
      Configuration cfg = createConfiguration();
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper(cfg);
      wrapper.create();
      wrapper.start();
      String cfgFromJmx = wrapper.printConfigurationAsHtmlString();
      assertEquals(CachePrinter.formatHtml(wrapper.getCache().getConfiguration().toString()), cfgFromJmx);
      checkHtml(cfgFromJmx, false);
   }

   @SuppressWarnings("unchecked")
   public void testGetCache() throws Exception
   {
      registerWrapper();
      // have to start the cache before we'll have a root
      cache.start();

      Cache<String, String> cacheJmx = (Cache<String, String>) mBeanServer.getAttribute(mBeanName, "Cache");
      cacheJmx.getRoot().put("key", "value");

      assertEquals("value", cache.getRoot().get("key"));

      Fqn fqn = Fqn.fromString("/testing/jmx");
      cache.put(fqn, "key", "value");

      assertEquals("value", cacheJmx.get(fqn, "key"));
   }

   public void testPrintCacheDetails() throws Exception
   {
      printCacheDetailsTest(false);
   }

   /**
    * Note that this is a bit of a 'white box' test as it checks
    * the currently coded HTML format. That could change and break
    * this test; if it does, and there's nothing wrong with the
    * change, just modify the test.
    *
    * @throws Exception
    */
   public void testPrintCacheDetailsAsHtml() throws Exception
   {
      String html = printCacheDetailsTest(true);
      checkHtml(html, true);
   }

   public void testPrintLockInfo() throws Exception
   {
      printLockInfoTest(false);
   }

   /**
    * Note that this is a bit of a 'white box' test as it checks
    * the currently coded HTML format. That could change and break
    * this test; if it does, and there's nothing wrong with the
    * change, just modify the test.
    *
    * @throws Exception
    */
   public void testPrintLockInfoAsHtml() throws Exception
   {
      printLockInfoTest(true);
   }

   @Test
   public void testGetMembers() throws Exception
   {
      Configuration c = createConfiguration();
      c.setCacheMode(CacheMode.REPL_ASYNC);
      // This cache instance does not go through UnitTestCacheFactory so we need to modify
      // it's config explicitelly.
      new UnitTestCacheFactory<String, String>().mangleConfiguration(c);
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper(c);
      wrapper.start();
      Address addr = wrapper.getLocalAddress();
      assertNotNull("Got an Address", addr);
      List members = wrapper.getMembers();
      assertNotNull("Got members", addr);
      assertEquals("Got correct number of members", 1, members.size());
      assertTrue("Got an IpAddress", wrapper.getLocalAddress() instanceof IpAddress);
      assertTrue("I am a member", members.contains(addr));
   }

   public void testDuplicateInvocation() throws Exception
   {
      CacheJmxWrapperMBean<String, String> cache = registerWrapper();
      cache.create();
      cache.start();
      cache.create();
      cache.start();

      cache.getCache().put(Fqn.fromString("/a/b/c"), null);
      assertTrue(cache.getNumberOfNodes() > 0);
      assertEquals(0, cache.getNumberOfAttributes());

      cache.destroy();
      cache.start();

      assertEquals(0, cache.getNumberOfNodes());
      assertEquals(0, cache.getNumberOfAttributes());

      cache.stop();
      cache.destroy();
      cache.stop();
      cache.destroy();
   }

   public void testFailedStart() throws Exception
   {
      CacheJmxWrapper<String, String> wrapper = new CacheJmxWrapper<String, String>(createCache(createConfiguration()));
      registerWrapper(wrapper);
      assertEquals("Correct state", CacheStatus.INSTANTIATED, wrapper.getCacheStatus());
      wrapper.create();

      DisruptLifecycleListener listener = new DisruptLifecycleListener();
      listener.setDisrupt(true);
      wrapper.getCache().addCacheListener(listener);

      assertEquals("Correct state", CacheStatus.CREATED, wrapper.getCacheStatus());
      try
      {
         wrapper.start();
         fail("Listener did not prevent start");
      }
      catch (CacheException good)
      {
      }

      assertEquals("Correct state", CacheStatus.FAILED, wrapper.getCacheStatus());

      listener.setDisrupt(false);

      wrapper.start();

      assertEquals("Correct state", CacheStatus.STARTED, wrapper.getCacheStatus());

      wrapper.getCache().put(Fqn.fromString("/a/b/c"), null);
      assertTrue(wrapper.getNumberOfNodes() > 0);
      assertEquals(0, wrapper.getNumberOfAttributes());

      listener.setDisrupt(true);
      // need to re-add the listener since the failed start would have nullified the notifier.
      cache.addCacheListener(listener);

      try
      {
         wrapper.stop();
         fail("Listener did not prevent stop");
      }
      catch (CacheException good)
      {
      }

      assertEquals("Correct state", CacheStatus.FAILED, wrapper.getCacheStatus());

      listener.setDisrupt(false);

      wrapper.stop();
      assertEquals("Correct state", CacheStatus.STOPPED, wrapper.getCacheStatus());
      wrapper.destroy();
      assertEquals("Correct state", CacheStatus.DESTROYED, wrapper.getCacheStatus());
   }

   private String printCacheDetailsTest(boolean html) throws Exception
   {
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper();

      // have to start the cache before we'll have a root
      cache.start();
      Fqn fqn = Fqn.fromString("/testing/jmx");
      cache.put(fqn, "foobar", "barfoo");

      assertEquals("barfoo", cache.get(fqn, "foobar"));

      String details = html ? wrapper.printCacheDetailsAsHtml() : wrapper.printCacheDetails();


      assertTrue("Details include testing", details.contains("testing"));
      assertTrue("Details include jmx", details.contains("jmx"));
      assertTrue("Details include foobar", details.contains("foobar"));
      assertTrue("Details include barfoo", details.contains("barfoo"));

      return details;
   }

   private String printLockInfoTest(boolean html) throws Exception
   {
      Configuration config = createConfiguration();
      config.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      Cache<String, String> c = createCache(config);
      CacheJmxWrapperMBean<String, String> wrapper = registerWrapper(c);

//      wrapper.setManageCacheLifecycle(true);
      wrapper.create();
      wrapper.start();

      TransactionManager tm = config.getRuntimeConfig().getTransactionManager();

      tm.begin();
      try
      {
         Fqn fqn = Fqn.fromString("/testing/jmx");
         cache.put(fqn, "foobar", "barfoo");

         String locks = html ? wrapper.printLockInfoAsHtml() : wrapper.printLockInfo();

         assertTrue("Details include testing", locks.contains("testing"));
         assertTrue("Details include jmx", locks.contains("jmx"));

         return locks;
      }
      catch (Exception e)
      {
         tm.setRollbackOnly();
         throw e;
      }
      finally
      {
         tm.commit();
      }

   }

   private void checkHtml(String html, boolean checkBR)
   {
      if (checkBR)
      {
         assertTrue("Has <br", html.contains("<br"));
      }

      assertTrue("No tabs", html.indexOf('\t') == -1);

      assertTrue("No spaces", html.indexOf(' ') == -1);

   }

   @CacheListener
   public class DisruptLifecycleListener
   {
      private boolean disrupt;

      @CacheStarted
      public void cacheStarted(Event e)
      {
         if (disrupt) throw new IllegalStateException("I don't want to start");
      }

      @CacheStopped
      public void cacheStopped(Event e)
      {
         if (disrupt) throw new IllegalStateException("I don't want to stop");
      }

      public void setDisrupt(boolean disrupt)
      {
         this.disrupt = disrupt;
      }
   }
}
