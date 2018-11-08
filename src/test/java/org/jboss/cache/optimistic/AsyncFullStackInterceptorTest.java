package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Cache;
import org.jboss.cache.commands.write.RemoveNodeCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.loader.SamplePojo;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * @author xenephon
 */
@Test(groups = {"functional", "transaction", "optimistic"}, testName = "optimistic.AsyncFullStackInterceptorTest")
public class AsyncFullStackInterceptorTest extends AbstractOptimisticTestCase
{
   private int groupIncreaser = 0;

   public void testSingleInstanceRollback() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createAsyncReplicatedCache();

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      mgr.rollback();

      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertEquals(false, cache.exists(Fqn.fromString("/one/two")));
      assertNull(cache.getNode("/one"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);
   }

   public void testSingleInstanceDuplicateCommit() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createAsyncReplicatedCache();

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      LockManager lockManager = TestingUtil.extractLockManager(cache);
      assertNull(mgr.getTransaction());

      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      mgr.commit();

      assertNull(mgr.getTransaction());

      boolean fail = false;
      try
      {
         mgr.commit();
      }
      catch (Exception e)
      {
         fail = true;

      }

      assertEquals(true, fail);
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getNode("/one"));
      assertEquals(false, lockManager.isLocked(cache.getRoot()));
      assertEquals(false, lockManager.isLocked(cache.getNode(Fqn.fromString("/one"))));
      assertEquals(false, lockManager.isLocked(cache.getNode(Fqn.fromString("/one/two"))));
      assertNotNull(cache.getNode("/one/two"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);

   }

   public void testValidationFailCommit() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createAsyncReplicatedCache();
      LockManager lockManager = TestingUtil.extractLockManager(cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();
      Transaction tx = mgr.getTransaction();
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      mgr.suspend();

      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());

      assertNull(mgr.getTransaction());

      mgr.begin();

      SamplePojo pojo2 = new SamplePojo(22, "test2");

      cache.put("/one/two", "key1", pojo2);

      mgr.commit();

      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());

      mgr.resume(tx);

      // one-phase-commits wont throw an exception on failure.
      try
      {
         mgr.commit();
         assert false : "Expecting an exception";
      }
      catch (RollbackException expected)
      {
         // this is good
      }

      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getNode("/one"));
      assertEquals(false, lockManager.isLocked(cache.getRoot()));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one")));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one/two")));
      assertNotNull(cache.getNode("/one").getChild("two"));
      assertEquals(pojo2, cache.get(Fqn.fromString("/one/two"), "key1"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);
   }

   public void test2InstanceCommit() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createAsyncReplicatedCache();
      CacheSPI<Object, Object> cache2 = createAsyncReplicatedCache();
      ReplicationListener replListener2 = ReplicationListener.getReplicationListener(cache2);
      LockManager lockManager = TestingUtil.extractLockManager(cache);
      LockManager lockManager2 = TestingUtil.extractLockManager(cache2);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      replListener2.expect(PutKeyValueCommand.class);
      cache.put("/one/two", "key1", pojo);

      mgr.commit();
      replListener2.waitForReplicationToOccur();

      // cache asserts
      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getNode("/one"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getNode("/one"));
      assertEquals(false, lockManager.isLocked(cache.getRoot()));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one")));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one/two")));
      assertNotNull(cache.getNode("/one").getChild("two"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

      // cache2 asserts
      assertEquals(0, cache2.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache2.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache2.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache2.getRoot().getChild("one"));
      assertNotNull(cache2.get(Fqn.fromString("/one/two"), "key1"));

      assertTrue(cache2.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache2.getRoot().getChild("one"));
      assertEquals(false, lockManager2.isLocked(cache2.getRoot()));
      assertEquals(false, lockManager2.isLocked(cache2.getNode("/one")));
      assertEquals(false, lockManager2.isLocked(cache2.getNode("/one/two")));
      assertNotNull(cache2.getNode("/one").getChild("two"));
      assertNotNull(cache2.get(Fqn.fromString("/one/two"), "key1"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);
   }

   public void test2InstanceRemove() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createAsyncReplicatedCache();
      CacheSPI<Object, Object> cache2 = createAsyncReplicatedCache();
      ReplicationListener replListener2 = ReplicationListener.getReplicationListener(cache2);
      LockManager lockManager = TestingUtil.extractLockManager(cache);
      LockManager lockManager2 = TestingUtil.extractLockManager(cache2);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      replListener2.expect(PutKeyValueCommand.class);
      cache.put("/one/two", "key1", pojo);

      mgr.commit();
      replListener2.waitForReplicationToOccur(1000);

      // cache asserts
      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getNode("/one"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getNode("/one"));
      assertEquals(false, lockManager.isLocked(cache.getRoot()));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one")));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one/two")));
      assertNotNull(cache.getNode("/one").getChild("two"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

      // cache2 asserts
      assertEquals(0, cache2.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache2.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache2.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache2.getRoot().getChild("one"));
      assertNotNull(cache2.get(Fqn.fromString("/one/two"), "key1"));

      assertTrue(cache2.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache2.getRoot().getChild("one"));
      assertEquals(false, lockManager2.isLocked(cache2.getRoot()));
      assertEquals(false, lockManager2.isLocked(cache2.getNode("/one")));
      assertEquals(false, lockManager2.isLocked(cache2.getNode("/one/two")));
      assertNotNull(cache2.getNode("/one").getChild("two"));
      assertNotNull(cache2.get(Fqn.fromString("/one/two"), "key1"));

      replListener2.expect(RemoveNodeCommand.class);
      replListener2.expect();
      cache.removeNode("/one/two");
      replListener2.waitForReplicationToOccur();

      assertEquals(false, cache.exists("/one/two"));
      assertEquals(null, cache.get("/one/two", "key1"));
      assertEquals(false, cache2.exists("/one/two"));
      assertEquals(null, cache2.get("/one/two", "key1"));
     TestingUtil.killCaches((Cache<Object, Object>) cache);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);
   }

   public void testValidationFailCommit2Instances() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createAsyncReplicatedCache();
      ReplicationListener replListener = ReplicationListener.getReplicationListener(cache);
      CacheSPI<Object, Object> cache2 = createAsyncReplicatedCache();
      LockManager lockManager = TestingUtil.extractLockManager(cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();
      Transaction tx = mgr.getTransaction();
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      mgr.suspend();

      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());

      GlobalTransaction gtx = cache.getCurrentTransaction(tx, true);
      TransactionTable table = cache.getTransactionTable();
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table.get(gtx);

      assertEquals(3, entry.getTransactionWorkSpace().getNodes().size());
      assertNull(mgr.getTransaction());

      mgr.begin();

      SamplePojo pojo2 = new SamplePojo(22, "test2");

      cache2.put("/one/two", "key1", pojo2);

      mgr.commit();

      // let async calls propagate
      TestingUtil.sleepThread((long) 1000);

      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());

      mgr.resume(tx);

      try
      {
         mgr.commit();
         assert false : "Expecting an exception";
      }
      catch (RollbackException expected)
      {
         // this is good
      }

      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertEquals(0, entry.getTransactionWorkSpace().getNodes().size());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getNode("/one"));
      assertEquals(false, lockManager.isLocked(cache.getRoot()));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one")));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one/two")));
      assertNotNull(cache.getNode("/one").getChild("two"));
      assertEquals(pojo2, cache.get(Fqn.fromString("/one/two"), "key1"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);

   }

   protected CacheSPI<Object, Object> createAsyncReplicatedCache() throws Exception
   {
      return createReplicatedCache("temp" + groupIncreaser, Configuration.CacheMode.REPL_ASYNC);
   }

}
