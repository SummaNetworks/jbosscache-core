package org.jboss.cache.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Excercises and tests the new move() api
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional", "pessimistic"}, testName = "api.NodeMoveAPITest")
public class NodeMoveAPITest extends AbstractSingleCacheTest
{
   protected final Log log = LogFactory.getLog(getClass());

   protected static final Fqn A = Fqn.fromString("/a"), B = Fqn.fromString("/b"), C = Fqn.fromString("/c"), D = Fqn.fromString("/d"), E = Fqn.fromString("/e");
   protected static final Object k = "key", vA = "valueA", vB = "valueB", vC = "valueC", vD = "valueD", vE = "valueE";

   protected NodeLockingScheme nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;

   private TransactionManager tm;

   protected CacheSPI createCache()
   {
      // start a single cache instance
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache("configs/local-tx.xml", false, getClass());
      cache.getConfiguration().setNodeLockingScheme(nodeLockingScheme);
      cache.getConfiguration().setFetchInMemoryState(false);
      cache.getConfiguration().setEvictionConfig(null);
      configure(cache.getConfiguration());
      cache.start();
      tm = cache.getTransactionManager();
      return cache;
   }

   protected void configure(Configuration c)
   {
      // to be overridden
   }

   public void testBasicMove()
   {
      Node<Object, Object> rootNode = cache.getRoot();

      Node<Object, Object> nodeA = rootNode.addChild(A);
      nodeA.put(k, vA);
      Node<Object, Object> nodeB = rootNode.addChild(B);
      nodeB.put(k, vB);
      Node<Object, Object> nodeC = nodeA.addChild(C);
      nodeC.put(k, vC);
      /*
         /a/c
         /b
       */

      assertTrue(rootNode.hasChild(A));
      assertTrue(rootNode.hasChild(B));
      assertFalse(rootNode.hasChild(C));
      assertTrue(nodeA.hasChild(C));

      // test data
      assertEquals("" + nodeA, vA, nodeA.get(k));
      assertEquals(vB, nodeB.get(k));
      assertEquals(vC, nodeC.get(k));

      // parentage
      assertEquals(nodeA, nodeC.getParent());

      log.info("BEFORE MOVE " + cache);
      // move
      cache.move(nodeC.getFqn(), nodeB.getFqn());

      // re-fetch nodeC
      nodeC = cache.getNode(Fqn.fromRelativeFqn(nodeB.getFqn(), C));

      log.info("POST MOVE " + cache);
      log.info("HC " + nodeC + " " + System.identityHashCode(nodeC));
      Node x = cache.getRoot().getChild(Fqn.fromString("b/c"));
      log.info("HC " + x + " " + System.identityHashCode(x));
      /*
         /a
         /b/c
      */
      assertEquals("NODE C " + nodeC, "/b/c", nodeC.getFqn().toString());

      assertTrue(rootNode.hasChild(A));
      assertTrue(rootNode.hasChild(B));
      assertFalse(rootNode.hasChild(C));
      assertFalse(nodeA.hasChild(C));
      assertTrue(nodeB.hasChild(C));

      // test data
      assertEquals(vA, nodeA.get(k));
      assertEquals(vB, nodeB.get(k));
      assertEquals(vC, nodeC.get(k));

      // parentage
      assertEquals("B is parent of C: " + nodeB, nodeB, nodeC.getParent());
   }

   @SuppressWarnings("unchecked")
   private Node<Object, Object> genericize(Node node)
   {
      return (Node<Object, Object>) node;
   }

