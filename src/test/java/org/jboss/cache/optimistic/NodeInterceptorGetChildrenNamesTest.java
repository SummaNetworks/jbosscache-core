package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.factories.ComponentRegistry;
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
import java.util.Iterator;

/**
 * @author xenephon
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.NodeInterceptorGetChildrenNamesTest")
public class NodeInterceptorGetChildrenNamesTest extends AbstractOptimisticTestCase
{
   TestListener listener;
   CacheSPI<Object, Object> cache;
   MockInterceptor dummy;
   TransactionManager mgr;

   @BeforeMethod
   public void setUp() throws Exception
   {
      listener = new TestListener();
      cache = createCacheWithListener(listener);

      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache);

      CommandInterceptor interceptor = new OptimisticCreateIfNotExistsInterceptor();
      cr.registerComponent(interceptor, OptimisticCreateIfNotExistsInterceptor.class);
      CommandInterceptor nodeInterceptor = new OptimisticNodeInterceptor();
      cr.registerComponent(nodeInterceptor, OptimisticNodeInterceptor.class);
      dummy = new MockInterceptor();
      cr.registerComponent(dummy, MockInterceptor.class);

      interceptor.setNext(nodeInterceptor);
      nodeInterceptor.setNext(dummy);

      TestingUtil.replaceInterceptorChain(cache, interceptor);

      mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   @SuppressWarnings("unchecked")
   public void testTransactionGetNamesMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      TransactionWorkspace workspace = entry.getTransactionWorkSpace();

      //assert we can see this with a key value get in the transaction
      assertEquals(1, cache.getNode("/one").getChildrenNames().size());
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

      setupTransactions(cache, tx2);

      assertEquals(0, cache.getRoot().getChildrenNames().size());
      mgr.commit();
   }


   public void testTransactionGetNoNamesMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      setupTransactions(cache, tx);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      //assert we can see this with a key value get in the transaction
      assertEquals(0, cache.getRoot().getChildrenNames().size());
      mgr.commit();


      assertTrue(entry.getLocks().isEmpty());
      assertEquals(0, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());
   }


   public void testTransactionGetNamesIteratorMethod() throws Exception
   {
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      setupTransactions(cache, tx);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertEquals(null, dummy.getCalledCommand());
      TransactionTable table = cache.getTransactionTable();

      GlobalTransaction gtx = table.get(tx);

      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      @SuppressWarnings("unchecked")
      TransactionWorkspace<Object, Object> workspace = entry.getTransactionWorkSpace();

      //assert we can see this
      assertEquals(1, cache.getNode("/one").getChildrenNames().size());

      try
      {
         for (Iterator<?> it = cache.getNode("/one").getChildrenNames().iterator(); it.hasNext();)
         {
            it.next();
            it.remove();
         }
         fail("Should not be allowed to modify elements in the set returned by getChildrenNames()");
      }
      catch (UnsupportedOperationException uoe)
      {
         // the returned set should be unmodifiable and a remove on the iterator should fail.
      }

      //assert the removal has had no effect
      assertEquals(1, cache.getNode("/one").getChildrenNames().size());
      assertNotNull(workspace.getNode(Fqn.fromString("/one/two")));

      mgr.commit();


      assertTrue(entry.getLocks().isEmpty());
      assertEquals(1, entry.getModifications().size());
      assertTrue(!cache.exists("/one/two"));
      assertEquals(null, dummy.getCalledCommand());
   }
}
