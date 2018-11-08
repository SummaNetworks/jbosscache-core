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
 * Unit tests for LFUQueue.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.LFUQueueTest")
public class LFUQueueTest
{
   private LFUQueue queue;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      queue = new LFUQueue();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      queue = null;
   }
   
   public void testQueue() throws Exception
   {
      NodeEntry ne;
      for (int i = 0; i < 500; i++)
      {
         ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         queue.addNodeEntry(ne);
      }

      queue.resortEvictionQueue();

      assertEquals(500, queue.getNumberOfNodes());
      assertTrue(queue.containsNodeEntry(new NodeEntry("/a/b/c/250")));

      NodeEntry ne275 = queue.getNodeEntry("/a/b/c/275");
      assertEquals("/a/b/c/275", ne275.getFqn().toString());

      int k = 0;
      for (NodeEntry entry : queue)
      {
         assertEquals("/a/b/c/" + Integer.toString(k), entry.getFqn().toString());
         if (k % 2 == 0)
         {
            entry.setNumberOfNodeVisits(entry.getNumberOfNodeVisits() + 1);
         }
         k++;
      }

      queue.resortEvictionQueue();

      assertEquals("/a/b/c/1", queue.getFirstNodeEntry().getFqn().toString());

      // now check the sort order.
      k = 0;
      for (NodeEntry entry : queue)
      {
         if (k < 250)
         {
            assertEquals(0, entry.getNumberOfNodeVisits());
         }
         else
         {
            assertEquals(1, entry.getNumberOfNodeVisits());
         }
         k++;
      }

      k = 0;
      while ((ne = queue.getFirstNodeEntry()) != null)
      {
         if (k == 250)
         {
            break;
         }
         queue.removeNodeEntry(ne);
         k++;
      }

      assertEquals(250, queue.getNumberOfNodes());

      assertFalse(queue.containsNodeEntry(new NodeEntry("/a/b/c/275")));
      assertNull(queue.getNodeEntry("/a/b/c/275"));

      for (int i = 0; i < 500; i++)
      {
         if (i % 2 == 0)
         {
            ne = queue.getNodeEntry("/a/b/c/" + Integer.toString(i));
            assertEquals(1, ne.getNumberOfNodeVisits());
            if (i > 250)
            {
               ne.setNumberOfNodeVisits(ne.getNumberOfNodeVisits() + 1);
            }
         }
      }

      queue.resortEvictionQueue();
      assertEquals(250, queue.getNumberOfNodes());

      k = 0;
      for (NodeEntry entry : queue)
      {
         if (k <= 125)
         {
            assertEquals(1, entry.getNumberOfNodeVisits());
         }
         else
         {
            assertEquals(2, entry.getNumberOfNodeVisits());
         }
         k++;
      }

   }

   public void testPrune() throws Exception
   {
      for (int i = 0; i < 500; i++)
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

      assertEquals(250, queue.getNumberOfNodes());

      Set<NodeEntry> removalQueue = queue.getRemovalQueue();
      List<NodeEntry> evictionList = queue.getEvictionList();

      assertEquals(250, removalQueue.size());

      for (NodeEntry ne : removalQueue)
      {
         int currentIndex = Integer.parseInt((String) ne.getFqn().get(3));
         assertEquals(0, currentIndex % 2);

         assertFalse(queue.containsNodeEntry(ne));
         assertNull(queue.getNodeEntry(ne.getFqn()));
         assertTrue(evictionList.contains(ne));
      }

      assertEquals(500, evictionList.size());

      queue.prune();

      assertEquals(0, removalQueue.size());
      assertEquals(250, evictionList.size());
   }

   public void testGetFirstNodeEntry() throws Exception
   {
      for (int i = 0; i < 500; i++)
      {
         NodeEntry ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         queue.addNodeEntry(ne);
         if (i % 2 == 0)
         {
            ne.setNumberOfNodeVisits(2);
         }
      }

      queue.resortEvictionQueue();

      NodeEntry ne;
      int count = 0;
      while ((ne = queue.getFirstNodeEntry()) != null)
      {
         if (count < 250)
         {
            assertEquals(0, ne.getNumberOfNodeVisits());
         }
         else
         {
            assertEquals(2, ne.getNumberOfNodeVisits());
         }
         queue.removeNodeEntry(ne);
         count++;
      }

      assertEquals(0, queue.getNumberOfNodes());
   }


   public void testNumElements() throws Exception
   {
      LFUQueue queue = new LFUQueue();

      NodeEntry ne = new NodeEntry("/a/b/c");
      ne.setNumberOfElements(50);
      queue.addNodeEntry(ne);

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
