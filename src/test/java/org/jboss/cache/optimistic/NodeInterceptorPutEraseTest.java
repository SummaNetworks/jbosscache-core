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
import java.util.HashMap;
import java.util.Map;

/**
 * @author xenephon
 */
@SuppressWarnings("unchecked")
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.NodeInterceptorPutEraseTest")
public class NodeInterceptorPutEraseTest extends AbstractOptimisticTestCase
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

   public void testTransactionPutKeyMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");
      Map temp = new HashMap();
      temp.put("key1", pojo);
      cache.put("/one/two", temp);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

      mgr.commit();

      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertEquals(pojo, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());
   }

   public void testTransactionKeyValOverwriteMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      //overwrite the map we just put in
      SamplePojo pojo2 = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo2);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

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

   public void testTransactionKeyValOverwriteNullMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);


      cache.put("/one/two", "key1", null);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

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
}
