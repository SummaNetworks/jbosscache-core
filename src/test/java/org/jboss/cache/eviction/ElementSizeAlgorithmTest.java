package org.jboss.cache.eviction;

import org.jboss.cache.Fqn;
import org.jboss.cache.RegionImpl;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Daniel Huang
 * @version $Revision: 7289 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.ElementSizeAlgorithmTest")
public class ElementSizeAlgorithmTest extends EvictionTestsBase
{
   RegionManager regionManager;
   ElementSizeAlgorithm algo;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      regionManager = new RegionManagerImpl();
      ElementSizeAlgorithmConfig config = new ElementSizeAlgorithmConfig();
      config.setMaxElementsPerNode(0);
      algo = (ElementSizeAlgorithm) createAndAssignToRegion("/a/b", regionManager, config);
   }

   public void testMaxElements() throws Exception
   {
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      ElementSizeAlgorithmConfig config = (ElementSizeAlgorithmConfig) region.getEvictionRegionConfig().getEvictionAlgorithmConfig();
      config.setMaxNodes(10);
      config.setMaxElementsPerNode(6);

      for (int i = 0; i < 10; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_NODE_EVENT);
         if (i % 2 == 0)
         {
            for (int k = 0; k < i; k++)
            {
               region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_ELEMENT_EVENT);
            }
         }
      }

      algo.process(region.getEvictionEventQueue());

      ElementSizeQueue queue = (ElementSizeQueue) algo.evictionQueue;
      assertEquals(9, algo.getEvictionQueue().getNumberOfNodes());
      assertEquals(12, algo.getEvictionQueue().getNumberOfElements());
      // now verify the order.
      int count = 6;
      for (NodeEntry ne : queue)
      {
         if (count > 0)
         {
            assertEquals(count, ne.getNumberOfElements());
         }
         else
         {
            assertEquals(0, ne.getNumberOfElements());
         }
         count -= 2;
      }

      for (int i = 0; i < 7; i++)
      {
         region.registerEvictionEvent(Fqn.fromString("/a/b/9"), EvictionEvent.Type.ADD_ELEMENT_EVENT);
         region.registerEvictionEvent(Fqn.fromString("/a/b/7"), EvictionEvent.Type.ADD_ELEMENT_EVENT);
      }

      algo.process(region.getEvictionEventQueue());

      assertEquals(7, queue.getNumberOfNodes());
   }

   public void testMaxNodesAndMaxElements() throws Exception
   {
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      ElementSizeAlgorithmConfig config = (ElementSizeAlgorithmConfig) region.getEvictionRegionConfig().getEvictionAlgorithmConfig();
      config.setMaxNodes(10);
      config.setMaxElementsPerNode(100);

      for (int i = 0; i < 20; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_NODE_EVENT);
         for (int k = 0; k < i; k++)
         {
            region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_ELEMENT_EVENT);

         }
      }

      algo.process(region.getEvictionEventQueue());

      ElementSizeQueue queue = (ElementSizeQueue) algo.evictionQueue;
      assertEquals(10, algo.getEvictionQueue().getNumberOfNodes());
      assertEquals(45, algo.getEvictionQueue().getNumberOfElements());

      // now verify the order.
      int num = 9;
      for (NodeEntry ne : queue)
      {
         assertEquals(num, ne.getNumberOfElements());
         num--;
      }

   }
}
