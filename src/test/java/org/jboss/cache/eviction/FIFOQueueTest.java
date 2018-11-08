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

import org.jboss.cache.config.EvictionConfig;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests for FIFOQueue.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7332 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.FIFOQueueTest")
public class FIFOQueueTest
{
   private static final int CAPACITY = EvictionConfig.EVENT_QUEUE_SIZE_DEFAULT/4;

   private FIFOQueue queue;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      queue = new FIFOQueue();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      queue = null;
   }
   
   public void testQueue() throws Exception
   {
      for (int i = 0; i < 50000; i++)
      {
         NodeEntry ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         queue.addNodeEntry(ne);
      }

      assertEquals(queue.getFirstNodeEntry().getFqn().toString(), "/a/b/c/0");

      assertEquals(50000, queue.getNumberOfNodes());
      assertTrue(queue.containsNodeEntry(new NodeEntry("/a/b/c/250")));

      NodeEntry ne27500 = queue.getNodeEntry("/a/b/c/27500");
      assertEquals("/a/b/c/27500", ne27500.getFqn().toString());

      // now make sure the ordering is correct.
      int k = 0;
      NodeEntry ne;
      while ((ne = queue.getFirstNodeEntry()) != null)
      {
         assertEquals("/a/b/c/" + Integer.toString(k), ne.getFqn().toString());
         queue.removeNodeEntry(ne);
         k++;
         if (k == 25000)
         {
            break;
         }
      }

      assertEquals(queue.getFirstNodeEntry().getFqn().toString(), "/a/b/c/25000");

      assertEquals(25000, queue.getNumberOfNodes());
      k = 25000;
      while ((ne = queue.getFirstNodeEntry()) != null)
      {
         assertEquals(ne.getFqn().toString(), "/a/b/c/" + Integer.toString(k));
         queue.removeNodeEntry(ne);
         k++;
      }

      assertEquals(0, queue.getNumberOfNodes());

      assertFalse(queue.containsNodeEntry(new NodeEntry("/a/b/c/27500")));

      assertNull(queue.getNodeEntry("/a/b/c/27500"));
   }

   public void testGetFirstNodeEntry() throws Exception
   {
      for (int i = 0; i < 50000; i++)
      {
         NodeEntry ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         queue.addNodeEntry(ne);
      }

      NodeEntry ne;
      int count = 0;
      while ((ne = queue.getFirstNodeEntry()) != null)
      {
         assertEquals("/a/b/c/" + Integer.toString(count), ne.getFqn().toString());
         queue.removeNodeEntry(ne);
         count++;
      }
   }

   public void testLargeAddAndRemoval() throws Exception
   {
      for (int i = 0; i < CAPACITY; i++)
      {
         queue.addNodeEntry(new NodeEntry("/test/" + Integer.toString(i)));
      }
      assertEquals(CAPACITY, queue.getNumberOfNodes());

      for (int i = CAPACITY - 1; i >= 0; i--)
      {
         // pop it backwards for worse case scenario if O(n) = n
         queue.removeNodeEntry(new NodeEntry("/test/" + Integer.toString(i)));
      }

      assertEquals(0, queue.getNumberOfNodes());

      for (int i = 0; i < CAPACITY; i++)
      {
         queue.addNodeEntry(new NodeEntry("/test/" + Integer.toString(i)));
      }
      assertEquals(CAPACITY, queue.getNumberOfNodes());
      NodeEntry ne;

      while ((ne = queue.getFirstNodeEntry()) != null)
      {
         queue.removeNodeEntry(ne);
      }

      assertEquals(0, queue.getNumberOfNodes());
   }

   public void testNumElements() throws Exception
   {
      FIFOQueue queue = new FIFOQueue();

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

      queue.removeNodeEntry(ne);

      assertEquals(45, queue.getNumberOfElements());
      assertEquals(9, queue.getNumberOfNodes());
      for (int i = 1; i < 10; i++)
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
