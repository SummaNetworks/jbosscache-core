package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests that children maps in workspace nodes are only loaded lazily, when a move() or getChildrenNames()
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.ChildMapLazyLoadingTest")
public class ChildMapLazyLoadingTest
{
   private CacheSPI<Object, Object> cache;
   private Fqn parent = Fqn.fromString("/parent");
   private Fqn child = Fqn.fromString("/parent/child");

   @BeforeMethod
   public void setUp()
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.start();
      cache.put(parent, "k", "v");
      cache.put(child, "k", "v");
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testLazyLoadingOnCacheGet() throws Exception
   {
      cache.getTransactionManager().begin();
      assert cache.get(parent, "k").equals("v") : "Should retrieve value";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnCachePut() throws Exception
   {
      cache.getTransactionManager().begin();
      cache.put(parent, "k2", "v2");
      assert cache.get(parent, "k2").equals("v2") : "Should retrieve value";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnCachePutData() throws Exception
   {
      cache.getTransactionManager().begin();
      Map<Object, Object> data = new HashMap<Object, Object>();
      data.put("k2", "v2");
      cache.put(parent, data);
      assert cache.get(parent, "k2").equals("v2") : "Should retrieve value";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnCacheRemove() throws Exception
   {
      cache.getTransactionManager().begin();
      cache.remove(parent, "k");
      assert cache.get(parent, "k") == null : "Data should be removed";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnCacheRemoveNode() throws Exception
   {
      cache.getTransactionManager().begin();
      cache.removeNode(parent);
      assert !cache.getRoot().hasChild(parent) : "Node should be removed";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnCacheMove() throws Exception
   {
      Fqn newparentToMoveTo = Fqn.fromString("/newparent");
      Fqn newparent = Fqn.fromString("/newparent/parent");
      Fqn newchild = Fqn.fromString("/newparent/parent/child");
      cache.getTransactionManager().begin();
      cache.move(parent, newparentToMoveTo);
      assert !cache.getRoot().hasChild(parent) : "Node should be removed";
      assert !cache.getRoot().hasChild(child) : "Node should be removed";
      assert cache.getRoot().hasChild(newparent) : "Node should have moved";
      assert cache.getRoot().hasChild(newchild) : "Node should have moved";
      assert cache.get(newparent, "k").equals("v") : "Data should have moved too";

      WorkspaceNode n = getWorkspaceNode(parent);
      assert n.isChildrenLoaded() : "Should have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnNodeGet() throws Exception
   {
      cache.getTransactionManager().begin();
      Node node = cache.getRoot().getChild(parent);
      assert node.getData().size() == 1 : "Node should have data";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnNodeRemove() throws Exception
   {
      cache.getTransactionManager().begin();
      Node node = cache.getRoot().getChild(parent);
      node.clearData();
      assert node.getData().size() == 0 : "Node should have removed data";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnNodePut() throws Exception
   {
      cache.getTransactionManager().begin();
      Node node = cache.getRoot().getChild(parent);
      node.put("k2", "v2");
      assert node.getData().size() == 2 : "Node should have added data";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnNodeGetChildrenNames() throws Exception
   {
      cache.getTransactionManager().begin();
      Node node = cache.getRoot().getChild(parent);
      assert node.getChildrenNames().size() == 1 : "Node should have 1 child";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert n.isChildrenLoaded() : "Should have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnNodeGetChildren() throws Exception
   {
      cache.getTransactionManager().begin();
      Node node = cache.getRoot().getChild(parent);
      assert node.getChildren().size() == 1 : "Node should have 1 child";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert n.isChildrenLoaded() : "Should have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnNodeAddChild() throws Exception
   {
      Fqn newChild = Fqn.fromString("/newchild");
      cache.getTransactionManager().begin();
      Node node = cache.getRoot().getChild(parent);
      node.addChild(newChild);
      assert node.hasChild(newChild) : "Node should have added child";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   public void testLazyLoadingOnNodeRemoveChild() throws Exception
   {
      cache.getTransactionManager().begin();
      Node node = cache.getRoot().getChild(parent);
      node.removeChild(child.getLastElement());
      assert !node.hasChild(child.getLastElement()) : "Node should have removed child";
      WorkspaceNode n = getWorkspaceNode(parent);
      assert !n.isChildrenLoaded() : "Should not have loaded children";
      cache.getTransactionManager().commit();
   }

   private WorkspaceNode getWorkspaceNode(Fqn fqn) throws Exception
   {
      Transaction tx = cache.getTransactionManager().getTransaction();
      GlobalTransaction gtx = cache.getTransactionTable().get(tx);
      OptimisticTransactionContext te = (OptimisticTransactionContext) cache.getTransactionTable().get(gtx);
      TransactionWorkspace tw = te.getTransactionWorkSpace();
      return tw.getNode(fqn);
   }
}
