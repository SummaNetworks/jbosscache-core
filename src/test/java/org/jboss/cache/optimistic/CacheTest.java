/*
 * Created on 17-Feb-2005
 *
 */
package org.jboss.cache.optimistic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.*;
import org.jboss.cache.commands.WriteCommand;
import org.jboss.cache.commands.tx.CommitCommand;
import org.jboss.cache.commands.tx.OptimisticPrepareCommand;
import org.jboss.cache.commands.tx.RollbackCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.invocation.NodeInvocationDelegate;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.Address;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.List;
import java.util.concurrent.CountDownLatch;


@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.CacheTest")
public class CacheTest extends AbstractOptimisticTestCase
{
   Log log = LogFactory.getLog(CacheTest.class);

   private CacheSPI<Object, Object> c;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      c = createCache();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      super.tearDown();
      if (c != null)
        TestingUtil.killCaches((Cache<Object, Object>) c);
      c = null;
   }

   public void testRoot()
   {
      NodeSPI node = c.getRoot();
      assert ((NodeInvocationDelegate) node).getDelegationTarget() instanceof VersionedNode;
   }

   public void testExplicitTxFailure() throws Exception
   {
      // explicit.
      TransactionManager mgr = c.getTransactionManager();
      try
      {
         mgr.begin();
         c.put("/a", "k", "v");
         Transaction t = mgr.suspend();
         c.put("/a", "k2", "v2");
         mgr.resume(t);
         mgr.commit();
         assertTrue("Expecting a rollback exception!", false);
      }
      catch (RollbackException re)
      {
         assertTrue("Expecting a rollback exception!", true);
      }
   }

   public void testImplicitTxFailure() throws Exception
   {
      // implicit (much harder to orchestrate...
      int numThreads = 100;
      ExceptionThread thread[] = new ExceptionThread[numThreads];
      final CountDownLatch latch = new CountDownLatch(1);

      for (int i = 0; i < numThreads; i++)
      {
         thread[i] = new ExceptionThread()
         {
            public void run()
            {
               try
               {
                  latch.await();
                  for (int i = 0; i < 5; i++)
                  {
                     c.put("/a", "k", "v");
                  }
               }
               catch (Exception e)
               {
                  setException(e);
               }
            }
         };
      }

      for (int i = 0; i < numThreads; i++)
         thread[i].start();
      latch.countDown();
      for (int i = 0; i < numThreads; i++)
         thread[i].join();
      // test exceptions.  Expecting at least one exception
      Exception e;
      int exceptionCount = 0;
      for (int i = 0; i < numThreads; i++)
      {

         if ((e = thread[i].getException()) != null)
         {
            assertFalse("Should never see a RollbackException - instead, expecting the CAUSE of the rollback.", e instanceof RollbackException);
            exceptionCount++;
         }
      }

      assertTrue("Expecting at least ONE concurrent write exception!!", exceptionCount > 0);
   }

   public void testLocalTransaction() throws Exception
   {
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, c);

      TransactionManager mgr = c.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      assertEquals(0, c.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, c.getTransactionTable().getNumLocalTransactions());

      c.put("/one/two", "key1", "value");

      mgr.commit();

      assertNull(mgr.getTransaction());
      assertEquals(0, c.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, c.getTransactionTable().getNumLocalTransactions());

      //make sure all calls were done in right order

      List<?> calls = dummy.getAllCalled();

      assertEquals(OptimisticPrepareCommand.class, calls.get(0));
      assertEquals(CommitCommand.class, calls.get(1));
   }

   public void testRollbackTransaction() throws Exception
   {

     TestingUtil.killCaches((Cache<Object, Object>) c);
     c = createCacheWithListener();

      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, c);


      TransactionManager mgr = c.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());
      assertEquals(0, c.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, c.getTransactionTable().getNumLocalTransactions());

      mgr.begin();
      c.put("/one/two", "key1", "value");
      mgr.rollback();
      assertNull(mgr.getTransaction());
      assertEquals(0, c.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, c.getTransactionTable().getNumLocalTransactions());

      //make sure all calls were done in right order

      List<?> calls = dummy.getAllCalled();

      assertEquals(1, calls.size());
      assertEquals(RollbackCommand.class, calls.get(0));
   }

   public void testRemotePrepareTransaction() throws Throwable
   {
     TestingUtil.killCaches((Cache<Object, Object>) c);
     c = createCacheWithListener();

      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, c);

      TransactionManager mgr = c.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      //this sets
      c.getCurrentTransaction(tx, true);

      c.put("/one/two", "key1", "value");

      GlobalTransaction gtx = c.getCurrentTransaction(tx, true);
      TransactionTable table = c.getTransactionTable();
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);
      assertNotNull(mgr.getTransaction());
      WriteCommand command = entry.getModifications().get(0);
      mgr.commit();

      GlobalTransaction remoteGtx = new GlobalTransaction();

      remoteGtx.setAddress(new DummyAddress());
      //hack the method call to make it have the remote globalTransaction

      command.setGlobalTransaction(remoteGtx);

      //call our remote method
      List<WriteCommand> cacheCommands = injectDataVersion(entry.getModifications());
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(remoteGtx, cacheCommands, (Address) remoteGtx.getAddress(), false);

      TestingUtil.replicateCommand(c, prepareCommand);

      //our thread should be null
      assertNull(mgr.getTransaction());

      //	 there should be a registration for the remote globalTransaction
      assertNotNull(table.get(remoteGtx));
      assertNotNull(table.getLocalTransaction(remoteGtx));
      //assert that this is populated
