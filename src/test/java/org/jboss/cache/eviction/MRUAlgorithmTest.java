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
import org.testng.annotations.AfterMethod;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests for MRUAlgorithm.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.MRUAlgorithmTest")
public class MRUAlgorithmTest extends EvictionTestsBase
{
   MRUAlgorithm algorithm;
   RegionManager regionManager;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      regionManager = new RegionManagerImpl();
      MRUAlgorithmConfig config = new MRUAlgorithmConfig();
      config.setMaxNodes(0);
      algorithm = (MRUAlgorithm) createAndAssignToRegion("/a/b", regionManager, config);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      regionManager = null;
      algorithm = null;
   }
   
   public void testMaxNodes() throws Exception
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      MRUAlgorithmConfig config = (MRUAlgorithmConfig) region.getEvictionRegionConfig().getEvictionAlgorithmConfig();
      config.setMaxNodes(1);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);
      algorithm.process(region.getEvictionEventQueue());

      assertEquals(1, algorithm.getEvictionQueue().getNumberOfNodes());

      config.setMaxNodes(100);
      for (int i = 0; i < 150; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/c/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_NODE_EVENT);
      }

      algorithm.process(region.getEvictionEventQueue());

      assertEquals(100, algorithm.getEvictionQueue().getNumberOfNodes());
   }

   public void testMRU() throws Exception
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      Fqn fqn4 = Fqn.fromString("/a/b/f");
      Fqn fqn5 = Fqn.fromString("/a/b/g");
      Fqn fqn6 = Fqn.fromString("/a/b/h");
      Fqn fqn7 = Fqn.fromString("/a/b/i");
      Fqn fqn8 = Fqn.fromString("/a/b/j");
      Fqn fqn9 = Fqn.fromString("/a/b/k");
      Fqn fqn10 = Fqn.fromString("/a/b/l");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      MRUAlgorithmConfig config = (MRUAlgorithmConfig) region.getEvictionRegionConfig().getEvictionAlgorithmConfig();
      config.setMaxNodes(8);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn4, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn5, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn6, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn7, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn8, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals(8, algorithm.getEvictionQueue().getNumberOfNodes());

      region.registerEvictionEvent(fqn9, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn10, EvictionEvent.Type.ADD_NODE_EVENT);

//      Thread.sleep(5000);
      assertEquals(8, algorithm.getEvictionQueue().getNumberOfNodes());

      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn4, EvictionEvent.Type.ADD_NODE_EVENT);

      algorithm.process(region.getEvictionEventQueue());

      assertEquals(8, algorithm.getEvictionQueue().getNumberOfNodes());

      assertNull(algorithm.getEvictionQueue().getNodeEntry(fqn2));
      assertNull("No FQN4 " + algorithm.getEvictionQueue(),
            algorithm.getEvictionQueue().getNodeEntry(fqn4));

      assertNotNull(algorithm.getEvictionQueue().getNodeEntry(fqn1));
      assertNotNull(algorithm.getEvictionQueue().getNodeEntry(fqn3));
      assertNotNull(algorithm.getEvictionQueue().getNodeEntry(fqn5));
      assertNotNull(algorithm.getEvictionQueue().getNodeEntry(fqn6));
      assertNotNull(algorithm.getEvictionQueue().getNodeEntry(fqn7));
      assertNotNull(algorithm.getEvictionQueue().getNodeEntry(fqn8));
      assertNotNull(algorithm.getEvictionQueue().getNodeEntry(fqn9));
      assertNotNull(algorithm.getEvictionQueue().getNodeEntry(fqn10));
   }

}
