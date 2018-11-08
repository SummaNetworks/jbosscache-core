package org.jboss.cache.eviction;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.interceptors.EvictionInterceptor;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Tests the eviction and the possible lack of locking nodes.
 * The configuration is with an aggressive eviction policy, 100 objects 2 seconds interval.
 * <p/>
 * It is possible that the number needs to be changed a little, depending on the machine speed.
 *
 * @author fhenning
 */
@Test(groups = {"functional"}, testName = "eviction.OptimisticEvictionTest")
public class OptimisticEvictionTest extends EvictionTestsBase
{
   private CacheSPI<Object, Object> cache;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration config = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      config.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      config.setEvictionConfig(buildEvictionConfig());
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(config, getClass());
   }

   private EvictionConfig buildEvictionConfig() throws Exception
   {
      EvictionConfig result = new EvictionConfig(new EvictionRegionConfig(Fqn.ROOT, new LRUAlgorithmConfig(0, 0, 10)), 200);
      result.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/testingRegion"), new LRUAlgorithmConfig(0, 0, 10)));
      result.addEvictionRegionConfig(new EvictionRegionConfig(Fqn.fromString("/timeBased"), new LRUAlgorithmConfig(1000, 1000, 0)));
      return result;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testEvictionOccurence() throws Exception
   {
      cache.put("/timeBased/test", "key", "value");
      assertTrue(cache.exists("/timeBased/test"));
      TestingUtil.sleepThread(2000);
      new EvictionController(cache).startEviction();
      assertTrue(!cache.exists("/timeBased/test"));
   }

   public void testInterceptorChain() throws Exception
   {
      List interceptors = cache.getInterceptorChain();
      Iterator i = interceptors.iterator();
      boolean found = false;

      while (i.hasNext())
      {
         Object o = i.next();
         if (o instanceof EvictionInterceptor)
         {
            found = true;
         }
      }

      assertTrue("Eviction interceptor should be in interceptor chain.", found);
   }

   public void testCompleteRemoval() throws Exception
   {
      String rootStr = "/timeBased/";

      // Add a parent, then a child. LRU will evict the parent,
      // then the child, leaving behind an empty parent
      Fqn parent = Fqn.fromString(rootStr + "parent");
      cache.put(parent, "key", "value");
      cache.put(Fqn.fromRelativeElements(parent, "child"), "key", "value");

      // Give eviction time to run a few times, then confirm parent
      // is completely gone
      assert waitForEviction(cache, 30, TimeUnit.SECONDS, parent);
      // wait for this twice since the first time will only clear the parent's contents since a child exists.
      assert waitForEviction(cache, 30, TimeUnit.SECONDS, parent);

      assertFalse("Parent completely removed", cache.getRoot().hasChild(parent));
   }
}
