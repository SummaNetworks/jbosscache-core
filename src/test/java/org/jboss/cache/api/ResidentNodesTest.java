package org.jboss.cache.api;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.internals.EvictionController;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Tester class for Node.isResident functionality.
 *
 * @author <a href="mailto:mircea.markus@jboss.com">Mircea Markus</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "pessimistic"}, sequential = true, testName = "api.ResidentNodesTest")
public class ResidentNodesTest
{
   private CacheSPI<Object, Object> cache;
   private EvictionController evController;

   private final String TEST_NODES_ROOT = "residentNodesTest";
   private Cache[] caches = {};

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      Configuration cacheConfig = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      cacheConfig.setCacheMode(Configuration.CacheMode.LOCAL);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(cacheConfig, false, getClass());
      cache.getConfiguration().getEvictionConfig().setWakeupInterval(1000);
      createNewRegion();
      cache.start();
      evController = new EvictionController(cache);
   }

   /**
    * Setting up a new region for our purposes.
    */
   private void createNewRegion()
   {
      EvictionConfig evConfig = cache.getConfiguration().getEvictionConfig();
      EvictionRegionConfig evRegConfig = new EvictionRegionConfig();
      evRegConfig.setRegionFqn(Fqn.fromString("/" + TEST_NODES_ROOT));
      evRegConfig.setEventQueueSize(100);
      LRUAlgorithmConfig lruConfig = new LRUAlgorithmConfig();
      lruConfig.setMaxAge(100000000);
      lruConfig.setTimeToLive(100000000);
      lruConfig.setMaxNodes(3);
      evRegConfig.setEvictionAlgorithmConfig(lruConfig);
      evConfig.addEvictionRegionConfig(evRegConfig);
      //end setting up region stuff
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      if (caches != null) TestingUtil.killCaches(caches);
      cache = null;
      caches = null;
      evController = null;
   }

   /**
    * Mark some nodes as resident and show that they won't get evicted,
    * even if normally scenario they would
    */
   public void testHappyFlow() throws InterruptedException
   {
      cache.put(getSubFqn("/a"), "k_a", "v_a");
      cache.getNode(getSubFqn("/a")).setResident(true);
      cache.put(getSubFqn("/b"), "k_b", "v_b");
      cache.getNode(getSubFqn("/b")).setResident(true);
      cache.put(getSubFqn("/c"), "k_c", "v_c");
      cache.put(getSubFqn("/d"), "k_d", "v_d");
      cache.put(getSubFqn("/e"), "k_e", "v_e");
      cache.put(getSubFqn("/f"), "k_f", "v_f");
      cache.put(getSubFqn("/g"), "k_g", "v_g");
      cache.put(getSubFqn("/h"), "k_h", "v_h");
      cache.put(getSubFqn("/i"), "k_i", "v_i");

      evController.startEviction();

      assertTrue(cache.exists(getSubFqn("/a")));
      assertTrue(cache.exists(getSubFqn("/b")));
      assertFalse(cache.exists(getSubFqn("/c")));
      assertFalse(cache.exists(getSubFqn("/d")));
      assertFalse(cache.exists(getSubFqn("/e")));
      assertFalse(cache.exists(getSubFqn("/f")));

      //only last three used are not evicted
      assertTrue(cache.exists(getSubFqn("/g")));
      assertTrue(cache.exists(getSubFqn("/h")));
      assertTrue(cache.exists(getSubFqn("/i")));

   }

   public void simpleTest() throws Exception
   {
      cache.put(getSubFqn("/a"), "k_a", "v_a");
      cache.put(getSubFqn("/b"), "k_b", "v_b");
      cache.put(getSubFqn("/c"), "k_c", "v_c");
      cache.put(getSubFqn("/d"), "k_d", "v_d");

      evController.startEviction();

      assertFalse(cache.exists(getSubFqn("/a")));
      assertTrue(cache.exists(getSubFqn("/b")));
      assertTrue(cache.exists(getSubFqn("/c")));
      assertTrue(cache.exists(getSubFqn("/d")));
   }

   /**
    * If a node is marked as resident, and a get is made on that given node then an VISITED event would normally be
    * added to the eviction queue. In a LRU scenario, this will cause another node to be evicted given that the size of
    * the eviction queue is bounded. This test makes sure that this scenario will not hapen.
    */
   public void testNoEvictionEventsForResidentNodes() throws InterruptedException
   {
      cache.put(getSubFqn("/a"), "k_a", "v_a");
      cache.put(getSubFqn("/b"), "k_b", "v_b");

      cache.getNode(getSubFqn("/a")).setResident(true);
      cache.getNode(getSubFqn("/b")).setResident(true);

      cache.put(getSubFqn("/c"), "k_c", "v_c");
      cache.put(getSubFqn("/d"), "k_d", "v_d");
      cache.put(getSubFqn("/e"), "k_e", "v_e");
      cache.put(getSubFqn("/f"), "k_f", "v_f");
      cache.put(getSubFqn("/g"), "k_g", "v_g");
      cache.put(getSubFqn("/h"), "k_h", "v_h");

      //at this point the oldest nodes are /a and /b so. There are eviction events in the queue corresponding
      // to those nodes
      cache.getNode(getSubFqn("/a"));
      cache.getNode(getSubFqn("/b"));

      evController.startEviction();

      //a and b should exist as those were marked resident. Also they shouldn't be caunted as nodes in the eviction
      // queue
      assertTrue(cache.exists(getSubFqn("/a")));
      assertTrue(cache.exists(getSubFqn("/b")));

      // c, d and e were the first accessed, they should be evicted
      assertFalse(cache.exists(getSubFqn("/c")));
      assertFalse(cache.exists(getSubFqn("/d")));
      assertFalse(cache.exists(getSubFqn("/e")));

      //all of them should be there - even if we re-retrieved a and b at a prev step (cache.get(getSubFqn("/a"))) this
      //call shouldn't create an eviction event.
      assertTrue(cache.exists(getSubFqn("/f")));
      assertTrue(cache.exists(getSubFqn("/g")));
      assertTrue(cache.exists(getSubFqn("/h")));
   }

   /**
    * Check the behavior whilst using optimistic locking.
    */
   public void testResidencyAndOptimisticLocking() throws Exception
   {

      Configuration config = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      config.setCacheMode(Configuration.CacheMode.LOCAL);
      config.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(config, true, getClass());

      cache.put(Fqn.fromString("/a/b"), "key", "value");
      TransactionManager txManager = cache.getTransactionManager();
      txManager.begin();
      cache.getRoot().getChild(Fqn.fromString("/a/b")).setResident(true);
      cache.getRoot().getChild(Fqn.fromString("/a/b")).put("k2", "v2");
      assertEquals(cache.getRoot().getChild(Fqn.fromString("/a/b")).getKeys().size(), 2);
      txManager.rollback();
      assertTrue(cache.getRoot().getChild(Fqn.fromString("/a/b")).isResident());

      txManager.begin();
      cache.getRoot().getChild(Fqn.fromString("/a/b")).setResident(false);
      cache.getRoot().getChild(Fqn.fromString("/a/b")).put("k2", "v2");
      assertEquals(cache.getRoot().getChild(Fqn.fromString("/a/b")).getKeys().size(), 2);
      txManager.commit();
      assertFalse(cache.getRoot().getChild(Fqn.fromString("/a/b")).isResident());
      assertEquals(cache.getRoot().getChild(Fqn.fromString("/a/b")).getKeys().size(), 2);

      try
      {
         if (cache.getTransactionManager().getTransaction() != null)
         {
            cache.getTransactionManager().rollback();
         }
      }
      finally
      {
         cache.stop();
      }
   }

   public void testResidencyAndPessimisticLocking() throws Exception
   {
      cache.put(Fqn.fromString("/a/b"), "key", "value");
      TransactionManager txManager = cache.getTransactionManager();

      assert cache.getNumberOfLocksHeld() == 0 : "Should have no stale locks!";

      txManager.begin();
      cache.getRoot().getChild(Fqn.fromString("/a/b")).setResident(true);
      cache.getRoot().getChild(Fqn.fromString("/a/b")).put("k2", "v2");
      assertEquals(cache.getRoot().getChild(Fqn.fromString("/a/b")).getKeys().size(), 2);
      txManager.rollback();
      assertTrue(cache.getRoot().getChild(Fqn.fromString("/a/b")).isResident());

      assert cache.getNumberOfLocksHeld() == 0 : "Should have no stale locks!";

      txManager.begin();
      cache.getRoot().getChild(Fqn.fromString("/a/b")).setResident(false);
      cache.getRoot().getChild(Fqn.fromString("/a/b")).put("k2", "v2");
      assertEquals(cache.getRoot().getChild(Fqn.fromString("/a/b")).getKeys().size(), 2);
      txManager.commit();
      assertFalse(cache.getRoot().getChild(Fqn.fromString("/a/b")).isResident());
      assertEquals(cache.getRoot().getChild(Fqn.fromString("/a/b")).getKeys().size(), 2);
   }

   private Fqn getSubFqn(String str)
   {
      return Fqn.fromString("/" + TEST_NODES_ROOT + str);
   }
}
