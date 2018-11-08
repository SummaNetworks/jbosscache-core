/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options.cachemodelocal;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.*;
import org.testng.annotations.Test;
import static org.testng.AssertJUnit.assertEquals;

@Test(groups = {"functional", "jgroups"}, testName = "options.cachemodelocal.SyncInvalidationPessLocksTest")
public class SyncInvalidationPessLocksTest extends CacheModeLocalTestBase
{

   public SyncInvalidationPessLocksTest()
    {
        cacheMode = Configuration.CacheMode.INVALIDATION_SYNC;
        nodeLockingScheme = "PESSIMISTIC";
        isInvalidation = true;
    }

   public void testMoveInvalidations() throws Exception
   {

      Node rootNode = cache1.getRoot();

      Node<Object, Object> nodeA = rootNode.addChild(Fqn.fromString("/a"));
      Node<Object, Object> nodeB = nodeA.addChild(Fqn.fromString("/b"));

      nodeA.put("key", "valueA");
      nodeB.put("key", "valueB");

      assertEquals("valueA", cache1.getRoot().getChild(Fqn.fromString("/a")).get("key"));
      assertEquals("valueB", cache1.getRoot().getChild(Fqn.fromString("/a")).getChild(Fqn.fromString("/b")).get("key"));

      assertInvalidated(cache2, Fqn.fromString("/a"), "Should be invalidated");
      assertInvalidated(cache2, Fqn.fromRelativeElements(Fqn.fromString("/a"), Fqn.fromString("/b").getLastElement()), "Should be invalidated");

      // now move...
      cache1.move(nodeB.getFqn(), Fqn.ROOT);

      assertEquals("valueA", cache1.getRoot().getChild(Fqn.fromString("/a")).get("key"));
      assertEquals("valueB", cache1.getRoot().getChild(Fqn.fromString("/b")).get("key"));

      assertInvalidated(cache2, Fqn.fromString("/a"), "Should be invalidated");
      assertInvalidated(cache2, Fqn.fromString("/b"), "Should be invalidated");

      // now make sure a node exists on cache 2
      cache2.getRoot().addChild(Fqn.fromString("/a")).put("k2", "v2");

      // te invalidation will happen in afterCompletion, hence no exception!
      try
      {
         cache1.move(Fqn.fromString("/b"), Fqn.fromString("/a"));// should throw an NPE
      }
      catch (Exception expected)
      {
      }
   }

   private void assertInvalidated(Cache cache, Fqn fqn, String msg)
   {
      assert cache.getRoot().getChild(fqn) == null : msg;
      NodeSPI n = ((CacheSPI) cache).peek(fqn, true, true);
   }



}
