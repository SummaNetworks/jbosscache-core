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
import org.jboss.cache.config.EvictionRegionConfig;
import org.testng.annotations.AfterMethod;
import static org.jboss.cache.eviction.EvictionEvent.Type.*;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit test for LFUAlgorithm.
 *
 * @author Daniel Huang - dhuang@jboss.org - 10/2005
 * @version $Revision: 7289 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.LFUAlgorithmTest")
public class LFUAlgorithmTest extends EvictionTestsBase
{
   RegionManager regionManager;
   LFUAlgorithm algo;
   LFUAlgorithmConfig config;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      regionManager = new RegionManagerImpl();
      config = new LFUAlgorithmConfig();
      algo = (LFUAlgorithm) createAndAssignToRegion("/a/b", regionManager, config);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      regionManager = null;
      config = null;
      algo = null;
   }
   
   public void testMaxNode1()
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      config.setMaxNodes(-1);
      config.setMinNodes(20);
      region.registerEvictionEvent(fqn1, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, ADD_NODE_EVENT);
      try
      {
         algo.process(region.getEvictionEventQueue());
      }
      catch (EvictionException e)
      {
         fail("testMaxNode: process failed " + e);
         e.printStackTrace();
      }
      assertEquals("Queue size should be ", 2, algo.getEvictionQueue().getNumberOfNodes());

   }

   public void testMaxNode2()
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      config.setMaxNodes(1);
      config.setMinNodes(20);
      region.registerEvictionEvent(fqn1, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, ADD_NODE_EVENT);

      try
      {
         algo.process(region.getEvictionEventQueue());
      }
      catch (EvictionException e)
      {
         fail("testMaxNode: process failed " + e);
         e.printStackTrace();
      }
      assertEquals("Queue size should be ", 1, algo.getEvictionQueue().getNumberOfNodes());

      region.registerEvictionEvent(fqn2, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, ADD_NODE_EVENT);


      try
      {
         algo.process(region.getEvictionEventQueue());
      }
      catch (EvictionException e)
      {
         fail("testMaxNode: process failed " + e);
         e.printStackTrace();
      }
      assertEquals("Queue size should be ", 1, algo.getEvictionQueue().getNumberOfNodes());
   }

   public void testMinNode1() throws Exception
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/c/d");
      Fqn fqn3 = Fqn.fromString("/a/b/c/d/e");
      Fqn fqn4 = Fqn.fromString("/a/b/c/d/e/f");

      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      LFUAlgorithmConfig config = (LFUAlgorithmConfig) region.getEvictionRegionConfig().getEvictionAlgorithmConfig();

      config.setMaxNodes(-1);
      config.setMinNodes(2);

      region.registerEvictionEvent(fqn1, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn4, ADD_NODE_EVENT);

      algo.process(region.getEvictionEventQueue());

      assertEquals("Queue size should be ", 2, algo.getEvictionQueue().getNumberOfNodes());
   }

   public void testMinNode2() throws Exception
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");

      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      LFUAlgorithmConfig config = (LFUAlgorithmConfig) region.getEvictionRegionConfig().getEvictionAlgorithmConfig();

      config.setMaxNodes(-1);
      config.setMinNodes(-1);

      region.registerEvictionEvent(fqn1, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, ADD_NODE_EVENT);

      algo.process(region.getEvictionEventQueue());

      assertEquals("Queue size should be ", 0, algo.getEvictionQueue().getNumberOfNodes());
   }

   public void testEvictionQueueSortOrder1() throws Exception
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/c/d");
      Fqn fqn3 = Fqn.fromString("/a/b/c/d/e");
      Fqn fqn4 = Fqn.fromString("/a/b/c/d/e/f");
      Fqn fqn5 = Fqn.fromString("/a/b/c/d/e/f/g/h");
      Fqn fqn6 = Fqn.fromString("/a/b/c/d/e/f/g/h/i");
      Fqn fqn7 = Fqn.fromString("/a/b/c/d/e/f/g/h/i/j");
      Fqn fqn8 = Fqn.fromString("/a/b/c/d/e/f/g/h/i/j/k");
      Fqn fqn9 = Fqn.fromString("/a/b/c/d/e/f/g/h/i/j/k/l");
      Fqn fqn10 = Fqn.fromString("/a/b/c/d/e/f/g/h/i/j/k/l/m");


      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      LFUAlgorithmConfig config = (LFUAlgorithmConfig) region.getEvictionRegionConfig().getEvictionAlgorithmConfig();
      config.setMaxNodes(-1);
      config.setMinNodes(100);

      region.registerEvictionEvent(fqn1, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn4, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn5, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn6, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn7, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn8, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn9, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn10, ADD_NODE_EVENT);

      algo.process(region.getEvictionEventQueue());
      LFUQueue queue = (LFUQueue) algo.evictionQueue;
      assertEquals(10, algo.getEvictionQueue().getNumberOfNodes());

      for (NodeEntry ne : queue)
      {
         assertEquals(1, ne.getNumberOfNodeVisits());
      }

      // fqn1 visited 4 additional times.
      region.registerEvictionEvent(fqn1, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn1, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn1, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn1, VISIT_NODE_EVENT);

      // fqn2 visited 3 additional times.
      region.registerEvictionEvent(fqn2, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn2, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn2, VISIT_NODE_EVENT);

      // fqn3 visited 1 additional time.
      region.registerEvictionEvent(fqn3, VISIT_NODE_EVENT);

      // fqn4 visited 2 additional times.
      region.registerEvictionEvent(fqn4, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn4, VISIT_NODE_EVENT);

      // fqn9 visited 1 additional time.
      region.registerEvictionEvent(fqn9, VISIT_NODE_EVENT);

      // fqn10 visited 2 additional times.
      region.registerEvictionEvent(fqn10, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn10, VISIT_NODE_EVENT);

      algo.process(region.getEvictionEventQueue());
      int count = 0;
      for (NodeEntry ne : queue)
      {
         count++;
         if (count == 5 || count == 6)
         {
            assertEquals(2, ne.getNumberOfNodeVisits());
         }
         else if (count == 7 || count == 8)
         {
            assertEquals(3, ne.getNumberOfNodeVisits());
         }
         else if (count == 9)
         {
            assertEquals(4, ne.getNumberOfNodeVisits());
         }
         else if (count == 10)
         {
            assertEquals(5, ne.getNumberOfNodeVisits());
         }
         else
         {
            assertEquals(1, ne.getNumberOfNodeVisits());
         }
      }


      assertEquals(10, algo.getEvictionQueue().getNumberOfNodes());

      Fqn fqn11 = Fqn.fromString("/a");
      Fqn fqn12 = Fqn.fromString("/a/b");

      region.registerEvictionEvent(fqn11, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn12, ADD_NODE_EVENT);

      algo.process(region.getEvictionEventQueue());

      count = 0;
      for (NodeEntry ne : queue)
      {
         count++;
         if (count == 7 || count == 8)
         {
            assertEquals(2, ne.getNumberOfNodeVisits());
         }
         else if (count == 9 || count == 10)
         {
            assertEquals(3, ne.getNumberOfNodeVisits());
         }
         else if (count == 11)
         {
            assertEquals(4, ne.getNumberOfNodeVisits());
         }
         else if (count == 12)
         {
            assertEquals(5, ne.getNumberOfNodeVisits());
         }
         else
         {
            assertEquals(1, ne.getNumberOfNodeVisits());
         }
      }

      assertEquals(12, algo.getEvictionQueue().getNumberOfNodes());

      region.registerEvictionEvent(fqn1, REMOVE_NODE_EVENT);
      region.registerEvictionEvent(fqn11, REMOVE_NODE_EVENT);
      region.registerEvictionEvent(fqn12, REMOVE_NODE_EVENT);
      region.registerEvictionEvent(fqn10, REMOVE_NODE_EVENT);

      algo.process(region.getEvictionEventQueue());

      count = 0;
      for (NodeEntry ne : queue)
      {
         count++;
         if (count == 5 || count == 6)
         {
            assertEquals(2, ne.getNumberOfNodeVisits());
         }
         else if (count == 7)
         {
            assertEquals(3, ne.getNumberOfNodeVisits());
         }
         else if (count == 8)
         {
            assertEquals(4, ne.getNumberOfNodeVisits());
         }
         else
         {
            assertEquals(1, ne.getNumberOfNodeVisits());
         }
      }

      assertEquals(8, algo.getEvictionQueue().getNumberOfNodes());

      //test add/visit/remove combination
      region.registerEvictionEvent(fqn11, ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn11, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn11, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn11, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn11, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn11, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn4, VISIT_NODE_EVENT);

      // purposefully revisit a node that has been removed. assert that it is readded.
      region.registerEvictionEvent(fqn1, VISIT_NODE_EVENT);
      region.registerEvictionEvent(fqn1, VISIT_NODE_EVENT);

      region.registerEvictionEvent(fqn3, REMOVE_NODE_EVENT);


      algo.process(region.getEvictionEventQueue());

      count = 0;
      for (NodeEntry ne : queue)
      {
         count++;
         if (count == 5 || count == 6)
         {
            assertEquals(2, ne.getNumberOfNodeVisits());
         }
         else if (count == 7 || count == 8)
         {
            assertEquals(4, ne.getNumberOfNodeVisits());
         }
         else if (count == 9)
         {
            assertEquals(6, ne.getNumberOfNodeVisits());
         }
         else
         {
            assertEquals(1, ne.getNumberOfNodeVisits());
         }
      }
      assertEquals(9, algo.getEvictionQueue().getNumberOfNodes());
   }

   public void testEvictionQueueSortOrder2() throws Exception
   {
      RegionImpl region = (RegionImpl) regionManager.getRegion("/a/b", true);
      EvictionRegionConfig config = region.getEvictionRegionConfig();

      ((LFUAlgorithmConfig) config.getEvictionAlgorithmConfig()).setMaxNodes(-1);
      ((LFUAlgorithmConfig) config.getEvictionAlgorithmConfig()).setMinNodes(10000);
      for (int i = 0; i < 10000; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
         region.registerEvictionEvent(fqn, ADD_NODE_EVENT);
      }

      algo.process(region.getEvictionEventQueue());
      LFUQueue queue = (LFUQueue) algo.evictionQueue;

      long lastModifiedTimestamp = 0;
      for (NodeEntry ne : queue)
      {
         assertTrue(lastModifiedTimestamp <= ne.getModifiedTimeStamp());
         lastModifiedTimestamp = ne.getModifiedTimeStamp();
      }

      for (int i = 0; i < 10000; i++)
      {
         if ((i % 2) == 0)
         {
            Fqn fqn = Fqn.fromString("/a/b/" + Integer.toString(i));
            region.registerEvictionEvent(fqn, VISIT_NODE_EVENT);
         }
      }

      algo.process(region.getEvictionEventQueue());

      int count = 0;
      lastModifiedTimestamp = 0;
      for (NodeEntry ne : queue)
      {
         assertTrue(lastModifiedTimestamp <= ne.getModifiedTimeStamp());
         lastModifiedTimestamp = ne.getModifiedTimeStamp();

         if (count < 5000)
         {
            assertEquals(1, ne.getNumberOfNodeVisits());
         }
         else
         {
            assertEquals(2, ne.getNumberOfNodeVisits());
         }
         count++;

      }

      assertEquals(10000, algo.getEvictionQueue().getNumberOfNodes());
   }
}
