/*
 * Created on 17-Feb-2005
 *
 *
 *
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.commands.WriteCommand;
import org.jboss.cache.commands.tx.CommitCommand;
import org.jboss.cache.commands.tx.OptimisticPrepareCommand;
import org.jboss.cache.commands.tx.RollbackCommand;
import org.jboss.cache.loader.SamplePojo;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.Address;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.List;

@Test(groups = {"functional", "transaction", "optimistic"}, sequential = true, testName = "optimistic.TxInterceptorTest")
public class TxInterceptorTest extends AbstractOptimisticTestCase
{
   @Override
   protected CacheSPI<Object, Object> createCacheUnstarted(boolean optimistic) throws Exception
   {
      CacheSPI<Object, Object> cache = super.createCacheUnstarted(optimistic);
      cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      return cache;
   }

   public void testNoTransaction() throws Exception
   {
      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);
      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);
      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      //make sure all calls were done in right order

      List<?> calls = dummy.getAllCalledIds();

      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(CommitCommand.METHOD_ID, calls.get(1));
      //flesh this out a bit more

   }

   public void testLocalTransactionExists() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);


      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      SamplePojo pojo = new SamplePojo(21, "test");

      assertNotNull(mgr.getTransaction());
      TransactionTable txTable = cache.getTransactionTable();
      assertNull(txTable.get(tx));

      cache.put("/one/two", "key1", pojo);

      assertNotNull(mgr.getTransaction());
      mgr.commit();

      assertNull(mgr.getTransaction());

      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(CommitCommand.METHOD_ID, calls.get(1));

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());
      TestingUtil.killCaches(cache);

   }

   public void testRollbackTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertNotNull(mgr.getTransaction());
      mgr.rollback();

      assertNull(mgr.getTransaction());

      List<?> calls = dummy.getAllCalledIds();
      assertEquals(1, calls.size());
      assertEquals(RollbackCommand.METHOD_ID, calls.get(0));


      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());
      TestingUtil.killCaches(cache);

   }

   public void testEmptyLocalTransaction() throws Exception
   {
      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();

      assertNotNull(mgr.getTransaction());
      mgr.commit();

      assertNull(mgr.getTransaction());

      List<?> calls = dummy.getAllCalled();
      assertEquals(0, calls.size());

      assertNull(mgr.getTransaction());

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      TestingUtil.killCaches(cache);
   }

   public void testEmptyRollbackLocalTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();

      assertNotNull(mgr.getTransaction());
      mgr.rollback();

      assertNull(mgr.getTransaction());

      List<?> calls = dummy.getAllCalled();
      assertEquals(0, calls.size());

      assertNull(mgr.getTransaction());

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());
      TestingUtil.killCaches(cache);

   }

   public void testLocalRollbackAftercommitTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertNotNull(mgr.getTransaction());
      mgr.commit();

      assertNull(mgr.getTransaction());

      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(CommitCommand.METHOD_ID, calls.get(1));
      boolean failed = false;
      try
      {
         mgr.rollback();
         fail();
      }
      catch (Exception e)
      {
         failed = true;
         assertTrue(true);
      }
      assertTrue(failed);
      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());
      TestingUtil.killCaches(cache);
   }


   public void testgtxTransactionExists() throws Exception
   {
      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      //this sets
      cache.getCurrentTransaction(tx, true);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertNotNull(mgr.getTransaction());
      mgr.commit();

      assertNull(mgr.getTransaction());

      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(CommitCommand.METHOD_ID, calls.get(1));


      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      TestingUtil.killCaches(cache);
   }


   public void testRemotePrepareTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();


      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      GlobalTransaction gtx = cache.getCurrentTransaction(tx, true);
      TransactionTable table = cache.getTransactionTable();
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);
      assertNotNull(mgr.getTransaction());
      WriteCommand command = entry.getModifications().get(0);
      mgr.commit();

      //test local calls
      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(CommitCommand.METHOD_ID, calls.get(1));
      assertNull(mgr.getTransaction());

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());


      GlobalTransaction remoteGtx = new GlobalTransaction();

      remoteGtx.setAddress(new DummyAddress());
      //hack the method call to make it have the remote globalTransaction

      command.setGlobalTransaction(remoteGtx);
      //call our remote method
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(remoteGtx, injectDataVersion(entry.getModifications()), (Address) remoteGtx.getAddress(), Boolean.FALSE);
      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
      }
      catch (Throwable t)
      {
         fail();
      }

      //our thread should still be null
      assertNull(mgr.getTransaction());

      //there should be a registration for the remote globalTransaction
      assertNotNull(table.get(remoteGtx));
      assertNotNull(table.getLocalTransaction(remoteGtx));

      //assert that the method has been passed up the stack
      calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(2));

      //assert we have the tx in th table
      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());
      TestingUtil.killCaches(cache);
   }

   public void testRemotePrepareSuspendTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();


      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      GlobalTransaction gtx = cache.getCurrentTransaction(tx, true);
      TransactionTable table = cache.getTransactionTable();
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);
      assertEquals(tx, mgr.getTransaction());

      //now send the remote prepare

      GlobalTransaction remoteGtx = new GlobalTransaction();

      remoteGtx.setAddress(new DummyAddress());
      //hack the method call to make it have the remote globalTransaction
      WriteCommand command = entry.getModifications().get(0);
      command.setGlobalTransaction(remoteGtx);
      //call our remote method
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(remoteGtx, injectDataVersion(entry.getModifications()), (Address) remoteGtx.getAddress(), Boolean.FALSE);
      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail();
      }

      //we should have the same transaction back again
      assertEquals(tx, mgr.getTransaction());

      // there should be a registration for the remote globalTransaction
      assertNotNull(table.get(remoteGtx));
      assertNotNull(table.getLocalTransaction(remoteGtx));


      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));

      //assert we have two current transactions
      assertEquals(2, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(2, cache.getTransactionTable().getNumLocalTransactions());

      //commit the local tx
      mgr.commit();

      //check local calls
      calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(1));
      assertEquals(CommitCommand.METHOD_ID, calls.get(2));

      //assert we have only 1 transaction left

      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());

      assertNotNull(table.get(remoteGtx));
      assertNotNull(table.getLocalTransaction(remoteGtx));

      assertNull(table.get(gtx));
      assertNull(table.getLocalTransaction(gtx));
      //assert we are no longer associated
      assertEquals(null, mgr.getTransaction());
      TestingUtil.killCaches(cache);
   }

   public void testRemoteCommitSuspendTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      //this sets
      cache.getCurrentTransaction(tx, true);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      GlobalTransaction gtx = cache.getCurrentTransaction(tx, true);
      TransactionTable table = cache.getTransactionTable();
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);
      assertEquals(tx, mgr.getTransaction());

      //now send the remote prepare

      GlobalTransaction remoteGtx = new GlobalTransaction();

      remoteGtx.setAddress(new DummyAddress());
      //hack the method call to make it have the remote globalTransaction
      WriteCommand command = entry.getModifications().get(0);
      command.setGlobalTransaction(remoteGtx);
      //call our remote method
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(remoteGtx, injectDataVersion(entry.getModifications()), (Address) remoteGtx.getAddress(), Boolean.FALSE);
      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
      }
      catch (Throwable t)
      {
         fail();
      }

      assertEquals(2, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(2, cache.getTransactionTable().getNumLocalTransactions());

//		    call our remote method
      CommitCommand commitMethod = new CommitCommand(remoteGtx);
      try
      {
         TestingUtil.replicateCommand(cache, commitMethod);
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail();
      }

      //we should have the same transaction back again
      assertEquals(tx, mgr.getTransaction());

      //	 there should be a registration for the remote globalTransaction
      assertNull(table.get(remoteGtx));
      assertNull(table.getLocalTransaction(remoteGtx));

      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(CommitCommand.METHOD_ID, calls.get(1));

      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());

      //commit the local tx
      mgr.commit();

      calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(2));
      assertEquals(CommitCommand.METHOD_ID, calls.get(3));


      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertEquals(null, mgr.getTransaction());
      TestingUtil.killCaches(cache);
   }

   public void testRemoteRollbackSuspendTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      //this sets
      cache.getCurrentTransaction(tx, true);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      GlobalTransaction gtx = cache.getCurrentTransaction(tx, true);
      TransactionTable table = cache.getTransactionTable();
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);
      assertEquals(tx, mgr.getTransaction());

      //now send the remote prepare

      GlobalTransaction remoteGtx = new GlobalTransaction();

      remoteGtx.setAddress(new DummyAddress());
      //hack the method call to make it have the remote globalTransaction
      WriteCommand command = entry.getModifications().get(0);
      command.setGlobalTransaction(remoteGtx);
      //call our remote method
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(remoteGtx, injectDataVersion(entry.getModifications()), (Address) remoteGtx.getAddress(), Boolean.FALSE);
      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
      }
      catch (Throwable t)
      {
         fail();
      }

      assertEquals(2, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(2, cache.getTransactionTable().getNumLocalTransactions());

//		    call our remote method
      RollbackCommand rollbackCommand = new RollbackCommand(remoteGtx);
      try
      {
         TestingUtil.replicateCommand(cache, rollbackCommand);
      }
      catch (Throwable t)
      {
         fail();
      }

      //we should have the same transaction back again
      assertEquals(tx, mgr.getTransaction());

      //	 there should be a registration for the remote globalTransaction
      assertNull(table.get(remoteGtx));
      assertNull(table.getLocalTransaction(remoteGtx));

      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(RollbackCommand.METHOD_ID, calls.get(1));

      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());

      //commit the local tx
      mgr.commit();

      calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(2));
      assertEquals(CommitCommand.METHOD_ID, calls.get(3));


      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertEquals(null, mgr.getTransaction());
      TestingUtil.killCaches(cache);
   }

   public void testRemoteCommitTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      //this sets
      cache.getCurrentTransaction(tx, true);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      GlobalTransaction gtx = cache.getCurrentTransaction(tx, true);
      TransactionTable table = cache.getTransactionTable();
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);
      assertNotNull(mgr.getTransaction());
      WriteCommand command = entry.getModifications().get(0);
      mgr.commit();

      //test local calls
      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(CommitCommand.METHOD_ID, calls.get(1));

      assertNull(mgr.getTransaction());

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());


      GlobalTransaction remoteGtx = new GlobalTransaction();

      remoteGtx.setAddress(new DummyAddress());

//	    hack the method call to make it have the remote globalTransaction

      command.setGlobalTransaction(remoteGtx);
      //call our remote method
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(remoteGtx, injectDataVersion(entry.getModifications()), (Address) remoteGtx.getAddress(), Boolean.FALSE);
      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
      }
      catch (Throwable t)
      {
         fail();
      }

      //our thread should be null
      assertNull(mgr.getTransaction());
      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());

      //	 there should be a registration for the remote globalTransaction
      assertNotNull(table.get(remoteGtx));
      assertNotNull(table.getLocalTransaction(remoteGtx));
      //this is not populated until replication interceptor is used
//      assertEquals(1, table.get(remoteGtx).getModifications().size());

      calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(2));

      assertNull(mgr.getTransaction());
//	    call our remote method
      CommitCommand commitCommand = new CommitCommand(remoteGtx);
      try
      {
         TestingUtil.replicateCommand(cache, commitCommand);
      }
      catch (Throwable t)
      {
         fail();
      }

      assertNull(table.get(remoteGtx));
      assertNull(table.getLocalTransaction(remoteGtx));

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());
      assertNull(mgr.getTransaction());
      TestingUtil.killCaches(cache);

   }

   public void testRemoteRollbackTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      //start local transaction
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      //this sets
      cache.getCurrentTransaction(tx, true);

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      GlobalTransaction gtx = cache.getCurrentTransaction(tx, true);
      TransactionTable table = cache.getTransactionTable();
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);
      assertNotNull(mgr.getTransaction());
      WriteCommand command = entry.getModifications().get(0);
      mgr.commit();

      //test local calls
      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(CommitCommand.METHOD_ID, calls.get(1));

      GlobalTransaction remoteGtx = new GlobalTransaction();

      remoteGtx.setAddress(new DummyAddress());

      command.setGlobalTransaction(remoteGtx);
      //call our remote method
      OptimisticPrepareCommand prepareCommand = new OptimisticPrepareCommand(remoteGtx, injectDataVersion(entry.getModifications()), (Address) remoteGtx.getAddress(), Boolean.FALSE);
      try
      {
         TestingUtil.replicateCommand(cache, prepareCommand);
      }
      catch (Throwable t)
      {
         fail();
      }

      //our thread should be null
      assertNull(mgr.getTransaction());

      //	 there should be a registration for the remote globalTransaction
      assertNotNull(table.get(remoteGtx));
      assertNotNull(table.getLocalTransaction(remoteGtx));
      //this is not populated until replication interceptor is used
//      assertEquals(1, table.get(remoteGtx).getModifications().size());

      calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(2));

//	    call our remote method
      RollbackCommand rollbackCommand = new RollbackCommand(remoteGtx);
      try
      {
         TestingUtil.replicateCommand(cache, rollbackCommand);
      }
      catch (Throwable t)
      {
         fail();
      }

      calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(2));
      assertEquals(RollbackCommand.METHOD_ID, calls.get(3));

      assertNull(table.get(remoteGtx));
      assertNull(table.getLocalTransaction(remoteGtx));

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());
      TestingUtil.killCaches(cache);

   }


   public void testSequentialTransactionExists() throws Exception
   {

      CacheSPI<Object, Object> cache = createCache();
      MockInterceptor dummy = new MockInterceptor();
      setAlteredInterceptorChain(dummy, cache);


      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      mgr.begin();
      Transaction tx = mgr.getTransaction();
      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertNotNull(mgr.getTransaction());
      mgr.commit();


      mgr.begin();
      Transaction tx1 = mgr.getTransaction();
      assertNotNull(tx1);
      assertNotSame(tx, tx1);
      cache.put("/one/two", "key1", pojo);
      mgr.commit();


      List<?> calls = dummy.getAllCalledIds();
      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(0));
      assertEquals(CommitCommand.METHOD_ID, calls.get(1));

      assertEquals(OptimisticPrepareCommand.METHOD_ID, calls.get(2));
      assertEquals(CommitCommand.METHOD_ID, calls.get(3));
      TestingUtil.killCaches(cache);
   }

}
