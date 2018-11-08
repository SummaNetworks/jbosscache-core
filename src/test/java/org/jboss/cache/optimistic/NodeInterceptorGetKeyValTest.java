package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.interceptors.OptimisticCreateIfNotExistsInterceptor;
import org.jboss.cache.interceptors.OptimisticNodeInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.loader.SamplePojo;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * @author xenephon
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.NodeInterceptorGetKeyValTest")
public class NodeInterceptorGetKeyValTest extends AbstractOptimisticTestCase
{
   private CacheSPI<Object, Object> cache;
   private TransactionManager mgr;
   private MockInterceptor dummy;

   @BeforeMethod
   public void setUp() throws Exception
   {
      cache = createCache();

      CommandInterceptor interceptor = TestingUtil.findInterceptor(cache, OptimisticCreateIfNotExistsInterceptor.class);
      CommandInterceptor nodeInterceptor = TestingUtil.findInterceptor(cache, OptimisticNodeInterceptor.class);
      dummy = new MockInterceptor();

      interceptor.setNext(nodeInterceptor);
      nodeInterceptor.setNext(dummy);

      TestingUtil.replaceInterceptorChain(cache, interceptor);

      mgr = cache.getTransactionManager();
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testTransactionGetKeyMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      @SuppressWarnings("unchecked")
      TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();

      //assert we can see this with a key value get in the transaction
      assertEquals(pojo, cache.get("/one/two", "key1"));
      mgr.commit();

      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());

      //assert that we cannot see the change if we have not put it into the cache
      // we need to do this as we do not have the tx interceptor in this stack
      mgr.begin();

      Transaction tx2 = mgr.getTransaction();

      // inject InvocationContext
      setupTransactions(cache, tx2);

      assertNull(cache.get("/one/two", "key1"));
      mgr.commit();
   }

   public void testTransactionGetKeyValOverwriteMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      //overwrite the map we just put in
      SamplePojo pojo2 = new SamplePojo(22, "test2");

      cache.put("/one/two", "key1", pojo2);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      @SuppressWarnings("unchecked")
      TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();
      assertEquals(pojo2, cache.get("/one/two", "key1"));
      mgr.commit();

      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo2, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(2, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());
   }


   public void testTransactionGetKeyValOverwriteNullMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);


      cache.put("/one/two", "key1", null);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      @SuppressWarnings("unchecked")
      TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();

      assertEquals(null, cache.get("/one/two", "key1"));

      mgr.commit();
      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(null, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(2, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());
   }


   public void testTwoTransactionGetIsolationKeyValMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      // inject InvocationContext
      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertEquals(pojo, cache.get("/one/two", "key1"));
      //suspend current transaction
      mgr.suspend();

      //start a new transaction
      mgr.begin();
      Transaction tx2 = mgr.getTransaction();
      // inject InvocationContext
      setupTransactions(cache, tx2);

      SamplePojo pojo2 = new SamplePojo(22, "test2");

      cache.put("/one/two", "key2", pojo2);
      assertEquals(null, cache.get("/one/two", "key1"));
      assertEquals(pojo2, cache.get("/one/two", "key2"));


      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();


      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      @SuppressWarnings("unchecked")
      TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();

      //resume the suspended transaction
      GlobalTransaction gtx2 = table.get(tx2);

      OptimisticTransactionContext entry2 = (OptimisticTransactionContext) table.get(gtx2);

      @SuppressWarnings("unchecked")
      TransactionWorkspace<Object, Object> workspace2 = entry2.getTransactionWorkSpace();

      //commit both tx
      mgr.commit();
      mgr.resume(tx);
      mgr.commit();

      //assert that our keys are in one space
      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(null, workspace.getNode(Fqn.fromString("/one/two")).get("key2"));
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());

      //assert that our keys are in one space
      assertEquals(3, workspace2.getNodes().size());
      assertNotNull(workspace2.getNode(Fqn.fromString("/one/two")));
      assertEquals(null, workspace2.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertEquals(pojo2, workspace2.getNode(Fqn.fromString("/one/two")).get("key2"));
      assertTrue(entry2.getLocks().isEmpty());
      assertEquals(1, entry2.getModifications().size());

      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());
   }
}