   public void testMoveWithChildren()
   {
      Node<Object, Object> rootNode = cache.getRoot();
      
      Node<Object, Object> nodeA = rootNode.addChild(A);
      nodeA.put(k, vA);
      Node<Object, Object> nodeB = rootNode.addChild(B);
      nodeB.put(k, vB);
      Node<Object, Object> nodeC = nodeA.addChild(C);
      nodeC.put(k, vC);
      Node<Object, Object> nodeD = nodeC.addChild(D);
      nodeD.put(k, vD);
      Node<Object, Object> nodeE = nodeD.addChild(E);
      nodeE.put(k, vE);

      assertTrue(rootNode.hasChild(A));
      assertTrue(rootNode.hasChild(B));
      assertFalse(rootNode.hasChild(C));
      assertTrue(nodeA.hasChild(C));
      assertTrue(nodeC.hasChild(D));
      assertTrue(nodeD.hasChild(E));

      // test data
      assertEquals(vA, nodeA.get(k));
      assertEquals(vB, nodeB.get(k));
      assertEquals(vC, nodeC.get(k));
      assertEquals(vD, nodeD.get(k));
      assertEquals(vE, nodeE.get(k));

      // parentage
      assertEquals(rootNode, nodeA.getParent());
      assertEquals(rootNode, nodeB.getParent());
      assertEquals(nodeA, nodeC.getParent());
      assertEquals(nodeC, nodeD.getParent());
      assertEquals(nodeD, nodeE.getParent());

      // move
      log.info("move " + nodeC + " to " + nodeB);
      cache.move(nodeC.getFqn(), nodeB.getFqn());

      // child nodes will need refreshing, since existing pointers will be stale.
      nodeC = nodeB.getChild(C);
      nodeD = nodeC.getChild(D);
      nodeE = nodeD.getChild(E);

      assertTrue(rootNode.hasChild(A));
      assertTrue(rootNode.hasChild(B));
      assertFalse(rootNode.hasChild(C));
      assertFalse(nodeA.hasChild(C));
      assertTrue(nodeB.hasChild(C));
      assertTrue(nodeC.hasChild(D));
      assertTrue(nodeD.hasChild(E));

      // test data
      assertEquals(vA, nodeA.get(k));
      assertEquals(vB, nodeB.get(k));
      assertEquals(vC, nodeC.get(k));
      assertEquals(vD, nodeD.get(k));
      assertEquals(vE, nodeE.get(k));

      // parentage
      assertEquals(rootNode, nodeA.getParent());
      assertEquals(rootNode, nodeB.getParent());
      assertEquals(nodeB, nodeC.getParent());
      assertEquals(nodeC, nodeD.getParent());
      assertEquals(nodeD, nodeE.getParent());
   }

   public void testTxCommit() throws Exception
   {
      Node<Object, Object> rootNode = cache.getRoot();

      Node<Object, Object> nodeA = rootNode.addChild(A);
      Node<Object, Object> nodeB = nodeA.addChild(B);

      assertEquals(rootNode, nodeA.getParent());
      assertEquals(nodeA, nodeB.getParent());
      assertEquals(nodeA, rootNode.getChildren().iterator().next());
      assertEquals(nodeB, nodeA.getChildren().iterator().next());

      tm.begin();
      // move node B up to hang off the root
      cache.move(nodeB.getFqn(), Fqn.ROOT);

      tm.commit();

      nodeB = rootNode.getChild(B);

      assertEquals(rootNode, nodeA.getParent());
      assertEquals(rootNode, nodeB.getParent());

      assertTrue(rootNode.getChildren().contains(nodeA));
      assertTrue(rootNode.getChildren().contains(nodeB));

      assertTrue(nodeA.getChildren().isEmpty());
   }

   public void testTxRollback() throws Exception
   {
      Node<Object, Object> rootNode = cache.getRoot();

      Node<Object, Object> nodeA = rootNode.addChild(A);
      Node<Object, Object> nodeB = nodeA.addChild(B);

      assertEquals(rootNode, nodeA.getParent());
      assertEquals(nodeA, nodeB.getParent());
      assertEquals(nodeA, rootNode.getChildren().iterator().next());
      assertEquals(nodeB, nodeA.getChildren().iterator().next());


      tm.begin();
      // move node B up to hang off the root
      cache.move(nodeB.getFqn(), Fqn.ROOT);

      // need to think of a way to test the same with optimistically locked nodes
      if (nodeLockingScheme == NodeLockingScheme.PESSIMISTIC)
      {
         assertEquals(rootNode, nodeA.getParent());
         assertEquals(rootNode, nodeB.getParent());
         assertTrue(rootNode.getChildren().contains(nodeA));
         assertTrue(rootNode.getChildren().contains(nodeB));
         assertTrue(nodeA.getChildren().isEmpty());
      }


      tm.rollback();

      nodeA = rootNode.getChild(A);
      nodeB = nodeA.getChild(B);

      // should revert
      assertEquals(rootNode, nodeA.getParent());
      assertEquals(nodeA, nodeB.getParent());
      assertEquals(nodeA, rootNode.getChildren().iterator().next());
      assertEquals(nodeB, nodeA.getChildren().iterator().next());
   }

