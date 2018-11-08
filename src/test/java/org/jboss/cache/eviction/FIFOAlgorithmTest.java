/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.jboss.cache.Fqn;
import org.jboss.cache.RegionImpl;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.RegionRegistry;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests for FIFOAlgorithm.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.FIFOAlgorithmTest")
public class FIFOAlgorithmTest extends EvictionTestsBase
{

   RegionManager regionManager;
   FIFOAlgorithm algo;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      regionManager = new RegionManagerImpl();
      ((RegionManagerImpl) regionManager).injectDependencies(null, null, null, null, null, new RegionRegistry());
      FIFOAlgorithmConfig config = new FIFOAlgorithmConfig();
      config.setMaxNodes(0);
      algo = (FIFOAlgorithm) createAndAssignToRegion("/a/b", regionManager, config);
   }

   public void testMaxNodes1() throws Exception
   {
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      FIFOAlgorithmConfig config = (FIFOAlgorithmConfig) region.getEvictionRegionConfig().getEvictionAlgorithmConfig();
      config.setMaxNodes(5);

      for (int i = 0; i < 8; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_NODE_EVENT);
      }

      algo.process(region.getEvictionEventQueue());
      FIFOQueue queue = (FIFOQueue) algo.evictionQueue;
      assertEquals(5, algo.getEvictionQueue().getNumberOfNodes());

      // now verify the order.
      int index = 3;
      for (NodeEntry ne : queue)
      {
         String fqn = ne.getFqn().toString();
         assertTrue(fqn.endsWith("/" + Integer.toString(index)));
         index++;
      }

      // now verify the same order still exists after visiting the nodes.
      for (int i = 3; i < 8; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.VISIT_NODE_EVENT);
      }
      for (int i = 3; i < 5; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.VISIT_NODE_EVENT);
      }

      algo.process(region.getEvictionEventQueue());

      assertEquals(5, algo.getEvictionQueue().getNumberOfNodes());

      index = 3;
      for (NodeEntry ne : queue)
      {
         String fqn = ne.getFqn().toString();
         assertTrue(fqn.endsWith("/" + Integer.toString(index)));
         index++;
      }
   }

   public void testMaxNodes2() throws Exception
   {
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      FIFOAlgorithmConfig config = (FIFOAlgorithmConfig) region.getEvictionRegionConfig().getEvictionAlgorithmConfig();
      config.setMaxNodes(500);

      for (int i = 0; i < 500; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_NODE_EVENT);
      }

      algo.process(region.getEvictionEventQueue());
      FIFOQueue queue = (FIFOQueue) algo.evictionQueue;
      assertEquals(500, algo.getEvictionQueue().getNumberOfNodes());

      int index = 0;
      for (NodeEntry ne : queue)
      {
         assertTrue(ne.getFqn().toString().endsWith("/" + Integer.toString(index)));
         index++;
      }

      for (int i = 500; i < 600; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_NODE_EVENT);
      }

      algo.process(region.getEvictionEventQueue());

      index = 100;
      for (NodeEntry ne : queue)
      {
         assertTrue(ne.getFqn().toString().endsWith("/" + Integer.toString(index)));
         index++;
      }
   }
}
