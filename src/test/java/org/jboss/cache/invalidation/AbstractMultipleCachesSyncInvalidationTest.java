package org.jboss.cache.invalidation;

import org.jboss.cache.AbstractMultipleCachesTest;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Node;
import org.jboss.cache.Fqn;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.fail;

import javax.transaction.TransactionManager;

/**
 * @author Mircea.Markus@jboss.com
 */
public abstract class AbstractMultipleCachesSyncInvalidationTest extends AbstractMultipleCachesTest
{
   protected CacheSPI<Object, Object> cache1;
   protected CacheSPI<Object, Object> cache2;

   public void nodeRemovalTest() throws Exception
   {
      Node<Object, Object> root1 = cache1.getRoot();
      Node<Object, Object> root2 = cache2.getRoot();

      // this fqn is relative, but since it is from the root it may as well be absolute
      Fqn fqn = Fqn.fromString("/test/fqn");
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put(fqn, "key", "value");
      assertEquals("value", cache1.get(fqn, "key"));
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache2.put(fqn, "key", "value");
      assertEquals("value", cache2.get(fqn, "key"));

      assertEquals(true, cache1.removeNode(fqn));
      assertFalse(root1.hasChild(fqn));
      Node<Object, Object> remoteNode = root2.getChild(fqn);
      CacheLoaderInvalidationTest.checkRemoteNodeIsRemoved(remoteNode);
      assertEquals(false, cache1.removeNode(fqn));

      Fqn child = Fqn.fromString("/test/fqn/child");
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put(child, "key", "value");
      assertEquals("value", cache1.get(child, "key"));
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache2.put(child, "key", "value");
      assertEquals("value", cache2.get(child, "key"));

      assertEquals(true, cache1.removeNode(fqn));
      assertFalse(root1.hasChild(fqn));
      remoteNode = root2.getChild(fqn);
      CacheLoaderInvalidationTest.checkRemoteNodeIsRemoved(remoteNode);
      assertEquals(false, cache1.removeNode(fqn));
   }

   public void nodeResurrectionTest() throws Exception
   {
      // this fqn is relative, but since it is from the root it may as well be absolute
      Fqn fqn = Fqn.fromString("/test/fqn1");
      cache1.put(fqn, "key", "value");
      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals(null, cache2.get(fqn, "key"));
      // Change the value in order to increment the version if Optimistic is used
      cache1.put(fqn, "key", "newValue");
      assertEquals("newValue", cache1.get(fqn, "key"));
      assertEquals(null, cache2.get(fqn, "key"));

      assertEquals(true, cache1.removeNode(fqn));
      assertEquals(null, cache1.get(fqn, "key"));
      assertEquals(null, cache2.get(fqn, "key"));

      // Restore locally
      cache1.put(fqn, "key", "value");
      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals(null, cache2.get(fqn, "key"));

      // Repeat, but now restore the node on the remote cache
      fqn = Fqn.fromString("/test/fqn2");
      cache1.put(fqn, "key", "value");
      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals(null, cache2.get(fqn, "key"));
      // Change the value in order to increment the version if Optimistic is used
      cache1.put(fqn, "key", "newValue");
      assertEquals("newValue", cache1.get(fqn, "key"));
      assertEquals(null, cache2.get(fqn, "key"));

      assertEquals(true, cache1.removeNode(fqn));
      assertEquals(null, cache1.get(fqn, "key"));
      assertEquals(null, cache2.get(fqn, "key"));

      // Restore on remote cache
      cache2.put(fqn, "key", "value");
      assertEquals("value", cache2.get(fqn, "key"));
      assertEquals(null, cache1.get(fqn, "key"));
   }

   /**
    * Here we model a scenario where a parent node represents
    * a structural node, and then child nodes represent different
    * data elements.
    * <p/>
    * Such data structures are set up on both caches, and then the parent node
    * is removed (globally) and re-added (locally) on one cache.  This
    * represents an attempt to clear the region -- removing a node and
    * re-adding is one of the only ways to do this.
    * <p/>
    * On the second cache, the fact that the structural node is missing is
    * detected, and an attempt is made to re-add it locally.
    *
    * @param optimistic should the cache be configured for optimistic locking
    * @throws Exception
    */
   private void nodeResurrectionTest2() throws Exception
   {
      Node root1 = cache1.getRoot();
      Node root2 = cache2.getRoot();

      // this fqn is relative, but since it is from the root it may as well be absolute
      Fqn fqn = Fqn.fromString("/test/fqn");
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      root1.addChild(fqn);
      assertEquals(true, root1.hasChild(fqn));
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      root1.addChild(fqn);
      assertEquals(true, root1.hasChild(fqn));

      Fqn child = Fqn.fromRelativeElements(fqn, "child");
      cache1.putForExternalRead(child, "key", "value");
      cache2.putForExternalRead(child, "key", "value");
      assertEquals("value", cache1.get(child, "key"));
      assertEquals("value", cache2.get(child, "key"));

      assertEquals(true, cache1.removeNode(fqn));
      assertFalse(root1.hasChild(fqn));

      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      root1.addChild(fqn);
      assertEquals(true, root1.hasChild(fqn));

      Node remoteNode = root2.getChild(fqn);
      CacheLoaderInvalidationTest.checkRemoteNodeIsRemoved(remoteNode);
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      root2.addChild(fqn);
      assertEquals(true, root2.hasChild(fqn));
   }

   public void deleteNonExistentTest() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");

      assertNull("Should be null", cache1.getNode(fqn));
      assertNull("Should be null", cache2.getNode(fqn));

      cache1.putForExternalRead(fqn, "key", "value");

      assertEquals("value", cache1.getNode(fqn).get("key"));
      assertNull("Should be null", cache2.getNode(fqn));

      // OK, here's the real test
      TransactionManager tm = cache2.getTransactionManager();
      tm.begin();
      try
      {
         // Remove a node that doesn't exist in cache2
         cache2.removeNode(fqn);
         tm.commit();
      }
      catch (Exception e)
      {
         String msg = "Unable to remove non-existent node " + fqn;
         fail(msg + " -- " + e);
      }
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache1.getNode(fqn), "Should have been invalidated");
      assertNull("Should be null", cache2.getNode(fqn));
   }


}
