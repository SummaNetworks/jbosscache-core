package org.jboss.cache.api;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import static org.jboss.cache.config.Configuration.NodeLockingScheme.OPTIMISTIC;
import static org.jboss.cache.config.Configuration.NodeLockingScheme.PESSIMISTIC;
import org.jboss.cache.interceptors.MVCCLockingInterceptor;
import org.jboss.cache.interceptors.OptimisticNodeInterceptor;
import org.jboss.cache.interceptors.PessimisticLockInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.optimistic.TransactionWorkspace;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.*;

/**
 * Tests {@link org.jboss.cache.Node}-centric operations
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional", "pessimistic"}, testName = "api.NodeAPITest")
public class NodeAPITest extends AbstractSingleCacheTest
{
   protected static final Fqn A = Fqn.fromString("/a"), B = Fqn.fromString("/b"), C = Fqn.fromString("/c"), D = Fqn
         .fromString("/d");
   protected Fqn A_B = Fqn.fromRelativeFqn(A, B);
   protected Fqn A_C = Fqn.fromRelativeFqn(A, C);

   public CacheSPI createCache()
   {
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache("configs/local-tx.xml", false, getClass());
      cache.getConfiguration().setNodeLockingScheme(getNodeLockingScheme());
      configure(cache.getConfiguration());
      cache.start();
      return cache;
   }

   protected void configure(Configuration c)
   {
      // to be overridden
   }

   protected NodeLockingScheme getNodeLockingScheme()
   {
      return PESSIMISTIC;
   }

   protected void assertNodeLockingScheme()
   {
      assert cache.getConfiguration().getNodeLockingScheme() == PESSIMISTIC;
      boolean interceptorChainOK = false;

      List<CommandInterceptor> chain = cache.getInterceptorChain();
      for (CommandInterceptor i : chain)
      {
         if (i instanceof PessimisticLockInterceptor) interceptorChainOK = true;
         if (i instanceof OptimisticNodeInterceptor) assert false : "Not a pessimistic locking chain!!";
         if (i instanceof MVCCLockingInterceptor) assert false : "Not a pessimistic locking chain!!";
      }

      assert interceptorChainOK : "Not a pessimistic locking chain!!";
   }

   public void testAddingData()
   {
      assertNodeLockingScheme();
      Node<Object, Object>  rootNode = cache.getRoot();
      Node<Object, Object> nodeA = rootNode.addChild(A);
      nodeA.put("key", "value");

      assertEquals("value", nodeA.get("key"));
   }

   public void testAddingDataTx() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();
      Node<Object, Object>  rootNode = cache.getRoot();
      tm.begin();
      Node<Object, Object> nodeA = rootNode.addChild(A);
      nodeA.put("key", "value");

      assertEquals("value", nodeA.get("key"));
      tm.commit();
   }

   public void testOverwritingDataTx() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();
      Node<Object, Object>  rootNode = cache.getRoot();

      Node<Object, Object> nodeA = rootNode.addChild(A);
      nodeA.put("key", "value");
      assertEquals("value", nodeA.get("key"));
      tm.begin();
      rootNode.removeChild(A);
      cache.put(A, "k2", "v2");
      tm.commit();
      assertNull(nodeA.get("key"));
      assertEquals("v2", nodeA.get("k2"));
   }


   /**
    * Remember, Fqns are relative!!
    */
   public void testParentsAndChildren()
   {

      Node<Object, Object>  rootNode = cache.getRoot();
      
      Node<Object, Object> nodeA = rootNode.addChild(A);
      Node<Object, Object> nodeB = nodeA.addChild(B);
      Node<Object, Object> nodeC = nodeA.addChild(C);
      Node<Object, Object> nodeD = rootNode.addChild(D);

      assertEquals(rootNode, nodeA.getParent());
      assertEquals(nodeA, nodeB.getParent());
      assertEquals(nodeA, nodeC.getParent());
      assertEquals(rootNode, nodeD.getParent());

      assertTrue(rootNode.hasChild(A));
      assertFalse(rootNode.hasChild(B));
      assertFalse(rootNode.hasChild(C));
      assertTrue(rootNode.hasChild(D));

      assertTrue(nodeA.hasChild(B));
      assertTrue(nodeA.hasChild(C));

      assertEquals(nodeA, rootNode.getChild(A));
      assertEquals(nodeD, rootNode.getChild(D));
      assertEquals(nodeB, nodeA.getChild(B));
      assertEquals(nodeC, nodeA.getChild(C));

      assertTrue(nodeA.getChildren().contains(nodeB));
      assertTrue(nodeA.getChildren().contains(nodeC));
      assertEquals(2, nodeA.getChildren().size());

      assertTrue(rootNode.getChildren().contains(nodeA));
      assertTrue(rootNode.getChildren().contains(nodeD));
      assertEquals(2, rootNode.getChildren().size());

      assertEquals(true, rootNode.removeChild(A));
      assertFalse(rootNode.getChildren().contains(nodeA));
      assertTrue(rootNode.getChildren().contains(nodeD));
      assertEquals(1, rootNode.getChildren().size());

      assertEquals("double remove", false, rootNode.removeChild(A));
      assertEquals("double remove", false, rootNode.removeChild(A.getLastElement()));
   }

   public void testLocking() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();
      Node<Object, Object>  rootNode = cache.getRoot();

      tm.begin();
      Node<Object, Object> nodeA = rootNode.addChild(A);
      Node<Object, Object> nodeB = nodeA.addChild(B);
      Node<Object, Object> nodeC = nodeB.addChild(C);

      if (getNodeLockingScheme() != OPTIMISTIC)
      {
         assertEquals(3, cache.getNumberOfNodes());
         assertEquals(4, cache.getNumberOfLocksHeld());
      }
      tm.commit();

      tm.begin();
      assertEquals(0, cache.getNumberOfLocksHeld());
      nodeC.put("key", "value");
      if (getNodeLockingScheme() != OPTIMISTIC) assertEquals(4, cache.getNumberOfLocksHeld());
      tm.commit();
   }

   public void testImmutabilityOfData()
   {

      Node<Object, Object>  rootNode = cache.getRoot();

      rootNode.put("key", "value");
      Map<Object, Object> m = rootNode.getData();
      try
      {
         m.put("x", "y");
         fail("Map should be immutable!!");
      }
      catch (Exception e)
      {
         // expected
      }

      try
      {
         rootNode.getKeys().add(new Object());
         fail("Key set should be immutable");
      }
      catch (Exception e)
      {
         // expected
      }
   }

   public void testDefensiveCopyOfData()
   {

      Node<Object, Object>  rootNode = cache.getRoot();

      rootNode.put("key", "value");
      Map<Object, Object> data = rootNode.getData();
      Set<Object> keys = rootNode.getKeys();

      assert keys.size() == 1;
      assert keys.contains("key");

      assert data.size() == 1;
      assert data.containsKey("key");

      // now change stuff.

      rootNode.put("key2", "value2");

      // assert that the collections we initially got have not changed.
      assert keys.size() == 1;
      assert keys.contains("key");

      assert data.size() == 1;
      assert data.containsKey("key");
   }

   public void testDefensiveCopyOfChildren()
   {

      Node<Object, Object>  rootNode = cache.getRoot();

      Fqn childFqn = Fqn.fromString("/child");
      rootNode.addChild(childFqn).put("k", "v");
      Set<Node<Object, Object>> children = rootNode.getChildren();
      Set<Object> childrenNames = rootNode.getChildrenNames();

      assert childrenNames.size() == 1;
      assert childrenNames.contains(childFqn.getLastElement());

      assert children.size() == 1;
      assert children.iterator().next().getFqn().equals(childFqn);

      // now change stuff.

      rootNode.addChild(Fqn.fromString("/child2"));

      // assert that the collections we initially got have not changed.
      assert childrenNames.size() == 1;
      assert childrenNames.contains(childFqn.getLastElement());

      assert children.size() == 1;
      assert children.iterator().next().getFqn().equals(childFqn);
   }


   public void testImmutabilityOfChildren()
   {

      Node<Object, Object>  rootNode = cache.getRoot();

      rootNode.addChild(A);

      try
      {
         rootNode.getChildren().clear();
         fail("Collection of child nodes returned in getChildrenDirect() should be immutable");
      }
      catch (Exception e)
      {
         // expected
      }
   }

   protected void childrenUnderTxCheck() throws Exception
   {


      assertEquals(3, cache.getNumberOfNodes());
      assertEquals(4, cache.getNumberOfLocksHeld());
   }

   public void testGetChildrenUnderTx() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();

      tm.begin();
      cache.put(A_B, "1", "1");
      cache.put(A_C, "2", "2");

      childrenUnderTxCheck();
      assertEquals("Number of child", 2, cache.getRoot().getChild(A).getChildren().size());
      tm.commit();
   }

   @SuppressWarnings("unchecked")
   protected TransactionWorkspace<Object, Object> getTransactionWorkspace() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();
      return ((OptimisticTransactionContext) cache.getTransactionTable().get(cache.getTransactionTable().get(tm.getTransaction()))).getTransactionWorkSpace();
   }

   public void testGetChildAPI()
   {

      Node<Object, Object>  rootNode = cache.getRoot();

      // creates a Node<Object, Object> with fqn /a/b/c
      Node childA = rootNode.addChild(A);
      childA.addChild(B).addChild(C);

      rootNode.getChild(A).put("key", "value");
      rootNode.getChild(A).getChild(B).put("key", "value");
      rootNode.getChild(A).getChild(B).getChild(C).put("key", "value");

      assertEquals("value", rootNode.getChild(A).get("key"));
      assertEquals("value", rootNode.getChild(A).getChild(B).get("key"));
      assertEquals("value", rootNode.getChild(A).getChild(B).getChild(C).get("key"));

      assertNull(rootNode.getChild(Fqn.fromElements("nonexistent")));
   }

   public void testClearingData()
   {

      Node<Object, Object>  rootNode = cache.getRoot();

      rootNode.put("k", "v");
      rootNode.put("k2", "v2");
      assertEquals(2, rootNode.getKeys().size());
      rootNode.clearData();
      assertEquals(0, rootNode.getKeys().size());
      assertTrue(rootNode.getData().isEmpty());
   }

   public void testClearingDataTx() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();
      Node<Object, Object>  rootNode = cache.getRoot();

      tm.begin();
      rootNode.put("k", "v");
      rootNode.put("k2", "v2");
      assertEquals(2, rootNode.getKeys().size());
      rootNode.clearData();
      assertEquals(0, rootNode.getKeys().size());
      assertTrue(rootNode.getData().isEmpty());
      tm.commit();
      assertTrue(rootNode.getData().isEmpty());
   }

   public void testPutData()
   {

      Node<Object, Object>  rootNode = cache.getRoot();

      assertTrue(rootNode.getData().isEmpty());

      Map<Object, Object> map = new HashMap<Object, Object>();
      map.put("k1", "v1");
      map.put("k2", "v2");

      rootNode.putAll(map);

      assertEquals(2, rootNode.getData().size());
      assertEquals("v1", rootNode.get("k1"));
      assertEquals("v2", rootNode.get("k2"));

      map.clear();
      map.put("k3", "v3");

      rootNode.putAll(map);
      assertEquals(3, rootNode.getData().size());
      assertEquals("v1", rootNode.get("k1"));
      assertEquals("v2", rootNode.get("k2"));
      assertEquals("v3", rootNode.get("k3"));

      map.clear();
      map.put("k4", "v4");
      map.put("k5", "v5");

      rootNode.replaceAll(map);
      assertEquals(2, rootNode.getData().size());
      assertEquals("v4", rootNode.get("k4"));
      assertEquals("v5", rootNode.get("k5"));
   }

   public void testGetChildrenNames() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();
      Node<Object, Object>  rootNode = cache.getRoot();

      rootNode.addChild(A).put("k", "v");
      rootNode.addChild(B).put("k", "v");

      Set<Object> childrenNames = new HashSet<Object>();
      childrenNames.add(A.getLastElement());
      childrenNames.add(B.getLastElement());

      assertEquals(childrenNames, rootNode.getChildrenNames());

      // now delete a child, within a tx
      tm.begin();
      rootNode.removeChild(B);
      assertFalse(rootNode.hasChild(B));
      childrenNames.remove(B.getLastElement());
      assertEquals(childrenNames, rootNode.getChildrenNames());
      tm.commit();
      assertEquals(childrenNames, rootNode.getChildrenNames());
   }

   public void testDoubleRemovalOfData() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();

      cache.put("/foo/1/2/3", "item", 1);
      assert 1 == (Integer) cache.get("/foo/1/2/3", "item");
      tm.begin();
      assert 1 == (Integer) cache.get("/foo/1/2/3", "item");
      cache.removeNode("/foo/1");
      assertNull(cache.getNode("/foo/1"));
      assertNull(cache.get("/foo/1", "item"));
      cache.removeNode("/foo/1/2/3");
      assertNull(cache.get("/foo/1/2/3", "item"));
      assertNull(cache.get("/foo/1", "item"));
      tm.commit();
      assertFalse(cache.exists("/foo/1"));
      assertNull(cache.get("/foo/1/2/3", "item"));
      assertNull(cache.get("/foo/1", "item"));
   }

   public void testDoubleRemovalOfData2() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();

      cache.put("/foo/1/2", "item", 1);
      tm.begin();
      assertEquals(cache.get("/foo/1", "item"), null);
      cache.removeNode("/foo/1");
      assertNull(cache.get("/foo/1", "item"));
      cache.removeNode("/foo/1/2");
      assertNull(cache.get("/foo/1", "item"));
      tm.commit();
      assertFalse(cache.exists("/foo/1"));
      assertNull(cache.get("/foo/1/2", "item"));
      assertNull(cache.get("/foo/1", "item"));
   }

   public void testIsLeaf()
   {
      cache.put("/a/b/c", "k", "v");
      cache.put("/a/d", "k", "v");

      Node A = cache.getNode("/a");
      Node B = cache.getNode("/a/b");
      Node C = cache.getNode("/a/b/c");
      Node D = cache.getNode("/a/d");
      Node root = cache.getRoot();

      assert !root.isLeaf();
      assert !A.isLeaf();
      assert !B.isLeaf();
      assert C.isLeaf();
      assert D.isLeaf();

      cache.removeNode("/a/b");
      cache.removeNode("/a/d");

      assert A.isLeaf();
   }
}