   public void testLocksDeepMove() throws Exception
   {
      Node<Object, Object> rootNode = cache.getRoot();

      Node<Object, Object> nodeA = rootNode.addChild(A);
      Node<Object, Object> nodeB = nodeA.addChild(B);
      Node<Object, Object> nodeD = nodeB.addChild(D);
      Node<Object, Object> nodeC = rootNode.addChild(C);
      Node<Object, Object> nodeE = nodeC.addChild(E);
      assertEquals(0, cache.getNumberOfLocksHeld());
      tm.begin();

      cache.move(nodeC.getFqn(), nodeB.getFqn());

      checkLocksDeep();


      tm.commit();

      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testLocks() throws Exception
   {
      Node<Object, Object> rootNode = cache.getRoot();

      Node<Object, Object> nodeA = rootNode.addChild(A);
      Node<Object, Object> nodeB = nodeA.addChild(B);
      Node<Object, Object> nodeC = rootNode.addChild(C);
      assertEquals(0, cache.getNumberOfLocksHeld());
      tm.begin();

      cache.move(nodeC.getFqn(), nodeB.getFqn());

      checkLocks();

      tm.commit();
      assertNoLocks();
   }

   protected void checkLocks()
   {
      assertEquals("ROOT should have a RL, nodeC should have a RL, nodeA should have a RL, nodeB should have a WL", 4, cache.getNumberOfLocksHeld());
   }

   protected void checkLocksDeep()
   {
      assertEquals("ROOT should have a RL, nodeC should have a RL, nodeA should have a RL, nodeB should have a WL, nodeD should have a WL", 6, cache.getNumberOfLocksHeld());
   }

   protected void assertNoLocks()
   {
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testConcurrency() throws InterruptedException
   {
      Node<Object, Object> rootNode = cache.getRoot();

      final int N = 3;// number of threads
      final int loops = 1 << 4;// number of loops
      // tests a tree structure as such:
      // /a
      // /b
      // /c
      // /d
      // /e
      // /x
      // /y

      // N threads constantly move /x and /y around to hang off either /a ~ /e randomly.

      final Fqn FQN_A = A, FQN_B = B, FQN_C = C, FQN_D = D, FQN_E = E, FQN_X = Fqn.fromString("/x"), FQN_Y = Fqn.fromString("/y");

      // set up the initial structure.
      final Node[] NODES = {
            rootNode.addChild(FQN_A), rootNode.addChild(FQN_B),
            rootNode.addChild(FQN_C), rootNode.addChild(FQN_D), rootNode.addChild(FQN_E)
      };

      final Node<Object, Object> NODE_X = genericize(NODES[0]).addChild(FQN_X);
      final Node<Object, Object> NODE_Y = genericize(NODES[1]).addChild(FQN_Y);

      Thread[] movers = new Thread[N];
      final CountDownLatch latch = new CountDownLatch(1);
      final Random r = new Random();

      for (int i = 0; i < N; i++)
      {
         movers[i] = new Thread(Thread.currentThread().getName() + " -Mover-" + i)
         {
            public void run()
            {
               try
               {
                  latch.await();
               }
               catch (InterruptedException e)
               {
               }

               for (int counter = 0; counter < loops; counter++)
               {

                  try
                  {
                     cache.move(NODE_X.getFqn(), NODES[r.nextInt(NODES.length)].getFqn());
                  }
                  catch (NodeNotExistsException e)
                  {
                     // this may happen ...
                  }
                  TestingUtil.sleepRandom(50);
                  try
                  {
                     cache.move(NODE_Y.getFqn(), NODES[r.nextInt(NODES.length)].getFqn());
                  }
                  catch (NodeNotExistsException e)
                  {
                     // this may happen ...
                  }
                  TestingUtil.sleepRandom(50);
               }
            }
         };
         movers[i].start();
      }

      latch.countDown();

      for (Thread t : movers)
      {
         t.join();
      }

      assertEquals(0, cache.getNumberOfLocksHeld());
      boolean found_x = false, found_x_again = false;
      for (Node erased : NODES)
      {
         Node<Object, Object> n = genericize(erased);
         if (!found_x)
         {
            found_x = n.hasChild(FQN_X);
         }
         else
         {
            found_x_again = found_x_again || n.hasChild(FQN_X);
         }
      }
      boolean found_y = false, found_y_again = false;
      for (Node erased : NODES)
      {
         Node<Object, Object> n = genericize(erased);
         if (!found_y)
         {
            found_y = n.hasChild(FQN_Y);
         }
         else
         {
            found_y_again = found_y_again || n.hasChild(FQN_Y);
         }
      }

      assertTrue("Should have found x", found_x);
      assertTrue("Should have found y", found_y);
      assertFalse("Should have only found x once", found_x_again);
      assertFalse("Should have only found y once", found_y_again);
   }

   public void testMoveInSamePlace()
   {
      Node<Object, Object> rootNode = cache.getRoot();

      final Fqn FQN_X = Fqn.fromString("/x");
      // set up the initial structure.
      Node aNode = rootNode.addChild(A);
      Node xNode = aNode.addChild(FQN_X);
      assertEquals(aNode.getChildren().size(), 1);
      cache.move(xNode.getFqn(), aNode.getFqn());
      assertEquals(aNode.getChildren().size(), 1);

      assert 0 == cache.getNumberOfLocksHeld();
   }

   protected boolean isOptimistic()
   {
      return false;
   }
}
