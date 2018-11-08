/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.config.parsing.element.BuddyElementParser;
import org.jboss.cache.interceptors.LegacyDataGravitatorInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

/**
 * Tests basic configuration options by passing stuff into the CacheImpl.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "jgroups"}, sequential = true, testName = "buddyreplication.BuddyReplicationConfigTest")
public class BuddyReplicationConfigTest
{
   private CacheSPI<Object, Object> cache;

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testNullConfig() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setBuddyReplicationConfig(null);
      assertNull(cache.getBuddyManager());
   }

   public void testDisabledConfig() throws Exception
   {
      String xmlConfig = "<buddy enabled=\"false\"/>";
      BuddyReplicationConfig config = getBuddyReplicationConfig(xmlConfig);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setBuddyReplicationConfig(config);
      assertNull(cache.getBuddyManager());
   }

   public void testBasicConfig() throws Exception
   {
      String xmlConfig = "<buddy enabled=\"true\"/>";
      BuddyReplicationConfig config = getBuddyReplicationConfig(xmlConfig);
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setBuddyReplicationConfig(config);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      cache.create();
      cache.start();
      assertNotNull(cache.getBuddyManager());
      BuddyManager mgr = cache.getBuddyManager();
      assertTrue(mgr.isEnabled());
      assertNull(mgr.getBuddyPoolName());
      assertEquals(NextMemberBuddyLocator.class, mgr.buddyLocator.getClass());
      NextMemberBuddyLocatorConfig blc = (NextMemberBuddyLocatorConfig) mgr.buddyLocator.getConfig();
      assertEquals(1, blc.getNumBuddies());
      assertTrue(blc.isIgnoreColocatedBuddies());
   }

   private BuddyReplicationConfig getBuddyReplicationConfig(String xmlConfig)
         throws Exception
   {
      Element element = XmlConfigHelper.stringToElementInCoreNS(xmlConfig);
      BuddyElementParser elementParser = new BuddyElementParser();
      BuddyReplicationConfig config = elementParser.parseBuddyElement(element);
      return config;
   }

   public void testXmlConfig() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(new XmlConfigurationParser().parseFile("configs/buddy-replication-cache.xml"), false, getClass());
      cache.create();
      cache.start();
      BuddyManager bm = cache.getBuddyManager();
      assertNotNull(bm);
      assertTrue(bm.isEnabled());
      assertTrue(bm.buddyLocator instanceof NextMemberBuddyLocator);
      NextMemberBuddyLocator bl = (NextMemberBuddyLocator) bm.buddyLocator;
      NextMemberBuddyLocatorConfig blc = (NextMemberBuddyLocatorConfig) bl.getConfig();
      assertTrue(blc.isIgnoreColocatedBuddies());
      assertEquals(1, blc.getNumBuddies());
      assertEquals("myBuddyPoolReplicationGroup", bm.getConfig().getBuddyPoolName());
      assertEquals(2000, bm.getConfig().getBuddyCommunicationTimeout());

      // test Data Gravitator
      boolean hasDG = false;
      for (CommandInterceptor interceptor : cache.getInterceptorChain())
      {
         hasDG = hasDG || (interceptor instanceof LegacyDataGravitatorInterceptor);
      }

      assertTrue("Should have a data gravitator!!", hasDG);
   }

   public void testLocalModeConfig() throws Exception
   {
      String xmlConfig = "<buddy enabled=\"true\"/>";
      BuddyReplicationConfig config = getBuddyReplicationConfig(xmlConfig);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setBuddyReplicationConfig(config);
      cache.create();
      cache.start();
      assert cache.getBuddyManager() == null;

      cache.getInvocationContext().getOptionOverrides().setForceDataGravitation(true);
      cache.getNode(Fqn.fromString("/nonexistent")); // should not barf!
   }
}