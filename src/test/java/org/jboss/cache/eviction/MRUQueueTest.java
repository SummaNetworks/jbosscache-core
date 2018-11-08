/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import org.jboss.cache.Fqn;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Unit tests for MRUQueue.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.MRUQueueTest")
public class MRUQueueTest
{
   private MRUQueue queue;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      queue = new MRUQueue();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      queue.clear();
   }

   public void testQueue() throws Exception
   {
      for (int i = 0; i < 100; i++)
      {
         NodeEntry ne = new NodeEntry("/a/b/c/" + Integer.toString(i));
         ne.setModifiedTimeStamp(0);
         queue.addNodeEntry(ne);
      }

      assertEquals(queue.nodeMap.size(), queue.list.size());

      for (int i = 0; i < 100; i++)
      {
         if (i % 2 == 0)
         {
            Fqn fqn = Fqn.fromString("/a/b/c/" + Integer.toString(i));
            NodeEntry ne = queue.getNodeEntry(fqn);
            ne.setModifiedTimeStamp(System.currentTimeMillis());
            queue.moveToTopOfStack(fqn);
         }
      }

      assertEquals(queue.nodeMap.size(), queue.list.size());

      NodeEntry ne;
      int count = 0;
      while ((ne = queue.getFirstNodeEntry()) != null)
      {
         if (count < 50)
         {
            assertTrue(ne.getModifiedTimeStamp() > 0);
            assertEquals(100 - count, queue.getNumberOfNodes());
         }
         else
         {
            assertEquals(0, ne.getModifiedTimeStamp());
         }
         queue.removeNodeEntry(ne);
         count++;
      }
      assertEquals(queue.nodeMap.size(), queue.list.size());

   }

   public void testNumElements() throws Exception
   {
      MRUQueue queue = new MRUQueue();

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
