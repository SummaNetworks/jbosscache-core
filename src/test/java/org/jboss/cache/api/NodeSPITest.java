package org.jboss.cache.api;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import java.util.Set;

/**
 * Tests NodeSPI specific APIs.
 */
@Test(groups = {"functional", "pessimistic"}, sequential = true, testName = "api.NodeSPITest")
public class NodeSPITest extends AbstractSingleCacheTest
{
   private NodeSPI<Object, Object> root;


   public CacheSPI createCache()
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.start();
      root = cache.getRoot();
      return cache;
   }

   public void testDeepOperations() throws Exception
   {
      Fqn A = Fqn.fromString("/a");
      Fqn B = Fqn.fromString("/b");
      Fqn A_B = Fqn.fromString("/a/b");

      NodeSPI nodeA, nodeB;

      cache.put(A, "k", "v");
      cache.put(A_B, "k", "v");

      nodeA = cache.getRoot().getChildDirect(A);// should work
      nodeB = cache.getRoot().getChildDirect(A_B);// should work

      assertEquals(A_B, nodeB.getFqn());

      nodeB = nodeA.getChildDirect(B);// should work
      assertEquals(A_B, nodeB.getFqn());
      assertEquals(true, cache.getRoot().removeChildDirect(A_B));// should work
      assertEquals(false, cache.getRoot().removeChildDirect(A_B));// should work

      cache.put(A_B, "k", "v");
      assertEquals(true, nodeA.removeChildDirect(B));// should work
      assertEquals(false, nodeA.removeChildDirect(B));// should work
      assertEquals(true, cache.getRoot().removeChildDirect(A.getLastElement()));
      assertEquals(false, cache.getRoot().removeChildDirect(A.getLastElement()));

      try
      {
         cache.getRoot().addChildDirect(A_B);// should fail
         fail("Should have failed");
      }
      catch (UnsupportedOperationException e)
      {
         // expected
      }
      nodeA = cache.getRoot().addChildDirect(A);// should work
      nodeA.addChildDirect(B);// should work
   }

   public void testChildrenImmutabilityAndDefensiveCopy()
   {
      // put some stuff in the root node
      String childName = "childName";
      String newChild = "newChild";
      root.addChild(Fqn.fromElements(childName));
      Set childrenDirect = root.getChildrenDirect();

      try
      {
         childrenDirect.clear();
         fail("getChildrenDirect() should return an unmodifiable collection object");
      }
      catch (UnsupportedOperationException uoe)
      {
         // good; should be immutable
      }

      // now test defensive copy
      root.addChild(Fqn.fromElements(newChild));

      assertTrue("root.addChild() should have succeeded", root.getChildrenNamesDirect().contains(newChild));
      assertTrue("getChildrenDirect() should have made a defensive copy of the data collection object", !childrenDirect.contains(newChild));
   }

   public void testNullCollections()
   {
      // nothing in root, make sure we see no nulls.
      assertNotNull("Should not be null", root.getDataDirect());
      assertTrue("Should be empty", root.getDataDirect().isEmpty());

      assertNotNull("Should not be null", root.getKeysDirect());
      assertTrue("Should be empty", root.getKeysDirect().isEmpty());

      assertNotNull("Should not be null", root.getChildrenDirect());
      assertTrue("Should be empty", root.getChildrenDirect().isEmpty());

      assertNotNull("Should not be null", root.getChildrenNamesDirect());
      assertTrue("Should be empty", root.getChildrenNamesDirect().isEmpty());
   }


}
