/*
 * Created on 17-Feb-2005
 *
 *
 *
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.commands.VisitableCommand;
import org.jboss.cache.commands.tx.CommitCommand;
import org.jboss.cache.commands.tx.OptimisticPrepareCommand;
import org.jboss.cache.commands.tx.RollbackCommand;
import org.jboss.cache.interceptors.InvocationContextInterceptor;
import org.jboss.cache.interceptors.OptimisticCreateIfNotExistsInterceptor;
import org.jboss.cache.interceptors.OptimisticNodeInterceptor;
import org.jboss.cache.interceptors.OptimisticValidatorInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.loader.SamplePojo;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.util.CachePrinter;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.Address;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author xenephon
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.ValidatorInterceptorTest")
public class ValidatorInterceptorTest extends AbstractOptimisticTestCase
{
   private CacheSPI<Object, Object> cache;
   private TransactionManager mgr;
   private MockInterceptor dummy;

   @BeforeMethod
   public void setUp() throws Exception
   {
      cache = createCacheWithListener();
      mgr = cache.getTransactionManager();

      CommandInterceptor ici = TestingUtil.findInterceptor(cache, InvocationContextInterceptor.class);
      CommandInterceptor validateInterceptor = TestingUtil.findInterceptor(cache, OptimisticValidatorInterceptor.class);
      CommandInterceptor interceptor = TestingUtil.findInterceptor(cache, OptimisticCreateIfNotExistsInterceptor.class);
      CommandInterceptor nodeInterceptor = TestingUtil.findInterceptor(cache, OptimisticNodeInterceptor.class);
      dummy = new MockInterceptor();
      ici.setNext(validateInterceptor);
      validateInterceptor.setNext(interceptor);
      interceptor.setNext(nodeInterceptor);
      nodeInterceptor.setNext(dummy);

      TestingUtil.replaceInterceptorChain(cache, ici);
      cache.addInterceptor(new ResetRemoteFlagInterceptor(), InvocationContextInterceptor.class);
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }


   public void testTransactionvalidateMethod() throws Throwable
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));

      SamplePojo pojo = new SamplePojo(21, "test");
      Map<Object, Object> temp = new HashMap<Object, Object>();
      temp.put("key1", pojo);
      cache.put("/one/two", temp);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();

      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(1, workspace.getNode(Fqn.fromString("/one/two")).getMergedData().size());
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());

      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(gtx, entry.getModifications(), (Address) gtx.getAddress(), Boolean.FALSE);
      //now let us do a prepare
      TestingUtil.replicateCommand(cache, prepareCommand);


      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(1, workspace.getNode(Fqn.fromString("/one/two")).getMergedData().size());
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(prepareCommand, dummy.getCalledCommand());


      mgr.commit();
   }

   public void testTransactionValidateFailureMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));

      SamplePojo pojo = new SamplePojo(21, "test");
      Map<Object, Object> temp = new HashMap<Object, Object>();
      temp.put("key1", pojo);
      cache.put("/one/two", temp);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();

      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(1, workspace.getNode(Fqn.fromString("/one/two")).getMergedData().size());
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());

      //lets change one of the underlying version numbers
      workspace.getNode(Fqn.fromString("/one/two")).getNode().setVersion(new DefaultDataVersion(2));
      //now let us do a prepare
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(gtx, entry.getModifications(), (Address) gtx.getAddress(), Boolean.FALSE);
      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
         fail();
      }
      catch (Throwable t)
      {
         assertTrue(true);
      }


      mgr.commit();
   }

   public void testTransactionValidateCommitMethod() throws Throwable
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));

      Object pojo = new SamplePojo(21, "test");
      cache.put("/one/two", Collections.singletonMap((Object) "key1", pojo));

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();

      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(1, workspace.getNode(Fqn.fromString("/one/two")).getMergedData().size());
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());

      //lets change one of the underlying version numbers
      //now let us do a prepare
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(gtx, entry.getModifications(), (Address) gtx.getAddress(), Boolean.FALSE);
      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
         fail();
      }
      catch (Throwable t)
      {
         assertTrue(true);
      }

      CommitCommand commitCommand = new CommitCommand(gtx);
      TestingUtil.replicateCommand(cache, commitCommand);


      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(1, workspace.getNode(Fqn.fromString("/one/two")).getMergedData().size());
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());


      assertEquals(commitCommand, dummy.getCalledCommand());
      NodeSPI<Object, Object> node = workspace.getNode(Fqn.ROOT).getNode();
      //assert we can navigate

      assertNotNull(node);
      node = (NodeSPI<Object, Object>) node.getChild("one");
      assertEquals(new DefaultDataVersion(0), node.getVersion());
      assertNotNull(node);

      node = (NodeSPI<Object, Object>) node.getChild("two");
      assertNotNull(node);

      assertEquals(new DefaultDataVersion(1), node.getVersion());

      assertEquals(pojo, node.get("key1"));

      mgr.commit();
   }


   public void testTransactionValidateFailRemoteCommitMethod() throws Throwable
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));

      SamplePojo pojo = new SamplePojo(21, "test");
      Map<Object, Object> temp = new HashMap<Object, Object>();
      temp.put("key1", pojo);
      cache.put("/one/two", temp);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      @SuppressWarnings("unchecked") TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();

      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(1, workspace.getNode(Fqn.fromString("/one/two")).getMergedData().size());
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());

      //lets change one of the underlying version numbers
      //now let us do a prepare
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(gtx, entry.getModifications(), (Address) gtx.getAddress(), Boolean.FALSE);
      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
         fail();
      }
      catch (Throwable t)
      {
         assertTrue(true);
      }


      CommitCommand commitCommand = new CommitCommand(gtx);
      TestingUtil.replicateCommand(cache, commitCommand);


      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(1, workspace.getNode(Fqn.fromString("/one/two")).getMergedData().size());
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());


      assertEquals(commitCommand, dummy.getCalledCommand());
      NodeSPI<Object, Object> node = workspace.getNode(Fqn.fromString("/")).getNode();
      //assert we can navigate

      assertNotNull(node);
      node = (NodeSPI<Object, Object>) node.getChild("one");
      assertEquals(new DefaultDataVersion(0), node.getVersion());
      assertNotNull(node);
      assertTrue(cache.exists(node.getFqn()));

      node = (NodeSPI<Object, Object>) node.getChild("two");
      assertNotNull(node);
      assertTrue(cache.exists(node.getFqn()));
      assertEquals(new DefaultDataVersion(1), node.getVersion());

      assertEquals(pojo, node.get("key1"));

      mgr.commit();
   }

   public void testTransactionValidateRollbackMethod() throws Throwable
   {

      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      cache.getInvocationContext().setTransaction(tx);
      cache.getInvocationContext().setGlobalTransaction(cache.getCurrentTransaction(tx, true));

      SamplePojo pojo = new SamplePojo(21, "test");
      Map<Object, Object> temp = new HashMap<Object, Object>();
      temp.put("key1", pojo);
      cache.put("/one/two", temp);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();

      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(1, workspace.getNode(Fqn.fromString("/one/two")).getMergedData().size());
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());

      //lets change one of the underlying version numbers
      //now let us do a prepare
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(gtx, entry.getModifications(), (Address) gtx.getAddress(), Boolean.FALSE);

      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
         fail();
      }
      catch (Throwable t)
      {
         assertTrue(true);
      }

      RollbackCommand rollbackCommand = new RollbackCommand(gtx);
      TestingUtil.replicateCommand(cache, rollbackCommand);


      assertEquals(0, workspace.getNodes().size());
      assertNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertNull(workspace.getNode(Fqn.fromString("/one")));
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());

      mgr.commit();
   }

   public static class ResetRemoteFlagInterceptor extends CommandInterceptor
   {
      public Object handleDefault(InvocationContext ctx, VisitableCommand command) throws Throwable
      {
         log.trace("Setting isRemote on globalTransaction " + ctx.getGlobalTransaction() + " to true");
         ctx.getGlobalTransaction().setRemote(true);
         return invokeNextInterceptor(ctx, command);
      }
   }
}
