/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.passivation;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests cache behavior in the presence of concurrent passivation.
 *
 * @author Brian Stansberry
 * @version $Revision: 7332 $
 */

@Test(groups = {"functional"}, testName = "passivation.ConcurrentPassivationTest")
public class ConcurrentPassivationTest
{
   private CacheSPI cache;
   private long wakeupIntervalMillis = 0;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      initCaches();
      wakeupIntervalMillis = cache.getConfiguration().getEvictionConfig().getWakeupInterval();
      if (wakeupIntervalMillis < 0)
      {
         fail("testEviction(): eviction thread wake up interval is illegal " + wakeupIntervalMillis);
      }

   }

   private void initCaches()
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI) instance.createCache(new XmlConfigurationParser().parseFile("configs/local-passivation.xml"), false, getClass());
      cache.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache.getConfiguration().getCacheLoaderConfig().getFirstCacheLoaderConfig().setClassName(DummyInMemoryCacheLoader.class.getName());
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testConcurrentPassivation() throws Exception
   {
      Fqn base = Fqn.fromElements("/org/jboss/test/data/concurrent/passivation");

      // Create a bunch of nodes; more than the /org/jboss/test/data
      // region's maxNodes so we know eviction will kick in
      for (int i = 0; i < 35000; i++)
      {
         cache.put(Fqn.fromRelativeElements(base, i / 100), i, "value");
      }

      // Loop for long enough to have 5 runs of the eviction thread
      long loopDone = System.currentTimeMillis() + (5 * wakeupIntervalMillis);

      while (System.currentTimeMillis() < loopDone)
      {
         // If any get returns null, that's a failure
         for (int i = 0; i < 35000; i++)
         {
            Fqn fqn = Fqn.fromRelativeElements(base, i / 100);
            assertNotNull("Get on Fqn " + fqn + " returned null", cache.getNode(fqn));
         }
      }
   }
}
