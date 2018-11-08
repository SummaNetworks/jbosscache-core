/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.api;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

@Test(groups = {"functional", "jgroups", "pessimistic"}, testName = "api.NodeReplicatedMoveTest")
public class NodeReplicatedMoveTest extends AbstractMultipleCachesTest
{

   protected static final Fqn A = Fqn.fromString("/a"), B = Fqn.fromString("/b"), C = Fqn.fromString("/c"), D = Fqn.fromString("/d"), E = Fqn.fromString("/e");
   protected static final Object k = "key", vA = "valueA", vB = "valueB", vC = "valueC", vD = "valueD", vE = "valueE";

   protected NodeLockingScheme nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;
   private CacheSPI<Object, Object> cache1;
   private TransactionManager tm;
   private CacheSPI<Object, Object> cache2;

   protected void createCaches()
   {
      // start a single cache instance
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());
      cache1.getConfiguration().setSyncCommitPhase(true);
      cache1.getConfiguration().setSyncRollbackPhase(true);
      cache1.getConfiguration().setNodeLockingScheme(nodeLockingScheme);
      configure(cache1.getConfiguration());
      cache1.start();
      tm = cache1.getTransactionManager();

      //  start second instance
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());
      cache2.getConfiguration().setSyncCommitPhase(true);
      cache2.getConfiguration().setSyncRollbackPhase(true);
      cache2.getConfiguration().setNodeLockingScheme(nodeLockingScheme);
      configure(cache2.getConfiguration());
      cache2.start();
      registerCaches(cache1, cache2);
   }


   protected void configure(Configuration c)
   {
      // to be overridden
   }

   public void testReplicatability()
   {
      Node<Object, Object> rootNode = cache1.getRoot();
      
      Node<Object, Object> nodeA = rootNode.addChild(A);
      Node<Object, Object> nodeB = nodeA.addChild(B);

      nodeA.put(k, vA);
      nodeB.put(k, vB);

      assertEquals(vA, cache1.getRoot().getChild(A).get(k));
      assertEquals(vB, cache1.getRoot().getChild(A).getChild(B).get(k));

      assertEquals(vA, cache2.getRoot().getChild(A).get(k));
      assertEquals(vB, cache2.getRoot().getChild(A).getChild(B).get(k));

      // now move...
      cache1.move(nodeB.getFqn(), Fqn.ROOT);

      assertEquals(vA, cache1.getRoot().getChild(A).get(k));
      assertEquals(vB, cache1.getRoot().getChild(B).get(k));

      assertEquals(vA, cache2.getRoot().getChild(A).get(k));
      assertEquals(vB, cache2.getRoot().getChild(B).get(k));
   }

   public void testReplTxCommit() throws Exception
   {
      Node<Object, Object> rootNode = cache1.getRoot();
      Fqn A_B = Fqn.fromRelativeFqn(A, B);
      Node<Object, Object> nodeA = rootNode.addChild(A);
      Node<Object, Object> nodeB = nodeA.addChild(B);

      nodeA.put(k, vA);
      nodeB.put(k, vB);

      assertEquals(vA, cache1.getRoot().getChild(A).get(k));
      assertEquals(vB, cache1.getRoot().getChild(A).getChild(B).get(k));

      assertEquals(vA, cache2.getRoot().getChild(A).get(k));
      assertEquals(vB, cache2.getRoot().getChild(A).getChild(B).get(k));

      // now move...
      tm.begin();
      cache1.move(nodeB.getFqn(), Fqn.ROOT);

      assertEquals(vA, cache1.get(A, k));
      assertNull(cache1.get(A_B, k));
      assertEquals(vB, cache1.get(B, k));
      tm.commit();

      assertEquals(vA, cache1.getRoot().getChild(A).get(k));
      assertEquals(vB, cache1.getRoot().getChild(B).get(k));
      assertEquals(vA, cache2.getRoot().getChild(A).get(k));
      assertEquals(vB, cache2.getRoot().getChild(B).get(k));

   }

   public void testReplTxRollback() throws Exception
   {

      Node<Object, Object> rootNode = cache1.getRoot();
      Node<Object, Object> nodeA = rootNode.addChild(A);
      Node<Object, Object> nodeB = nodeA.addChild(B);

      nodeA.put(k, vA);
      nodeB.put(k, vB);

      assertEquals(vA, cache1.getRoot().getChild(A).get(k));
      assertEquals(vB, cache1.getRoot().getChild(A).getChild(B).get(k));
      assertEquals(vA, cache2.getRoot().getChild(A).get(k));
      assertEquals(vB, cache2.getRoot().getChild(A).getChild(B).get(k));

      // now move...
      tm.begin();
      cache1.move(nodeB.getFqn(), Fqn.ROOT);

      assertEquals(vA, cache1.get(A, k));
      assertEquals(vB, cache1.get(B, k));

      tm.rollback();

      assertEquals(vA, cache1.getRoot().getChild(A).get(k));
      assertEquals(vB, cache1.getRoot().getChild(A).getChild(B).get(k));
      assertEquals(vA, cache2.getRoot().getChild(A).get(k));
      assertEquals(vB, cache2.getRoot().getChild(A).getChild(B).get(k));
   }

   protected boolean isOptimistic()
   {
      return nodeLockingScheme == NodeLockingScheme.OPTIMISTIC;
   }
}
