/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7289 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.EvictionQueueListTest")
public class EvictionQueueListTest
{
   EvictionQueueList list;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      list = new EvictionQueueList();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      list = null;
   }
   
   public void testAddToBottom() throws Exception
   {
      for (int i = 0; i < 100; i++)
      {
         NodeEntry ne = new NodeEntry("/" + Integer.toString(i));
         EvictionListEntry listEntry = new EvictionListEntry(ne);
         list.addToBottom(listEntry);
      }

      assertEquals(100, list.size());
      for (int i = 0; i < 100; i++)
      {
         EvictionListEntry entry = list.getFirst();
         assertEquals("/" + Integer.toString(i), entry.node.getFqn().toString());
         list.remove(entry);
      }
   }

   public void testAddToTop() throws Exception
   {
      for (int i = 0; i < 100; i++)
      {
         NodeEntry ne = new NodeEntry("/" + Integer.toString(i));
         EvictionListEntry listEntry = new EvictionListEntry(ne);
         list.addToTop(listEntry);
      }

      assertEquals(100, list.size());
      for (int i = 99; i >= 0; i--)
      {
         EvictionListEntry entry = list.getFirst();
         assertEquals("/" + Integer.toString(i), entry.node.getFqn().toString());
         list.remove(entry);
      }
   }

   public void testRemoveAndClear() throws Exception
   {
      EvictionListEntry listEntry1 = new EvictionListEntry(new NodeEntry("/0"));
      list.addToBottom(listEntry1);
      assertEquals(list.getFirst(), list.getLast());

      EvictionListEntry listEntry2 = new EvictionListEntry(new NodeEntry("/1"));
      list.addToBottom(listEntry2);
      EvictionListEntry listEntry3 = new EvictionListEntry(new NodeEntry("/2"));
      list.addToBottom(listEntry3);
      EvictionListEntry listEntry4 = new EvictionListEntry(new NodeEntry("/3"));
      list.addToBottom(listEntry4);
      EvictionListEntry listEntry5 = new EvictionListEntry(new NodeEntry("/4"));
      list.addToBottom(listEntry5);
      EvictionListEntry listEntry6 = new EvictionListEntry(new NodeEntry("/5"));
      list.addToBottom(listEntry6);

      assertEquals(6, list.size());

      assertEquals(listEntry1, list.getFirst());
      assertEquals(listEntry6, list.getLast());

      // test removal from the top.
      list.remove(list.getFirst());
      assertEquals(5, list.size());
      assertEquals(listEntry2, list.getFirst());

      // test removal from the bottom.
      list.remove(list.getLast());
      assertEquals(4, list.size());
      assertEquals(listEntry5, list.getLast());

      // test removal from the middle
      list.remove(listEntry3);
      assertEquals(3, list.size());
      assertEquals(listEntry2, list.getFirst());
      assertEquals(listEntry5, list.getLast());


      Iterator it = list.iterator();
      int count = 0;
      while (it.hasNext())
      {
         NodeEntry e = (NodeEntry) it.next();
         if (count == 0)
         {
            assertEquals(listEntry2.node, e);
         }
         else if (count == 1)
         {
            assertEquals(listEntry4.node, e);
         }
         else if (count == 2)
         {
            assertEquals(listEntry5.node, e);
         }
         count++;
      }

      assertEquals(3, count);

      // test clear.
      list.clear();
      assertEquals(0, list.size());
      boolean caught = false;
      try
      {
         list.getFirst();
      }
      catch (NoSuchElementException e)
      {
         caught = true;
      }
      assertTrue(caught);

      caught = false;
      try
      {
         list.getLast();
      }
      catch (NoSuchElementException e)
      {
         caught = true;
      }
      assertTrue(caught);

   }

   public void testIterator() throws Exception
   {
      for (int i = 0; i < 100; i++)
      {
         NodeEntry ne = new NodeEntry("/" + Integer.toString(i));
         EvictionListEntry listEntry = new EvictionListEntry(ne);
         list.addToBottom(listEntry);
      }

      Iterator it = list.iterator();
      int count = 0;
      while (it.hasNext())
      {
         NodeEntry e = (NodeEntry) it.next();
         assertEquals("/" + Integer.toString(count), e.getFqn().toString());
         it.remove();
         count++;
      }

      assertEquals(0, list.size());

      it = list.iterator();
      assertFalse(it.hasNext());

      for (int i = 0; i < 100; i++)
      {
         NodeEntry ne = new NodeEntry("/" + Integer.toString(i));
         EvictionListEntry listEntry = new EvictionListEntry(ne);
         list.addToBottom(listEntry);
      }

      it = list.iterator();
      boolean caught = false;
      try
      {
         while (it.hasNext())
         {
            list.addToBottom(new EvictionListEntry(new NodeEntry("/a/b/c")));
         }
      }
      catch (ConcurrentModificationException e)
      {
         caught = true;
      }
      assertTrue(caught);
   }

   public void testToArray() throws Exception
   {
      for (int i = 0; i < 100; i++)
      {
         NodeEntry ne = new NodeEntry("/" + Integer.toString(i));
         EvictionListEntry listEntry = new EvictionListEntry(ne);
         list.addToTop(listEntry);
      }

      EvictionListEntry entries[] = list.toArray();
      assertEquals(100, entries.length);

      for (int i = 0, j = 99; i < 100; i++, j--)
      {
         assertEquals("/" + Integer.toString(j), entries[i].node.getFqn().toString());
      }
   }

   public void testFromArray() throws Exception
   {
      EvictionListEntry entries[] = new EvictionListEntry[100];
      for (int i = 0; i < 100; i++)
      {
         entries[i] = new EvictionListEntry(new NodeEntry("/" + Integer.toString(i)));
      }

      assertEquals(0, list.size());

      list.fromArray(entries);

      assertEquals(100, list.size());

      for (int i = 0; i < 100; i++)
      {
         assertEquals(entries[i], list.getFirst());
         list.remove(list.getFirst());
      }

      assertEquals(0, list.size());
   }
}
