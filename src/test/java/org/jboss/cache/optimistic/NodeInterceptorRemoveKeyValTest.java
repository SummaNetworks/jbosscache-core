/*
 * Created on 17-Feb-2005
 *
 *
 *
 */
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
@Test(groups = {"functional", "optimistic"}, testName = "optimistic.NodeInterceptorRemoveKeyValTest")
public class NodeInterceptorRemoveKeyValTest extends AbstractOptimisticTestCase
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

   public void testTransactionRemoveNoNodeKeyValMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      setupTransactions(cache, tx);

      cache.remove("/one/two", "keyOne");

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

      mgr.commit();

      //assert what should be the results of our call
      assertEquals(0, workspace.getNodes().size());
      assertNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());
   }

   public void testTransactionRemoveNoKeyValMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");
      Map temp = new HashMap();
      temp.put("key1", pojo);
      cache.put("/one/two", temp);

      cache.remove("/one/two", "key2");

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

      mgr.commit();

      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertNull(workspace.getNode(Fqn.fromString("/one/two")).get("key2"));
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(2, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());
   }

   public void testTransactionRemoveKeyValMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");
      Map temp = new HashMap();
      temp.put("key1", pojo);
      cache.put("/one/two", temp);

      cache.remove("/one/two", "key1");

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

      mgr.commit();

      //assert what should be the results of our call
      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertNull(workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertTrue(entry.getLocks().isEmpty());
      assertEquals(2, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());
   }
}
