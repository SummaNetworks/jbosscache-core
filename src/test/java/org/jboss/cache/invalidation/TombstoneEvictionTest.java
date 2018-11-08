package org.jboss.cache.invalidation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.FIFOAlgorithmConfig;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Make sure tombstones are evicted
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "optimistic"}, testName = "invalidation.TombstoneEvictionTest")
public class TombstoneEvictionTest extends AbstractMultipleCachesTest
{
   private CacheSPI c1, c2;
   private Fqn fqn = Fqn.fromString("/data/test");
   private Fqn dummy = Fqn.fromString("/data/dummy");
   private EvictionController ec1;
   private EvictionController ec2;


   protected void createCaches() throws Throwable
   {
      Configuration c = new Configuration();

      // the FIFO policy cfg
      FIFOAlgorithmConfig cfg = new FIFOAlgorithmConfig();
      cfg.setMaxNodes(1);
      cfg.setMinTimeToLive(0);

      // the region configuration
      EvictionRegionConfig regionCfg = new EvictionRegionConfig();
      regionCfg.setRegionFqn(dummy.getParent());
      regionCfg.setRegionName(dummy.getParent().toString());
      regionCfg.setEvictionAlgorithmConfig(cfg);

      // set regions in a list
      List<EvictionRegionConfig> evictionRegionConfigs = new ArrayList<EvictionRegionConfig>();
      evictionRegionConfigs.add(regionCfg);


      EvictionConfig ec = new EvictionConfig();
      ec.setWakeupInterval(-1);
      ec.setEvictionRegionConfigs(evictionRegionConfigs);

      c.setCacheMode(Configuration.CacheMode.INVALIDATION_SYNC);
      c.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      c.setEvictionConfig(ec);

      c1 = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      c2 = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), false, getClass());

      c1.start();
      c2.start();
      ec1 = new EvictionController(c1);
      ec2 = new EvictionController(c2);
      TestingUtil.blockUntilViewsReceived(60000, c1, c2);
      registerCaches(c1, c2);
   }

   @BeforeMethod
   public void clearQueues()
   {
      clearRegions(c1.getRegionManager().getAllRegions(Region.Type.ANY));
      clearRegions(c2.getRegionManager().getAllRegions(Region.Type.ANY));
   }

   private void clearRegions(List<Region> regionList)
   {
      for (Region region : regionList)
      {
         region.resetEvictionQueues();
      }
   }

   private static final Log log = LogFactory.getLog(TombstoneEvictionTest.class);

   public void testControl()
   {
      log.trace("***** entered testControl()");
      c1.put(fqn, "k", "v");
      c1.put(dummy, "k", "v");
      log.trace("***** nodes were added testControl()");


      assert c1.peek(fqn, false, true) != null : "Node should exist";
      assert c1.peek(dummy, false, true) != null : "Node should exist";

      ec1.startEviction();

      assert c1.peek(fqn, false, true) == null : "Should have evicted";
      assert c1.peek(dummy, false, true) != null : "Node should exist";
   }

   public void testWithInvalidationMarkers()
   {
      log.trace(" **** testWithInvalidationMarkers() before put");
      c1.put(fqn, "k", "v");
      c1.put(dummy, "k", "v");
      log.trace(" **** testWithInvalidationMarkers() after put");

      assert c1.peek(fqn, false, true) != null : "Node should exist";
      assert c1.peek(dummy, false, true) != null : "Node should exist";

      assert c2.peek(fqn, false, true) != null : "Node should exist";
      assert c2.peek(dummy, false, true) != null : "Node should exist";

      ec1.startEviction();
      ec2.startEviction();

      assert c1.peek(fqn, false, true) == null : "Should have evicted";
      assert c1.peek(dummy, false, true) != null : "Node should exist";

      assert c2.peek(fqn, false, true) == null : "Should have evicted";
      assert c2.peek(dummy, false, true) != null : "Node should exist";
   }

   public void testWithTombstones()
   {
      c1.put(fqn, "k", "v");
      c1.removeNode(fqn);
      c1.put(dummy, "k", "v");

      assert c1.peek(fqn, false, true) != null : "Node should exist";
      assert c1.peek(dummy, false, true) != null : "Node should exist";

      assert c2.peek(fqn, false, true) != null : "Node should exist";
      assert c2.peek(dummy, false, true) != null : "Node should exist";

      ec1.startEviction();
      ec2.startEviction();

      assert c1.peek(fqn, false, true) == null : "Should have evicted";
      assert c1.peek(dummy, false, true) != null : "Node should exist";

      assert c2.peek(fqn, false, true) == null : "Should have evicted";
      assert c2.peek(dummy, false, true) != null : "Node should exist";
   }
}
