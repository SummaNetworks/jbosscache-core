/*
 * Created on 17-Feb-2005
 *
 *
 *
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.tx.CommitCommand;
import org.jboss.cache.commands.tx.OptimisticPrepareCommand;
import org.jboss.cache.interceptors.CallInterceptor;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.transaction.TransactionTable;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.cache.util.TestingUtil;

/**
 * @author xenephon
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.NodeInterceptorRemoveNodeTest")
@SuppressWarnings("unchecked")
public class NodeInterceptorRemoveNodeTest extends AbstractOptimisticTestCase
{
   private CacheSPI<Object, Object> cache;
   private TestListener listener;
   private MockInterceptor dummy;
   private TransactionManager mgr;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      listener = new TestListener();
      cache = createCacheWithListener(listener);

      dummy = new MockInterceptor();

      cache.addInterceptor(dummy, CallInterceptor.class);
      cache.removeInterceptor(CallInterceptor.class);

      mgr = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      super.tearDown();
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testTransactionRemoveNotExistsNodeMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));


      cache.removeNode("/one/two");

      TransactionTable table = cache.getTransactionTable();
      GlobalTransaction gtx = table.get(tx);
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);
      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

      mgr.commit();

      assert 2 == dummy.getAllCalled().size();
      assert dummy.getAllCalled().contains(CommitCommand.class);
      assert dummy.getAllCalled().contains(OptimisticPrepareCommand.class);

      //assert what should be the results of our call
      assertEquals(0, workspace.getNodes().size());

      assertTrue(entry.getLocks().isEmpty());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(0, listener.getNodesAdded());
   }

   public void testTransactionRemoveNodeMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));


      Map temp = new HashMap();
      temp.put("key1", "value");
      cache.put("/one/two", temp);

      cache.removeNode("/one/two");

      assert dummy.getAllCalled().isEmpty();

      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace<?, ?> workspace = entry.getTransactionWorkSpace();

      mgr.commit();

      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());

      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one/two")).isRemoved());
      assertNotNull(workspace.getNode(Fqn.fromString("/one")));
      assertEquals(false, workspace.getNode(Fqn.fromString("/one")).isRemoved());
      List<Set<Fqn>> mergedChildren = workspace.getNode(Fqn.fromString("/one")).getMergedChildren();
      assertEquals(1, mergedChildren.get(1).size());
      assertTrue(!cache.exists("/one/two"));
   }

   public void testTransactionRemoveIntermediateNodeMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));


      Map temp = new HashMap();
      temp.put("key1", "value");
      cache.put("/one/two", temp);

      cache.removeNode("/one");
      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace<?, ?> workspace = entry.getTransactionWorkSpace();

      mgr.commit();

      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());

      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one/two")).isRemoved());
      assertNotNull(workspace.getNode(Fqn.fromString("/one")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one")).isRemoved());
      assertEquals(0, workspace.getNode(Fqn.fromString("/one")).getMergedChildren().get(0).size());
      assertTrue(!cache.exists("/one/two"));
      assert 2 == dummy.getAllCalled().size();
      assert dummy.getAllCalled().contains(CommitCommand.class);
      assert dummy.getAllCalled().contains(OptimisticPrepareCommand.class);

   }

   public void testTransactionRemoveTwiceMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));


      Map temp = new HashMap();
      temp.put("key1", "value");
      cache.put("/one/two", temp);

      // get the transaction stuff
      TransactionTable table = cache.getTransactionTable();
      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

      workspace.getNode(Fqn.fromString("/one"));
      workspace.getNode(Fqn.fromString("/one/two"));


      cache.removeNode("/one");

      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one/two")).isRemoved());
      assertNotNull(workspace.getNode(Fqn.fromString("/one")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one")).isRemoved());

      //now put /one/two back in
      cache.removeNode("/one");


      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one/two")).isRemoved());
      assertNotNull(workspace.getNode(Fqn.fromString("/one")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one")).isRemoved());

      assertEquals(null, dummy.getCalledCommand());

      mgr.commit();

      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());

      assertTrue(!cache.exists("/one/two"));
      assert 2 == dummy.getAllCalled().size();
      assert dummy.getAllCalled().contains(CommitCommand.class);
      assert dummy.getAllCalled().contains(OptimisticPrepareCommand.class);
   }


   public void testTransactionRemovePutNodeMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));


      Map temp = new HashMap();
      temp.put("key1", "value");
      cache.put("/one/two", temp);

      // get the transaction stuff
      TransactionTable table = cache.getTransactionTable();
      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

      WorkspaceNode one = workspace.getNode(Fqn.fromString("/one"));
      WorkspaceNode two = workspace.getNode(Fqn.fromString("/one/two"));


      cache.removeNode("/one");

      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one/two")).isRemoved());
      assertNotNull(workspace.getNode(Fqn.fromString("/one")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one")).isRemoved());

      //now put /one/two back in
      cache.put("/one/two", temp);

      WorkspaceNode oneAfter = workspace.getNode(Fqn.fromString("/one"));
      WorkspaceNode twoAfter = workspace.getNode(Fqn.fromString("/one/two"));

      assertSame(one, oneAfter);
      assertEquals(false, oneAfter.isRemoved());
      assertSame(two, twoAfter);
      assertEquals(false, twoAfter.isRemoved());

      assertEquals(null, dummy.getCalledCommand());


      mgr.commit();

      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());


      assert 2 == dummy.getAllCalled().size();
      assert dummy.getAllCalled().contains(CommitCommand.class);
      assert dummy.getAllCalled().contains(OptimisticPrepareCommand.class);
      assertEquals(2, listener.getNodesAdded());
   }


   public void testTransactionRemovePutkeyValMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));


      Map temp = new HashMap();
      temp.put("key1", "value");
      cache.put("/one/two", temp);

      // get the transaction stuff
      TransactionTable table = cache.getTransactionTable();
      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

      WorkspaceNode one = workspace.getNode(Fqn.fromString("/one"));
      WorkspaceNode two = workspace.getNode(Fqn.fromString("/one/two"));


      cache.removeNode("/one");

      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one/two")).isRemoved());
      assertNotNull(workspace.getNode(Fqn.fromString("/one")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one")).isRemoved());

      //now put /one back in
      cache.put(Fqn.fromString("/one"), "key1", "value2");

      WorkspaceNode oneAfter = workspace.getNode(Fqn.fromString("/one"));
      WorkspaceNode twoAfter = workspace.getNode(Fqn.fromString("/one/two"));

      assertSame(one, oneAfter);
      assertEquals(false, oneAfter.isRemoved());
      assertEquals(two, twoAfter);
      assertEquals(true, twoAfter.isRemoved());

      assertEquals(null, dummy.getCalledCommand());

      mgr.commit();

      assertEquals("value2", workspace.getNode(Fqn.fromString("/one")).get("key1"));
      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());


      assertTrue(!cache.exists("/one/two"));
      assert 2 == dummy.getAllCalled().size();
      assert dummy.getAllCalled().contains(CommitCommand.class);
      assert dummy.getAllCalled().contains(OptimisticPrepareCommand.class);

      assertEquals(2, listener.getNodesAdded());
   }

   public void testTransactionRemoveSubNodeMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));


      Map temp = new HashMap();
      temp.put("key1", "value");
      cache.put("/one/two", temp);

      // get the transaction stuff
      TransactionTable table = cache.getTransactionTable();
      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace<?, ?> workspace = entry.getTransactionWorkSpace();

      WorkspaceNode<?, ?> one = workspace.getNode(Fqn.fromString("/one"));

      assertEquals(1, one.getMergedChildren().get(0).size());

      cache.removeNode("/one/two");

      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(true, workspace.getNode(Fqn.fromString("/one/two")).isRemoved());
      assertNotNull(workspace.getNode(Fqn.fromString("/one")));
      assertEquals(false, workspace.getNode(Fqn.fromString("/one")).isRemoved());

      assertEquals(null, dummy.getCalledCommand());

      mgr.commit();

      assertEquals(1, workspace.getNode(Fqn.fromString("/one")).getMergedChildren().get(1).size());
      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());

      assertTrue(!cache.exists("/one/two"));
      assert 2 == dummy.getAllCalled().size();
      assert dummy.getAllCalled().contains(CommitCommand.class);
      assert dummy.getAllCalled().contains(OptimisticPrepareCommand.class);

      assertEquals(2, listener.getNodesAdded());
   }
}
