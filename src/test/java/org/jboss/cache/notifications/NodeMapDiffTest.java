package org.jboss.cache.notifications;

import org.jboss.cache.util.Util;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests the diffs between maps.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional"}, testName = "notifications.NodeMapDiffTest")
public class NodeMapDiffTest
{
   public void testDataAdded()
   {
      Util.MapModifications expected = new Util.MapModifications();
      expected.addedEntries.put("key", "value");
      expected.addedEntries.put("key1", "value1");

      Map<Object, Object> pre = new HashMap<Object, Object>();
      pre.put("oldKey", "oldValue");

      Map<Object, Object> post = new HashMap<Object, Object>();
      post.putAll(pre);
      post.put("key", "value");
      post.put("key1", "value1");

      assertEquals(expected, Util.diffNodeData(pre, post));
   }

   public void testDataRemoved()
   {
      Util.MapModifications expected = new Util.MapModifications();
      expected.removedEntries.put("key", "value");
      expected.removedEntries.put("key1", "value1");

      Map<Object, Object> post = new HashMap<Object, Object>();
      post.put("oldKey", "oldValue");

      Map<Object, Object> pre = new HashMap<Object, Object>();
      pre.putAll(post);
      pre.put("key", "value");
      pre.put("key1", "value1");

      assertEquals(expected, Util.diffNodeData(pre, post));
   }

   public void testDataChanged()
   {
      Util.MapModifications expected = new Util.MapModifications();
      expected.modifiedEntries.put("key", "value");
      expected.modifiedEntries.put("key1", "value1");

      Map<Object, Object> pre = new HashMap<Object, Object>();
      pre.put("oldKey", "oldValue");
      pre.put("key", "valueOLD");
      pre.put("key1", "value1OLD");

      Map<Object, Object> post = new HashMap<Object, Object>();
      post.putAll(pre);
      post.put("key", "value");
      post.put("key1", "value1");

      assertEquals(expected, Util.diffNodeData(pre, post));
   }

   public void testNullMaps()
   {
      try
      {
         Util.diffNodeData(null, null);
         fail("Expected NPE");
      }
      catch (NullPointerException npe)
      {
         // expected
      }

      try
      {
         Util.diffNodeData(new HashMap<Object, Object>(), null);
         fail("Expected NPE");
      }
      catch (NullPointerException npe)
      {
         // expected
      }

      try
      {
         Util.diffNodeData(null, new HashMap<Object, Object>());
         fail("Expected NPE");
      }
      catch (NullPointerException npe)
      {
         // expected
      }

   }

   public void testEmptyMaps()
   {
      Util.MapModifications expected = new Util.MapModifications();

      Map<Object, Object> pre = new HashMap<Object, Object>();
      Map<Object, Object> post = new HashMap<Object, Object>();

      assertEquals(expected, Util.diffNodeData(pre, post));
   }

   public void testNoChange()
   {
      Util.MapModifications expected = new Util.MapModifications();

      Map<Object, Object> pre = new HashMap<Object, Object>();
      pre.put("a", "b");
      pre.put("c", "d");
      pre.put("e", "f");

      Map<Object, Object> post = new HashMap<Object, Object>();
      post.put("a", "b");
      post.put("c", "d");
      post.put("e", "f");

      assertEquals(expected, Util.diffNodeData(pre, post));
   }

   public void testMultipleChanges()
   {
      Util.MapModifications expected = new Util.MapModifications();
      expected.modifiedEntries.put("key", "value");
      expected.modifiedEntries.put("key1", "value1");
      expected.addedEntries.put("key2", "value2");
      expected.addedEntries.put("key3", "value3");
      expected.removedEntries.put("key4", "value4");
      expected.removedEntries.put("key5", "value5");

      Map<Object, Object> pre = new HashMap<Object, Object>();
      pre.put("oldKey", "oldValue");
      pre.put("key", "valueOLD");
      pre.put("key1", "value1OLD");
      pre.put("key4", "value4");
      pre.put("key5", "value5");

      Map<Object, Object> post = new HashMap<Object, Object>();
      post.put("oldKey", "oldValue");
      post.put("key", "value");
      post.put("key1", "value1");
      post.put("key2", "value2");
      post.put("key3", "value3");

      assertEquals(expected, Util.diffNodeData(pre, post));
   }

}
