/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.manager;


import org.jboss.cache.Cache;
import org.jboss.cache.CacheManagerImpl;
import org.jboss.cache.CacheStatus;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.ConfigurationRegistry;
import org.jgroups.JChannelFactory;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.jboss.cache.util.TestingUtil;

/**
 * Tests CacheRegistryImpl.
 *
 * @author Brian Stansberry
 */
@Test(groups = {"functional"}, sequential = true, testName = "manager.CacheManagerTest")
public class CacheManagerTest
{
   /**
    * A file that includes every configuration element I could think of
    */
   public static final String DEFAULT_CONFIGURATION_FILE = "jbc3-registry-configs.xml";

   private Set<Cache<Object, Object>> caches = new HashSet<Cache<Object, Object>>();

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      for (Cache<Object, Object> cache : caches)
      {
         TestingUtil.killCaches(cache);
      }
      caches.clear();
   }

   /**
    * A test that instantiates a CacheRegistryImpl and cycles through all its
    * configs, creating and releasing each.
    * <p/>
    * TODO: 2.2.0: Break this up into more fine-grained tests
    *
    * @throws Exception
    */
   public void testBasic() throws Exception
   {
      JChannelFactory cf = new JChannelFactory();
      cf.setMultiplexerConfig("stacks.xml"); // the default stacks in jgroups.jar
      CacheManagerImpl registry = new CacheManagerImpl(DEFAULT_CONFIGURATION_FILE, cf);
      registry.start();

      ConfigurationRegistry configRegistry = registry.getConfigurationRegistry();

      Set<String> configNames = registry.getConfigurationNames();
      assertEquals(7, configNames.size());
      Set<String> cacheNames = registry.getCacheNames();
      assertEquals(0, cacheNames.size());

      for (String configName : configNames)
      {
         assertNull(configName + " not created", registry.getCache(configName, false));
         Cache<Object, Object> cache = registry.getCache(configName, true);
         caches.add(cache);

         // Cache shouldn't be started
         assertEquals(CacheStatus.INSTANTIATED, cache.getCacheStatus());
         cache.create();
         cache.start();

         // Config should be a clone
         Configuration rawConfig = configRegistry.getConfiguration(configName);
         Configuration realConfig = cache.getConfiguration();
         assertFalse(rawConfig == realConfig);
         assertEquals(rawConfig.getClusterName(), realConfig.getClusterName());
      }

      cacheNames = registry.getCacheNames();
      assertEquals(configNames, cacheNames);

      // Test basic releasing of caches
      for (String configName : configNames)
      {
         registry.releaseCache(configName);
      }

      cacheNames = registry.getCacheNames();
      assertEquals(0, cacheNames.size());

      // We shouldn't have affected configuration set
      Set<String> configNames2 = registry.getConfigurationNames();
      assertEquals(configNames, configNames2);

      // Releasing only checkout of cache should have destroyed it
      for (Iterator<Cache<Object, Object>> it = caches.iterator(); it.hasNext();)
      {
         assertEquals(CacheStatus.DESTROYED, it.next().getCacheStatus());
         it.remove();
      }

      // Get cache w/o asking to create returns null
      String configName = configNames.iterator().next();
      assertNull(configName + " not created", registry.getCache(configName, false));
      // Get cache w/ asking to create returns cache
      Cache<Object, Object> cache = registry.getCache(configName, true);
      assertFalse(null == cache);
      caches.add(cache);

      cache.create();
      cache.start();

      // Test 2 checkouts of the same cache
      Cache<Object, Object> cache2 = registry.getCache(configName, true);
      assertTrue(cache == cache2);

      registry.releaseCache(configName);

      // One release does not cause registry to stop cache
      assertEquals(CacheStatus.STARTED, cache.getCacheStatus());

      registry.stop();

      // Now it's stopped
      assertEquals(CacheStatus.DESTROYED, cache.getCacheStatus());
      caches.remove(cache);

      cacheNames = registry.getCacheNames();
      assertEquals(0, cacheNames.size());
      assertEquals(cacheNames, registry.getConfigurationNames());
   }

   public void testNullConfigResource() throws Exception
   {
      JChannelFactory cf = new JChannelFactory();
      cf.setMultiplexerConfig("stacks.xml"); // the default stacks in jgroups.jar
      String configResource = null;
      CacheManagerImpl registry = new CacheManagerImpl(configResource, cf);
      registry.start();

      assertEquals("No configs", 0, registry.getConfigurationNames().size());
   }
}
