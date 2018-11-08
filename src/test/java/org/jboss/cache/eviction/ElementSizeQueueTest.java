/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import static org.testng.AssertJUnit.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import org.testng.annotations.AfterMethod;


/**
 * @author Daniel Huang
 * @version $Revision: 7289 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.ElementSizeQueueTest")
public class ElementSizeQueueTest
{
   private ElementSizeQueue queue;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      queue = new ElementSizeQueue();
   }
   
   @AfterMethod(alwaysRun = true)
   public void tearDown() {
      queue = null;
   }


   public void testQueue() throws Exception
   {
      for (int i = 0; i < 500; i++)
      {
         queue.addNodeEntry(new NodeEntry("/a/b/c/" + Integer.toString(i)));
      }

      queue.resortEvictionQueue();

      assertEquals(500, queue.getNumberOfNodes());
      assertTrue(queue.containsNodeEntry(new NodeEntry("/a/b/c/250")));

      NodeEntry ne275 = queue.getNodeEntry("/a/b/c/275");
      assertEquals("/a/b/c/275", ne275.getFqn().toString());

      // now make sure the ordering is correct.
      int k = 0;
      for (NodeEntry ne : queue)
      {
         assertEquals("/a/b/c/" + Integer.toString(k), ne.getFqn().toString());
         if (k % 2 == 0)
         {
            ne.setNumberOfElements(k);
         }
         k++;
      }

      queue.resortEvictionQueue();

      k = 0;
      for (NodeEntry ne : queue)
      {

         // the first 250 elements should have (250 - 1 - n) * 2 elements.  The rest should have 0 elements.
         int expectedElements = 0;
         if (k < 250)
         {
            expectedElements = (250 - 1 - k) * 2;
         }

         assertEquals("k is " + k, expectedElements, ne.getNumberOfElements());

         k++;
      }
   }

   public void testPrune() throws Exception
   {
      for (int i = 0; i < 5000; i++)
      {
         queue.addNodeEntry(new NodeEntry("/a/b/c/" + Integer.toString(i)));
      }

      int i = 0;
      for (NodeEntry ne : queue)
      {
         if (i % 2 == 0)
         {
            queue.removeNodeEntry(ne);
         }
         i++;
      }

      assertEquals(2500, queue.getNumberOfNodes());

      Set<NodeEntry> removalQueue = queue.getRemovalQueue();
      List<NodeEntry> evictionList = queue.getEvictionList();

      assertEquals(2500, removalQueue.size());

      for (NodeEntry ne : removalQueue)
      {
         int currentIndex = Integer.parseInt((String) ne.getFqn().get(3));
         assertEquals(0, currentIndex % 2);

         assertFalse(queue.containsNodeEntry(ne));
         assertNull(queue.getNodeEntry(ne.getFqn()));
         assertTrue(evictionList.contains(ne));
      }

      assertEquals(5000, evictionList.size());

      queue.prune();

      assertEquals(0, removalQueue.size());
      assertEquals(2500, evictionList.size());
   }

   public void testGetFirstNodeEntry() throws Exception
   {
      for (int i = 0; i < 500; i++)
      {
         NodeEntry ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         queue.addNodeEntry(ne);
         if (i % 2 == 0)
         {
            ne.setNumberOfElements(2);
         }
      }

      queue.resortEvictionQueue();

      NodeEntry ne;
      int count = 0;
      while ((ne = queue.getFirstNodeEntry()) != null)
      {
         if (count < 250)
         {
            assertEquals(2, ne.getNumberOfElements());
         }
         else
         {
            assertEquals(0, ne.getNumberOfNodeVisits());
         }
         queue.removeNodeEntry(ne);
         count++;
      }

      assertEquals(0, queue.getNumberOfNodes());
   }

   public void testNumElements() throws Exception
   {
      ElementSizeQueue queue = new ElementSizeQueue();

      NodeEntry ne = new NodeEntry("/a/b/c");
      ne.setNumberOfElements(50);
      queue.addNodeEntry(ne);

      queue.resortEvictionQueue();
      assertEquals(50, queue.getNumberOfElements());
      assertEquals(1, queue.getNumberOfNodes());

      queue.removeNodeEntry(ne);
      assertEquals(0, queue.getNumberOfElements());

      for (int i = 0; i < 10; i++)
      {
         ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         ne.setNumberOfElements(i);
         queue.addNodeEntry(ne);
      }
      queue.resortEvictionQueue();
      assertEquals(45, queue.getNumberOfElements());
      assertEquals(10, queue.getNumberOfNodes());

      ne = queue.getNodeEntry("/a/b/c/0");
      assertNotNull(ne);
      assertEquals(0, ne.getNumberOfElements());
      ne.setNumberOfElements(500);

      assertEquals(545, queue.getNumberOfElements());
      ne = queue.getNodeEntry("/a/b/c/0");
      assertEquals(500, ne.getNumberOfElements());

      queue.resortEvictionQueue();

      ne = queue.getNodeEntry("/a/b/c/1");
      assertNotNull(ne);
      assertEquals(1, ne.getNumberOfElements());

      queue.resortEvictionQueue();
      ne.setNumberOfElements(2);
      queue.resortEvictionQueue();
      assertEquals(546, queue.getNumberOfElements());

      queue.removeNodeEntry(ne);

      assertEquals(544, queue.getNumberOfElements());
      assertEquals(9, queue.getNumberOfNodes());

      queue.removeNodeEntry(queue.getNodeEntry("/a/b/c/0"));

      for (int i = 2; i < 10; i++)
      {
         ne = queue.getNodeEntry("/a/b/c/" + Integer.toString(i));
         assertEquals(i, ne.getNumberOfElements());
         queue.removeNodeEntry(ne);
      }

      assertEquals(0, queue.getNumberOfNodes());
      assertEquals(0, queue.getNumberOfElements());

   }

}
