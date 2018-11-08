/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.Iterator;

import org.jboss.cache.Fqn;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
/**
 * Unit tests for LRUQueue.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.LRUQueueTest")
public class LRUQueueTest
{
   private LRUQueue queue;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      queue = new LRUQueue();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      queue = null;
   }
   
   public void testQueue() throws Exception
   {
      for (int i = 0; i < 500; i++)
      {
         NodeEntry ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         queue.addNodeEntry(ne);
      }

      assertEquals(500, queue.getNumberOfNodes());

      for (int i = 0; i < 500; i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/c/" + Integer.toString(i));
         if ((i < 100) || (i >= 300 && i < 400))
         {
            // visit the nodes from 0-99 and the nodes from 300 - 399
            queue.reorderByLRU(fqn);
         }
      }

      // visiting the nodes should have no affect ont he maxAgeQueue.
      Iterator maxAgeIt = queue.iterateMaxAgeQueue();
      int count = 0;
      long lastTs = 0;
      while (maxAgeIt.hasNext())
      {
         NodeEntry ne = (NodeEntry) maxAgeIt.next();
         assertTrue(lastTs <= ne.getCreationTimeStamp());
         lastTs = ne.getCreationTimeStamp();
         count++;
      }

      assertEquals(500, count);

      Iterator lruIt = queue.iterateLRUQueue();
      count = 0;
      while (lruIt.hasNext())
      {
         NodeEntry ne = (NodeEntry) lruIt.next();
         int nodeIndex = Integer.parseInt((String) ne.getFqn().get(3));

         // the last 200 in the list should be the visisted LRU ones.
         if (count >= 300 && count < 400)
         {
            int expectedNodeIndex = count - 300;
            assertEquals(expectedNodeIndex, nodeIndex);
         }
         else if (count >= 400 && count < 500)
         {
            int expectedNodeIndex = count - 100;
            assertEquals(expectedNodeIndex, nodeIndex);
         }
         else if (count < 200)
         {
            int expectedNodeIndex = count + 100;
            assertEquals(expectedNodeIndex, nodeIndex);
         }
         else if (count >= 200 && count < 300)
         {
            int expectedNodeIndex = count + 200;
            assertEquals(expectedNodeIndex, nodeIndex);
         }

         count++;
      }

      assertEquals(500, count);

      NodeEntry ne = queue.getFirstMaxAgeNodeEntry();
      queue.removeNodeEntry(ne);
      assertEquals(499, queue.getNumberOfNodes());

      assertFalse(queue.containsNodeEntry(ne));

      ne = queue.getFirstLRUNodeEntry();
      queue.removeNodeEntry(ne);
      assertEquals(498, queue.getNumberOfNodes());

      assertFalse(queue.containsNodeEntry(ne));


   }

   public void testGetFirstLRUNodeEntry() throws Exception
   {
      for (int i = 0; i < 100; i++)
      {
         NodeEntry ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         queue.addNodeEntry(ne);
      }

      for (int i = 0; i < 100; i++)
      {
         // this should move all the even numbered NodeEntries to the bottom of the lruQueue.
         // maxAgeQueue should be unaffected.
         if (i % 2 == 0)
         {
            Fqn fqn = Fqn.fromString("/a/b/c/" + Integer.toString(i));
            queue.reorderByLRU(fqn);
         }
      }

      assertEquals(100, queue.getNumberOfNodes());

      NodeEntry ne;
      int count = 0;
      while ((ne = queue.getFirstLRUNodeEntry()) != null)
      {
         int nodeIndex = Integer.parseInt((String) ne.getFqn().get(3));

         if (count < 50)
         {
            // the top 50 should be all odds in the lruQueue/
            assertTrue(nodeIndex % 2 != 0);
         }
         else
         {
            // the bottom fifty should all be even #'s (and 0)
            assertTrue(nodeIndex % 2 == 0);
         }
         queue.removeNodeEntry(ne);
         count++;
      }
      assertEquals(0, queue.getNumberOfNodes());
   }

   public void testGetFirstMaxAgeNodeEntriy() throws Exception
   {
      for (int i = 0; i < 100; i++)
      {
         NodeEntry ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         queue.addNodeEntry(ne);
      }

      for (int i = 0; i < 100; i++)
      {
         // this should move all the even numbered NodeEntries to the bottom of the lruQueue.
         // maxAgeQueue should be unaffected.
         if (i % 2 == 0)
         {
            Fqn fqn = Fqn.fromString("/a/b/c/" + Integer.toString(i));
            queue.reorderByLRU(fqn);
         }
      }

      assertEquals(100, queue.getNumberOfNodes());

      NodeEntry ne;
      int count = 0;
      while ((ne = queue.getFirstMaxAgeNodeEntry()) != null)
      {
         int nodeIndex = Integer.parseInt((String) ne.getFqn().get(3));
         assertEquals(count, nodeIndex);
         queue.removeNodeEntry(ne);
         count++;
      }

      assertEquals(0, queue.getNumberOfNodes());
   }

   public void testNumElements() throws Exception
   {
      LRUQueue queue = new LRUQueue();

      NodeEntry ne = new NodeEntry("/a/b/c");
      ne.setNumberOfElements(50);
      queue.addNodeEntry(ne);

      assertEquals(50, queue.getNumberOfElements());
      assertEquals(1, queue.getNumberOfNodes());

      queue.removeNodeEntry(ne);
      assertEquals(0, queue.getNumberOfElements());

      for(int i = 0; i < 10; i++)
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

      queue.removeNodeEntry(ne);

      assertEquals(45, queue.getNumberOfElements());
      assertEquals(9, queue.getNumberOfNodes());
      for(int i = 1; i < 10; i++)
      {
         ne = queue.getNodeEntry("/a/b/c/" + Integer.toString(i));
         assertEquals(i, ne.getNumberOfElements());
         queue.removeNodeEntry(ne);
      }

      assertEquals(0, queue.getNumberOfNodes());
      assertEquals(0, queue.getNumberOfElements());

      assertNull(queue.getNodeEntry("/a/b/c/0"));
      assertNull(queue.getFirstNodeEntry());
   }

}