//      assertEquals(1, table.get(remoteGtx).getModifications().size());

      //assert that the remote prepare has populated the local workspace
      assertEquals(3, entry.getTransactionWorkSpace().getNodes().size());
      List<?> calls = dummy.getAllCalled();
      assertEquals(OptimisticPrepareCommand.class, calls.get(2));

      assertEquals(1, c.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, c.getTransactionTable().getNumLocalTransactions());
   }

   public void testRemoteCacheBroadcast() throws Exception
   {
     TestingUtil.killCaches((Cache<Object, Object>) c);

     CacheSPI<Object, Object> cache = createReplicatedCache(Configuration.CacheMode.REPL_SYNC);
      CacheSPI<Object, Object> cache2 = createReplicatedCache(Configuration.CacheMode.REPL_SYNC);
      assertEquals(2, cache.getMembers().size());
      assertEquals(2, cache2.getMembers().size());

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();

      //this sets
      cache.put("/one/two", "key1", "value");

      //GlobalTransaction globalTransaction = cache.getCurrentTransaction(tx);
      assertNotNull(mgr.getTransaction());
      mgr.commit();

      assertNull(mgr.getTransaction());

      //assert that the local cache is in the right state
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertTrue(cache.exists(Fqn.fromString("/one")));
      assertEquals("value", cache.get(Fqn.fromString("/one/two"), "key1"));

      assertEquals(0, cache2.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache2.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache2.exists(Fqn.fromString("/one/two")));
      assertTrue(cache2.exists(Fqn.fromString("/one")));
      assertEquals("value", cache2.get(Fqn.fromString("/one/two"), "key1"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);
   }

   public void testTwoWayRemoteCacheBroadcast() throws Exception
   {

     TestingUtil.killCaches((Cache<Object, Object>) c);
     CacheSPI<Object, Object> cache = createReplicatedCache(Configuration.CacheMode.REPL_SYNC);
      CacheSPI<Object, Object> cache2 = createReplicatedCache(Configuration.CacheMode.REPL_SYNC);
      assertEquals(2, cache.getMembers().size());
      assertEquals(2, cache2.getMembers().size());

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      //this sets
      cache.getCurrentTransaction(tx, true);

      cache.put("/one/two", "key1", "value");

      assertNotNull(mgr.getTransaction());
      mgr.commit();

      assertNull(mgr.getTransaction());

      //assert that the local cache is in the right state
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertTrue(cache.exists(Fqn.fromString("/one")));
      assertEquals("value", cache.get(Fqn.fromString("/one/two"), "key1"));

      assertEquals(0, cache2.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache2.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache2.exists(Fqn.fromString("/one/two")));
      assertTrue(cache2.exists(Fqn.fromString("/one")));

      assertEquals("value", cache2.get(Fqn.fromString("/one/two"), "key1"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);

   }

   public void testConcurrentNodeRemoval() throws Exception
   {
      c.put(fqn, "key", "value");

      // now start a tx to change the value in fqn
      TransactionManager mgr = c.getTransactionManager();
      mgr.begin();

      c.put(fqn, "key2", "value2");

      Transaction tx = mgr.suspend();

      // now remove the original node...
      c.removeNode(fqn);

      mgr.resume(tx);
      // now try and commit this - this should fail.
      boolean ok = false;
      try
      {
         mgr.commit();
      }
      catch (RollbackException rbe)
      {
         ok = true;
      }

      assertTrue("Concurrent mod should result in a rollback", ok);
      // now assert that the node has in fact been removed.
      assertTrue("The node should have been removed!", !c.exists(fqn));

   }

   public void testConcurrentNodeModification() throws Exception
   {
      c.put(fqn, "key", "value");

      // now start a tx to change the value in fqn
      TransactionManager mgr = c.getTransactionManager();
      mgr.begin();

      c.put(fqn, "key2", "value2");

      Transaction tx = mgr.suspend();

      // now change the original node...
      c.put(fqn, "key3", "value3");

      mgr.resume(tx);
      // now try and commit this - this should fail.
      boolean ok = false;
      try
      {
         mgr.commit();
      }
      catch (RollbackException rbe)
      {
         ok = true;
      }

      assertTrue("Concurrent mod should result in a rollback", ok);
   }

   public void testRemoveAndCreate() throws Exception
   {
      c = createCache();
      c.put(fqn, "key", "value");
      TransactionManager tm = c.getTransactionManager();
      tm.begin();
      c.put(fqn, "test", "test");
      tm.commit();

      assertEquals(1, c.getRoot().getChildrenNames().size());

      tm.begin();
      c.removeNode(fqn);
      c.put(fqn, "test", "test");
      tm.commit();

      assertEquals(1, c.getRoot().getChildrenNames().size());

   }

   public void testRemoveChildAfterRemoveParent() throws Exception
   {
      c = createCache();
      TransactionManager tm = c.getTransactionManager();
      c.put(Fqn.fromString("/a/b"), "k", "v");
      tm.begin();
      c.removeNode(Fqn.fromString("/a"));
      c.removeNode(Fqn.fromString("/a/b"));
      tm.commit();

     TestingUtil.killCaches((Cache<Object, Object>) c);
   }

   public void testAddChildAfterRemoveParent() throws Exception
   {
      c = createCache();
      TransactionManager tm = c.getTransactionManager();
      c.put(Fqn.fromString("/a/b"), "k", "v");
      tm.begin();
      c.removeNode(Fqn.fromString("/a"));
      c.put(Fqn.fromString("/a/b"), "k", "v");
      tm.commit();

     TestingUtil.killCaches((Cache<Object, Object>) c);
   }
}
