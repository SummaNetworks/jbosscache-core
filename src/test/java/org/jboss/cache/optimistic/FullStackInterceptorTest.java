package org.jboss.cache.optimistic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Cache;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.loader.SamplePojo;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Set;

/**
 * @author xenephon
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.FullStackInterceptorTest")
public class FullStackInterceptorTest extends AbstractOptimisticTestCase
{

   private Log log = LogFactory.getLog(FullStackInterceptorTest.class);

   private int groupIncreaser = 0;

   public void testLocalTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCacheWithListener();

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      mgr.commit();

      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));
      // flesh this out a bit more

     TestingUtil.killCaches((Cache<Object, Object>) cache);

   }

   public void testNoLocalTransaction() throws Exception
   {
      TestListener listener = new TestListener();

      CacheSPI<Object, Object> cache = createCacheWithListener(listener);
      LockManager lockManager = TestingUtil.extractLockManager(cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
      assertEquals(false, lockManager.isLocked(cache.getRoot()));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one")));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one/two")));
      assertNotNull(cache.getNode("/one").getChild("two"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

      assertEquals(2, listener.getNodesAdded());

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
     TestingUtil.killCaches((Cache<Object, Object>) cache);

   }

   public void testSingleInstanceCommit() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createCacheWithListener();
      LockManager lockManager = TestingUtil.extractLockManager(cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      mgr.commit();

      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
      assertEquals(false, lockManager.isLocked(cache.getRoot()));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one")));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one/two")));
      assertNotNull(cache.getNode("/one").getChild("two"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));
     TestingUtil.killCaches((Cache<Object, Object>) cache);

   }

   public void testSingleInstanceRollback() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createSyncReplicatedCache();

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
      assertNull(cache.getRoot().getChild("one"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);

   }

   public void testSingleInstanceDuplicateCommit() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createSyncReplicatedCache();
      LockManager lockManager = TestingUtil.extractLockManager(cache);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
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
      assertNotNull(cache.getRoot().getChild("one"));
      assertEquals(false, lockManager.isLocked(cache.getRoot()));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one")));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one/two")));
      assertNotNull(cache.getNode("/one").getChild("two"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);

   }

   public void testValidationFailCommit() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createSyncReplicatedCache();
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

      boolean fail = false;
      try
      {
         mgr.commit();
      }
      catch (Exception e)
      {
         fail = true;

      }
      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertEquals(true, fail);

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
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
      CacheSPI<Object, Object> cache = createSyncReplicatedCache();
      CacheSPI<Object, Object> cache2 = createSyncReplicatedCache();
      LockManager lockManager = TestingUtil.extractLockManager(cache);
      LockManager lockManager2 = TestingUtil.extractLockManager(cache2);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      mgr.commit();

      // cache asserts
      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
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
      CacheSPI<Object, Object> cache = createSyncReplicatedCache();
      CacheSPI<Object, Object> cache2 = createSyncReplicatedCache();
      LockManager lockManager = TestingUtil.extractLockManager(cache);
      LockManager lockManager2 = TestingUtil.extractLockManager(cache2);

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      mgr.commit();

      // cache asserts
      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
      assertNotNull(cache.get(Fqn.fromString("/one/two"), "key1"));

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
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

      cache.removeNode("/one/two");

      assertEquals(false, cache.exists("/one/two"));
      assertEquals(false, cache2.exists("/one/two"));

      assertEquals(null, cache.get("/one/two", "key1"));
      assertEquals(null, cache2.get("/one/two", "key1"));
     TestingUtil.killCaches((Cache<Object, Object>) cache);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);
   }

   public void testValidationFailCommit2Instances() throws Exception
   {
      groupIncreaser++;
      CacheSPI<Object, Object> cache = createSyncReplicatedCache();
      CacheSPI<Object, Object> cache2 = createSyncReplicatedCache();
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
      OptimisticTransactionContext entry = (OptimisticTransactionContext) table
            .get(gtx);


      assertEquals(3, entry.getTransactionWorkSpace().getNodes().size());
      assertNull(mgr.getTransaction());

      mgr.begin();

      SamplePojo pojo2 = new SamplePojo(22, "test2");

      cache2.put("/one/two", "key1", pojo2);

      mgr.commit();

      assertEquals(1, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(1, cache.getTransactionTable().getNumLocalTransactions());

      mgr.resume(tx);

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
      assertNull(mgr.getTransaction());
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      assertEquals(0, entry.getTransactionWorkSpace().getNodes().size());

      assertTrue(cache.exists(Fqn.fromString("/one/two")));
      assertNotNull(cache.getRoot().getChild("one"));
      assertEquals(false, lockManager.isLocked(cache.getRoot()));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one")));
      assertEquals(false, lockManager.isLocked(cache.getNode("/one/two")));
      assertNotNull(cache.getNode("/one").getChild("two"));
      assertEquals(pojo2, cache.get(Fqn.fromString("/one/two"), "key1"));

     TestingUtil.killCaches((Cache<Object, Object>) cache);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);

   }

   public void testGetKeyValIsolationTransaction() throws Exception
   {
      SamplePojo pojo1 = new SamplePojo(21, "test-1");
      SamplePojo pojo2 = new SamplePojo(21, "test-2");

      CacheSPI<Object, Object> cache = createCacheWithListener();

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(mgr.getTransaction());

      // first put in a value
      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      cache.put("/one/two", "key1", pojo1);

      mgr.commit();

      mgr.begin();
      Transaction tx = mgr.getTransaction();
      assertEquals(pojo1, cache.get("/one/two", "key1"));

      // start another
      mgr.suspend();

      mgr.begin();
      cache.put("/one/two", "key2", pojo2);

      // assert we can see this INSIDE the existing tx
      //assertEquals(pojo2, cache.get("/one/two", "key2"));

      mgr.commit();

      // assert we can see this outside the existing tx
      assertEquals(pojo2, cache.get("/one/two", "key2"));
      // resume the suspended one
      mgr.resume(tx);
      // assert we can't see the change from tx2 as we already touched the node
      assertEquals(null, cache.get("/one/two", "key2"));
      mgr.commit();
     TestingUtil.killCaches((Cache<Object, Object>) cache);
   }

   public void testGetKeysIsolationTransaction() throws Exception
   {

      CacheSPI<Object, Object> cache = createCacheWithListener();

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      if (mgr.getTransaction() != null) mgr.rollback();
      assertNull(mgr.getTransaction());

      // first put in a value
      mgr.begin();

      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());

      SamplePojo pojo = new SamplePojo(21, "test");

      cache.put("/one/two", "key1", pojo);

      mgr.commit();

      mgr.begin();
      Transaction tx = mgr.getTransaction();
      assertEquals(1, cache.getNode("/one/two").getKeys().size());
      // start another
      mgr.suspend();

      mgr.begin();
      cache.put("/one/two", "key2", pojo);

      mgr.commit();

      // assert we can see this outsode the existing tx
      assertEquals(2, cache.getNode("/one/two").getKeys().size());

      // resume the suspended one
      mgr.resume(tx);
      // assert we can't see thge change from tx2 as we already touched the node
      assertEquals(1, cache.getNode("/one/two").getKeys().size());
      mgr.commit();
     TestingUtil.killCaches((Cache<Object, Object>) cache);

   }


   public void testTxRollbackThroughConcurrentWrite() throws Exception
   {
      CacheSPI<Object, Object> cache = createCacheWithListener();
      Set keys;

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      if (mgr.getTransaction() != null) mgr.rollback();
      assertNull(mgr.getTransaction());

      // first put in a value
      mgr.begin();
      assertEquals(0, cache.getTransactionTable().getNumGlobalTransactions());
      assertEquals(0, cache.getTransactionTable().getNumLocalTransactions());
      cache.put("/one/two", "key1", "val1");
      mgr.commit();
      keys = cache.getNode("/one/two").getKeys();
      assertEquals(1, keys.size());

      // First TX
      mgr.begin();
      Transaction tx = mgr.getTransaction();
      cache.put("/one/two", "key2", "val2");// version for this is 1

      // start another
      mgr.suspend();

      // Second TX
      mgr.begin();
      cache.put("/one/two", "key3", "val3");
      mgr.commit();// now version is 2, attrs are key1 and key3

      // assert we can see this outside the existing tx
      keys = cache.getNode("/one/two").getKeys();
      assertEquals(2, keys.size());

      // resume the suspended one
      mgr.resume(tx);
      // assert we can't see the change from tx2 as we already touched the node
      keys = cache.getNode("/one/two").getKeys();
      assertEquals(2, keys.size());// we will see key1 and key2, but *not* key3

      // this will fail as our workspace has version 1, whereas cache has 2; TX will be rolled back
      try
      {
         mgr.commit();
         fail("TX should fail as other TX incremented version number");
      }
      catch (RollbackException rollback_ex)
      {
      }

      keys = cache.getNode("/one/two").getKeys();
      assertEquals(2, keys.size());// key1 and key2
     TestingUtil.killCaches((Cache<Object, Object>) cache);
   }

   protected CacheSPI<Object, Object> createSyncReplicatedCache() throws Exception
   {
      return createReplicatedCache("temp" + groupIncreaser, Configuration.CacheMode.REPL_SYNC);
   }

   protected CacheSPI<Object, Object> createSyncReplicatedCacheAsyncCommit() throws Exception
   {
      CacheSPI<Object, Object> cache = createReplicatedCache("temp" + groupIncreaser, Configuration.CacheMode.REPL_SYNC, false);
      cache.getConfiguration().setSyncCommitPhase(false);
      cache.getConfiguration().setSyncRollbackPhase(false);
      cache.create();
      cache.start();
      return cache;
   }

   public void testPuts() throws Exception
   {
      CacheSPI<Object, Object> cache = createCache();
      Transaction tx;

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(cache.getNode(fqn));

      mgr.begin();
      cache.put(fqn, key, value);
      assertEquals(value, cache.get(fqn, key));
      tx = mgr.getTransaction();
      mgr.suspend();

      mgr.begin();
      assertNull(cache.get(fqn, key));
      mgr.commit();

      mgr.resume(tx);
      assertEquals(value, cache.get(fqn, key));
      mgr.commit();

      assertEquals(value, cache.get(fqn, key));

   }

   public void testRemoves() throws Exception
   {
      CacheSPI<Object, Object> cache = createCache();
      Transaction tx;

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(cache.getNode(fqn));
      cache.put(fqn, key, value);
      assertEquals(value, cache.get(fqn, key));

      mgr.begin();
      assertEquals(value, cache.get(fqn, key));
      cache.removeNode(fqn);
      assertNull(cache.getNode(fqn));
      tx = mgr.getTransaction();
      mgr.suspend();

      mgr.begin();
      assertEquals(value, cache.get(fqn, key));
      mgr.commit();

      mgr.resume(tx);
      assertNull(cache.getNode(fqn));
      mgr.commit();

      assertNull(cache.getNode(fqn));
   }


   public void testRemovesBeforeGet() throws Exception
   {
      CacheSPI<Object, Object> cache = createCache();
      Transaction tx;

      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      assertNull(cache.getNode(fqn));
      cache.put(fqn, key, value);
      assertEquals(value, cache.get(fqn, key));

      mgr.begin();
      cache.removeNode(fqn);
      assertNull(cache.getNode(fqn));
      tx = mgr.getTransaction();
      mgr.suspend();

      mgr.begin();
      assertEquals(value, cache.get(fqn, key));
      mgr.commit();

      mgr.resume(tx);
      assertNull(cache.getNode(fqn));
      mgr.commit();

      assertNull(cache.getNode(fqn));
   }

   public void testLoopedPutAndGet() throws Exception
   {
      try
      {
         log.debug("Starting test");
         CacheSPI<Object, Object> cache1 = createSyncReplicatedCache();
         CacheSPI<Object, Object> cache2 = createSyncReplicatedCache();
         log.debug("Created caches");
         TransactionManager mgr = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();

         int numLoops = 5, numPuts = 5;


         log.debug("Starting " + numLoops + " loops");
         for (int i = 0; i < numLoops; i++)
         {
            log.debug(" *** in loop " + i);
            mgr.begin();
            for (int j = 0; j < numPuts; j++)
            {
               cache1.put(Fqn.fromString("/profiler/node" + i), "key" + j, "value" + j);
            }
            log.debug("*** >> Out of put loop");
            mgr.commit();
            //cache2.get(Fqn.fromString("/profiler/node" + i));
         }

        TestingUtil.killCaches((Cache<Object, Object>) cache1);
        TestingUtil.killCaches((Cache<Object, Object>) cache2);
      }
      catch (Exception e)
      {
         log.debug("Error: ", e);
         assertFalse("Threw exception!", true);
         throw e;
      }
   }

   /**
    * Tests that if synchronous commit messages are not used, the proper
    * data is returned from remote nodes after a tx that does a local
    * put returns.
    *
    * @throws Exception
    */
   @Test(enabled = false)
   // known failure - JBCACHE-1201
   public void testAsynchronousCommit() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createSyncReplicatedCacheAsyncCommit();
      CacheSPI<Object, Object> cache2 = createSyncReplicatedCacheAsyncCommit();
      // Test will pass if we set up the caches with SyncCommitPhaseTrue
//      CacheSPI<Object, Object> cache1 = createSyncReplicatedCache();
//      CacheSPI<Object, Object> cache2 = createSyncReplicatedCache();

      TransactionManager tm1 = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();

      Fqn fqn = Fqn.fromString("/test/node");
      String KEY = "key";
      String VALUE1 = "value1";

      tm1.begin();
      cache1.put(fqn, KEY, VALUE1);
      tm1.commit();

      // A simple sleep will also make this test pass
//      try { Thread.sleep(100); } catch (InterruptedException e) {}

      assertEquals("Known issue JBCACHE-1201: Correct node2 value", VALUE1, cache2.get(fqn, KEY));
      assertEquals("Correct node1 value", VALUE1, cache1.get(fqn, KEY));

     TestingUtil.killCaches((Cache<Object, Object>) cache1);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);
   }
}
