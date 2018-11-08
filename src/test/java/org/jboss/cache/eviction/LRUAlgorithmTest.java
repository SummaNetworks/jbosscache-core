package org.jboss.cache.eviction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.RegionImpl;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests for LRUAlgorithm.
 *
 * @author Ben Wang, Feb 11, 2004
 * @author Daniel Huang (dhuang@jboss.org)
 */
@Test(groups = "functional", testName = "eviction.LRUAlgorithmTest")
public class LRUAlgorithmTest extends EvictionTestsBase
{
   RegionManager regionManager;
   LRUAlgorithm algorithm;
   LRUAlgorithmConfig config;
   Log log = LogFactory.getLog(LRUAlgorithm.class);

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      regionManager = new RegionManagerImpl();
      config = new LRUAlgorithmConfig();
      config.setTimeToLive(-1);
      algorithm = (LRUAlgorithm) createAndAssignToRegion("/a/b", regionManager, config);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      regionManager = null;
      config = null;
      algorithm = null;
   }
   
   /**
    * maxNodes = 1. Eception is evictFromCacheNode. Should be commented for now.
    */
   public void testEvictException() throws EvictionException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      config.setMaxNodes(1);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals("Queue size should be ", 1, algorithm.getEvictionQueue().getNumberOfNodes());

      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals("Queue size should be ", 1, algorithm.getEvictionQueue().getNumberOfNodes());
   }


   /**
    * maxNodes = 0 case
    */
   public void testMaxNode1() throws EvictionException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      config.setMaxNodes(0);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals("Queue size should be ", 2, algorithm.getEvictionQueue().getNumberOfNodes());
   }

   /**
    * maxNodes = 1
    */
   public void testMaxNode2() throws EvictionException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      config.setMaxNodes(1);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals("Queue size should be ", 1, algorithm.getEvictionQueue().getNumberOfNodes());

      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);


      algorithm.process(region.getEvictionEventQueue());

      assertEquals("Queue size should be ", 1, algorithm.getEvictionQueue().getNumberOfNodes());
   }

   /**
    * TimeToIdleSeconds = 0
    */
   public void testIdleTimeSeconds1() throws EvictionException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      config.setMaxNodes(-1);
      config.setTimeToLive(-1);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals("Queue size should be ", 2, algorithm.getEvictionQueue().getNumberOfNodes());
   }

   /**
    * TimeToIdleSeconds = 1
    */
   public void testIdleTimeSeconds2() throws EvictionException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      config.setMaxNodes(0);
      config.setTimeToLive(1000);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals("Queue size #1: ", 3, algorithm.getEvictionQueue().getNumberOfNodes());
      TestingUtil.sleepThread(1100);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals("Queue size #2: ", 0, algorithm.getEvictionQueue().getNumberOfNodes());
   }

   /**
    * TimeToIdleSeconds = 1 with node visited in between.
    */
   public void testIdleTimeSeconds3() throws EvictionException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      config.setMaxNodes(0);
      config.setTimeToLive(1000);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals("Queue size #1: ", 3, algorithm.getEvictionQueue().getNumberOfNodes());
      TestingUtil.sleepThread(1100);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.VISIT_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());
      assertEquals("Queue size #2: ", 1, algorithm.getEvictionQueue().getNumberOfNodes());
   }

   /**
    * MaxAgeSeconds = 1 with 3 nodes.
    *
    * @throws Exception
    */
   public void testMaxAgeSeconds1() throws Exception
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      config.setMaxNodes(-1);
      config.setTimeToLive(-1);
      config.setMaxAge(1000);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());
      assertEquals("Queue size #1: ", 3, algorithm.getEvictionQueue().getNumberOfNodes());
      TestingUtil.sleepThread(1100);
      algorithm.process(region.getEvictionEventQueue());
      assertEquals("Queue size #2: ", 0, algorithm.getEvictionQueue().getNumberOfNodes());
   }

   /**
    * Generic combo case.
    */
   public void testCombo1() throws EvictionException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      Fqn fqn4 = Fqn.fromString("/a/b/f");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);

      // Should have a maximum of 2 nodes.
      config.setMaxNodes(2);
      config.setTimeToLive(1000);
      config.setMaxAge(3000);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn4, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());
      EvictionQueue eq = algorithm.getEvictionQueue();

      int numNodesInQueue = eq.getNumberOfNodes();
      assert 2 == numNodesInQueue : "Queue size #1: expected 2 but was " + numNodesInQueue;

      // make sure all nodes now expire
      TestingUtil.sleepThread(1100);

      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      numNodesInQueue = eq.getNumberOfNodes();
      assert 1 == numNodesInQueue : "Queue size #2: expected 1 but was " + numNodesInQueue;

      TestingUtil.sleepThread(3100);
      // visit the node now to prevent the idle time from doing the pruning - node still gets pruned but by
      // max age.
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.VISIT_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      numNodesInQueue = eq.getNumberOfNodes();
      assert 0 == numNodesInQueue : "Queue size #3: expected 0 but was " + numNodesInQueue;
   }

   /**
    * Generic combo case with newly added node should be around.
    */
   public void testCombo2() throws EvictionException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);

      config.setMaxNodes(2);
      config.setTimeToLive(1000);
      config.setMaxAge(3000);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.REMOVE_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      EvictionQueue eq = algorithm.getEvictionQueue();
      int numNodesInQueue = eq.getNumberOfNodes();
      assert 1 == numNodesInQueue : "Queue size #1: expected 1 but was " + numNodesInQueue;

      // make sure existing events all time out
      TestingUtil.sleepThread(1100);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      numNodesInQueue = eq.getNumberOfNodes();
      assert 1 == numNodesInQueue : "Queue size #2: expected 1 but was " + numNodesInQueue;

      TestingUtil.sleepThread(3100);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.VISIT_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      numNodesInQueue = eq.getNumberOfNodes();
      assert 0 == numNodesInQueue : "Queue size #3: expected 0 but was " + numNodesInQueue;
   }

   public void testEvictionSortOrder() throws EvictionException
   {
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);

      config.setMaxAge(1000000);
      config.setMaxNodes(0);
      config.setTimeToLive(1000000);

      for (int i = 0; i < 100; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_NODE_EVENT);
      }

      algorithm.process(region.getEvictionEventQueue());

      for (int i = 0; i < 100; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         if (i % 2 == 0)
         {
            region.registerEvictionEvent(fqn, EvictionEvent.Type.VISIT_NODE_EVENT);
         }
      }

      algorithm.process(region.getEvictionEventQueue());

      LRUQueue queue = (LRUQueue) algorithm.getEvictionQueue();

      NodeEntry ne;
      int count = 0;
      while ((ne = queue.getFirstLRUNodeEntry()) != null)
      {
         if (count < 50)
         {
            assertEquals(1, ne.getNumberOfNodeVisits());
         }
         else
         {
            assertEquals(2, ne.getNumberOfNodeVisits());
         }
         queue.removeNodeEntry(ne);
         count++;
      }

      for (int i = 0; i < 100; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_NODE_EVENT);
      }

      algorithm.process(region.getEvictionEventQueue());
      long lastCreateTimestamp = 0;
      while ((ne = queue.getFirstMaxAgeNodeEntry()) != null)
      {
         assertTrue(ne.getCreationTimeStamp() >= lastCreateTimestamp);
         lastCreateTimestamp = ne.getCreationTimeStamp();
         queue.removeNodeEntry(ne);
      }
   }
}