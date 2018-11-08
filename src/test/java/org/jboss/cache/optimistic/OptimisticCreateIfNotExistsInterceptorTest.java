/*
 * Created on 17-Feb-2005
 *
 *
 *
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.interceptors.OptimisticCreateIfNotExistsInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.loader.SamplePojo;
import org.jboss.cache.transaction.DummyTransactionManager;
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
@SuppressWarnings("unchecked")
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.OptimisticCreateIfNotExistsInterceptorTest")
public class OptimisticCreateIfNotExistsInterceptorTest extends AbstractOptimisticTestCase
{
   protected TransactionManager txManager;
   protected Transaction tx;
   protected GlobalTransaction gtx;
   protected TransactionTable table;
   protected OptimisticTransactionContext entry;
   protected TransactionWorkspace workspace;
   CacheSPI cache;
   MockInterceptor dummy;
   SamplePojo pojo;

   @BeforeMethod
   public void setUp() throws Exception
   {
      pojo = new SamplePojo(21, "test");
      cache = createCache();
      CommandInterceptor interceptor = new OptimisticCreateIfNotExistsInterceptor();
      dummy = new MockInterceptor();

      interceptor.setNext(dummy);

      TestingUtil.replaceInterceptorChain(cache, interceptor);

      setupTransactionsInInvocationCtx(cache);
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }


   protected void setupTransactionsInInvocationCtx(CacheSPI cache) throws Exception
   {
      txManager = DummyTransactionManager.getInstance();
      // start a tx
      txManager.begin();

      // set class level vars
      table = cache.getTransactionTable();

      // create a globalTransaction
      gtx = cache.getCurrentTransaction();
      tx = txManager.getTransaction();
      entry = (OptimisticTransactionContext) table.get(gtx);
      workspace = entry.getTransactionWorkSpace();

      setupTransactions(cache, tx);
   }

   public void testNodeCreation() throws Exception
   {
      cache.put("/one/two", "key1", pojo);

      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertNotNull(workspace.getNode(Fqn.fromString("/one/")));
      assertEquals(null, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertTrue(entry.getLocks().isEmpty());

      assertTrue(!cache.exists("/one/two"));
      assertEquals(PutKeyValueCommand.class, dummy.getCalledCommandClass());
      txManager.commit();
   }

   public void testInvalidTransaction() throws Exception
   {
      cache.put("/one/two", "key1", pojo);

      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertNotNull(workspace.getNode(Fqn.fromString("/one/")));
      assertEquals(null, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertTrue(entry.getLocks().isEmpty());

      assertTrue(!cache.exists("/one/two"));
      assertEquals(PutKeyValueCommand.class, dummy.getCalledCommandClass());

      txManager.commit();
      // we should now remove stuff from the InvocationCtx
      cache.getInvocationContext().setGlobalTransaction(null);
      cache.getInvocationContext().setTransaction(null);
      cache.getInvocationContext().setTransactionContext(null);

      try
      {
         cache.put("/one/two/three", "key1", pojo);
         assertTrue("Should never be reched", false);
      }
      catch (Throwable t)
      {
         assertTrue(true);
      }
   }

   public void testMultiplePut() throws Exception
   {
      cache.put("/one/two", "key1", pojo);
      cache.put("/one/two", "key2", pojo);

      assertEquals(3, workspace.getNodes().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));
      assertNotNull(workspace.getNode(Fqn.fromString("/one/")));
      assertEquals(null, workspace.getNode(Fqn.fromString("/one/two")).get("key1"));
      assertTrue(entry.getLocks().isEmpty());

      assertTrue(!cache.exists("/one/two"));
      assertEquals(PutKeyValueCommand.class, dummy.getCalledCommandClass());

      txManager.commit();
   }
}
