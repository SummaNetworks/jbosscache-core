/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.transaction.pessimistic;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.lock.LockManager;
import static org.jboss.cache.lock.LockType.READ;
import static org.jboss.cache.lock.LockType.WRITE;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tests transactional access to a local CacheImpl.
 * Note: we use DummpyTranasctionManager to replace jta
 *
 * @version $Id: PessimisticTransactionTest.java 7451 2009-01-12 11:38:59Z mircea.markus $
 */
@Test(groups = {"functional", "transaction"}, sequential = true, testName = "transaction.pessimistic.PessimisticTransactionTest")
public class PessimisticTransactionTest
{
   CacheSPI<String, Comparable> cache = null;
   UserTransaction tx = null;
   Exception exception;
   LockManager lockManager;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {

      UnitTestCacheFactory<String, Comparable> instance = new UnitTestCacheFactory<String, Comparable>();
      cache = (CacheSPI<String, Comparable>) instance.createCache(false, getClass());
      cache.getConfiguration().setClusterName("test");
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.getConfiguration().setStateRetrievalTimeout(10000);
      cache.getConfiguration().setIsolationLevel(IsolationLevel.SERIALIZABLE);
      cache.getConfiguration().setLockParentForChildInsertRemove(true);// this test case is written to assume this.
      cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      tx = TransactionSetup.getUserTransaction();

      cache.create();
      cache.start();
      exception = null;
      lockManager = TestingUtil.extractLockManager(cache);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }

      // BW. kind of a hack to destroy jndi binding and thread local tx before next run.
      Transaction c = TransactionSetup.getManager().getTransaction();
      if (c != null) c.rollback();

      if (tx != null)
      {
         try
         {
            tx.rollback();
         }
         catch (Throwable t)
         {
            // do nothing
         }
         tx = null;
      }
   }

   public void testPutTx() throws Exception
   {
      tx.begin();
      cache.put("/a/b/c", "age", 38);
      // the tx interceptor should know that we're in the same tx.
      assertEquals(cache.get("/a/b/c", "age"), 38);

      cache.put("/a/b/c", "age", 39);
      tx.commit();

      // This test is done outside the TX, it wouldn't work if someone else
      // modified "age". This works because we're the only TX running.
      assertEquals(cache.get("/a/b/c", "age"), 39);
   }

   public void testRollbackTx1()
   {
      try
      {
         tx.begin();
         cache.put("/a/b/c", "age", 38);
         cache.put("/a/b/c", "age", 39);
         tx.rollback();

         // This test is done outside the TX, it wouldn't work if someone else
         // modified "age". This works because we're the only TX running.
         assertNull(cache.get("/a/b/c", "age"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testGetAfterRemovalRollback() throws Exception
   {
      assertEquals(0, cache.getNumberOfLocksHeld());
      cache.put("/a/b", null);
      assertEquals(0, cache.getNumberOfLocksHeld());
      assertTrue(cache.exists("/a/b"));
      tx.begin();
      cache.removeNode("/a/b");
      assertFalse(cache.exists("/a/b"));
      tx.rollback();
      assertTrue(cache.exists("/a/b"));
      assertEquals(0, cache.getNumberOfLocksHeld());
      // new tx in new thread
      Thread th = new Thread()
      {
         public void run()
         {
            try
            {
               cache.getTransactionManager().begin();
               assertNotNull(cache.getNode("/a/b"));
               cache.getTransactionManager().rollback();
            }
            catch (Exception e)
            {
               e.printStackTrace();
               fail("Caught exception");
            }
         }
      };

      th.start();
      th.join();

      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testRollbackTx2()
   {
      try
      {
         tx.begin();
         cache.put("/a/b/c", "age", 38);
         cache.remove("/a/b/c", "age");
         tx.rollback();

         // This test is done outside the TX, it wouldn't work if someone else
         // modified "age". This works because we're the only TX running.
         assertNull(cache.get("/a/b/c", "age"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testRollbackTx2a()
   {
      try
      {
         cache.put("/a/b/c", "age", 38);
         tx.begin();
         cache.remove("/a/b/c", "age");
         tx.rollback();

         // This test is done outside the TX, it wouldn't work if someone else
         // modified "age". This works because we're the only TX running.
         assertEquals(38, cache.get("/a/b/c", "age"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testRollbackTx3()
   {
      try
      {
         java.util.Map<String, Comparable> map1 = new java.util.HashMap<String, Comparable>();
         map1.put("age", 38);
         java.util.Map<String, Comparable> map2 = new java.util.HashMap<String, Comparable>();
         map2.put("age", 39);
         tx.begin();
         cache.put("/a/b/c", map1);
         cache.put("/a/b/c", map2);
         tx.rollback();

         // This test is done outside the TX, it wouldn't work if someone else
         // modified "age". This works because we're the only TX running.
         assertNull(cache.get("/a/b/c", "age"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testRollbackTx4()
   {
      try
      {
         Map<String, Comparable> map = new HashMap<String, Comparable>();
         map.put("age", 38);
         tx.begin();
         cache.put("/a/b/c", map);
         cache.removeNode("/a/b/c");
         tx.rollback();

         // This test is done outside the TX, it wouldn't work if someone else
         // modified "age". This works because we're the only TX running.
         assertNull(cache.get("/a/b/c", "age"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testNodeCreationRollback()
   {
      try
      {
         tx.begin();
         cache.put("/bela/ban", "key", "value");
         tx.rollback();

         assertNull("node should be not existent", cache.getNode("/bela/ban"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testNodeCreationRollback2()
   {
      try
      {
         cache.put("/bela/ban", null);
         tx.begin();
         cache.put("/bela/ban/michelle", null);
         tx.rollback();
         assertNotNull("node should be not null", cache.getNode("/bela/ban"));
         assertNull("node should be not existent", cache.getNode("/bela/ban/michelle"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testNodeDeletionRollback()
   {
      try
      {
         cache.put("/a/b/c", null);
         tx.begin();
         cache.removeNode("/a/b/c");
         assertNull(cache.getNode("/a/b/c"));
         cache.removeNode("/a/b");
         assertNull(cache.getNode("/a/b"));
         cache.removeNode("/a");
         assertNull(cache.getNode("/a"));
         tx.rollback();
         assertNotNull(cache.getNode("/a/b/c"));
         assertNotNull(cache.getNode("/a/b"));
         assertNotNull(cache.getNode("/a"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testNodeDeletionRollback2() throws Exception
   {
      cache.put("/a/b/c", null);
      cache.put("/a/b/c1", null);
      cache.put("/a/b/c2", null);
      tx.begin();
      cache.removeNode("/a");
      assertNull(cache.getNode("/a/b/c"));
      assertNull(cache.getNode("/a/b/c1"));
      assertNull(cache.getNode("/a/b/c2"));
      assertNull(cache.getNode("/a/b"));
      assertNull(cache.getNode("/a"));
      Set children = cache.getChildrenNames(Fqn.fromString("/a/b"));
      assertTrue(children.isEmpty());
      children = cache.getChildrenNames("/a");
      assertTrue(children.isEmpty());
      tx.rollback();
      assertNotNull(cache.getNode("/a"));
      assertNotNull(cache.getNode("/a/b"));
      assertNotNull(cache.getNode("/a/b/c"));
      assertNotNull(cache.getNode("/a/b/c1"));
      assertNotNull(cache.getNode("/a/b/c2"));
      children = cache.getChildrenNames(Fqn.fromString("/a/b"));
      assertEquals(3, children.size());
   }

   public void testNodeCreation() throws Exception
   {
      GlobalTransaction gtx;
      cache.put("/a/b", null);
      tx.begin();
      gtx = cache.getCurrentTransaction();
      cache.put("/a/b/c", null);
      assertLocked(gtx, "/a", false);
      assertLocked(gtx, "/a/b", true);
      assertLocked(gtx, "/a/b/c", true);
   }

   public void testNodeCreation2() throws Exception
   {
      GlobalTransaction gtx;
      tx.begin();
      gtx = cache.getCurrentTransaction();
      cache.put("/a/b/c", null);
      assertLocked(gtx, "/a", true);
      assertLocked(gtx, "/a/b", true);
      assertLocked(gtx, "/a/b/c", true);
   }

   public void testNodeRemoval() throws SystemException, NotSupportedException
   {
      GlobalTransaction gtx;
      cache.put("/a/b/c", null);
      tx.begin();
      gtx = cache.getCurrentTransaction();
      cache.removeNode("/a/b/c");// need to remove the node, not just the data in the node.
      assertLocked(gtx, "/a", false);
      assertLocked(gtx, "/a/b", true);
      assertLocked(gtx, "/a/b/c", true);
      tx.rollback();
   }

   public void testNodeRemoval2() throws SystemException, NotSupportedException
   {
      GlobalTransaction gtx;
      cache.put("/a/b/c", null);
      tx.begin();
      gtx = cache.getCurrentTransaction();
      cache.removeNode("/a/b");// need to remove the node, not just the data in the node.
      assertLocked(gtx, "/a", true);
      assertLocked(gtx, "/a/b", true);
      assertLocked(gtx, "/a/b/c", true);
      tx.rollback();
   }

   public void testIntermediateNodeCreationOnWrite() throws Exception
   {
      cache.put("/a", null);
      tx.begin();
      cache.put("/a/b/c", null);
      // expecting WLs on /a, /a/b and /a/b/c.
      GlobalTransaction gtx = cache.getCurrentTransaction();
      assertLocked(gtx, "/a", true);
      assertLocked(gtx, "/a/b", true);
      assertLocked(gtx, "/a/b/c", true);
      tx.rollback();
   }

   public void testIntermediateNodeCreationOnRead() throws Exception
   {
      cache.put("/a", null);
      tx.begin();
      cache.getNode("/a/b/c");

      // expecting RLs on /, /a
      // /a/b, /a/b/c should NOT be created!
      GlobalTransaction gtx = cache.getCurrentTransaction();
      assertLocked(gtx, "/", false);
      assertLocked(gtx, "/a", false);
      assertNull("/a/b should not exist", cache.peek(Fqn.fromString("/a/b"), true));
      assertNull("/a/b/c should not exist", cache.peek(Fqn.fromString("/a/b/c"), true));
      tx.rollback();
      assertNull("/a/b should not exist", cache.peek(Fqn.fromString("/a/b"), true));
      assertNull("/a/b/c should not exist", cache.peek(Fqn.fromString("/a/b/c"), true));

   }

   public void testIntermediateNodeCreationOnRemove() throws Exception
   {
      cache.put("/a", null);
      tx.begin();
      cache.removeNode("/a/b/c");

      // expecting RLs on /, /a
      // /a/b, /a/b/c should NOT be created!
      GlobalTransaction gtx = cache.getCurrentTransaction();
      assertLocked(gtx, "/", false);
      assertLocked(gtx, "/a", true);
      assertLocked(gtx, "/a/b", true);
      assertLocked(gtx, "/a/b/c", true);
      assertNotNull("/a/b should exist", cache.peek(Fqn.fromString("/a/b"), true));
      assertNotNull("/a/b/c should exist", cache.peek(Fqn.fromString("/a/b/c"), true));
      assertNotNull("/a/b should NOT be visible", cache.exists(Fqn.fromString("/a/b")));
      assertNotNull("/a/b/c should NOT be visible", cache.exists(Fqn.fromString("/a/b/c")));
      tx.rollback();
      assertNull("/a/b should not exist", cache.peek(Fqn.fromString("/a/b"), true));
      assertNull("/a/b/c should not exist", cache.peek(Fqn.fromString("/a/b/c"), true));

   }

   public void testNodeDeletionRollback3() throws Exception
   {
      GlobalTransaction gtx;
      cache.put("/a/b/c1", null);

      tx.begin();
      gtx = cache.getCurrentTransaction();
      cache.put("/a/b/c1", null);
      assertLocked(gtx, "/a", false);
      assertLocked(gtx, "/a/b", false);
      assertLocked(gtx, "/a/b/c1", true);

      cache.put("/a/b/c2", null);
      assertLocked(gtx, "/a/b", true);
      assertLocked(gtx, "/a/b/c2", true);

      cache.put("/a/b/c3", null);
      cache.put("/a/b/c1/one", null);
      assertLocked(gtx, "/a/b/c1", true);
      assertLocked(gtx, "/a/b/c1/one", true);

      cache.put("/a/b/c1/two", null);
      cache.put("/a/b/c1/one/1", null);
      assertLocked(gtx, "/a/b/c1", true);
      assertLocked(gtx, "/a/b/c1/one", true);
      assertLocked(gtx, "/a/b/c1/one/1", true);

      cache.put("/a/b/c1/two/2/3/4", null);
      assertLocked(gtx, "/a/b/c1", true);
      assertLocked(gtx, "/a/b/c1/two", true);
      assertLocked(gtx, "/a/b/c1/two/2", true);
      assertLocked(gtx, "/a/b/c1/two/2/3", true);
      assertLocked(gtx, "/a/b/c1/two/2/3/4", true);

      cache.removeNode("/a/b");
      tx.rollback();
      assertTrue(cache.getChildrenNames("/a/b/c1").isEmpty());
      Set cn = cache.getChildrenNames(Fqn.fromString("/a/b"));
      assertEquals(1, cn.size());
      assertEquals("c1", cn.iterator().next());
   }

   public void testDoubleLocks() throws Exception
   {
      tx.begin();
      GlobalTransaction gtx = cache.getCurrentTransaction();
      cache.put("/a/b/c", null);
      cache.put("/a/b/c", null);

      NodeSPI n = cache.getNode("/a");
      assert !lockManager.isLocked(n, READ);
      // make sure this is write locked.
      assertLocked(gtx, "/a", true);

      n = cache.getNode("/a/b");
      assert !lockManager.isLocked(n, READ);
      // make sure this is write locked.
      assertLocked(gtx, "/a/b", true);

      n = cache.getNode("/a/b/c");
      assert !lockManager.isLocked(n, READ);
      // make sure this is write locked.
      assertLocked(gtx, "/a/b/c", true);

      tx.rollback();
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   private void assertLocked(Object owner, String fqn, boolean writeLocked)
   {
      NodeSPI<String, Comparable> n = cache.peek(Fqn.fromString(fqn), true);
      if (owner == null) owner = Thread.currentThread();
      assertTrue("node " + fqn + " is not locked", lockManager.isLocked(n));
      if (writeLocked)
      {
         assertTrue("node " + fqn + " is not write-locked by owner " + owner + ". Lock details: " + lockManager.printLockInfo(n), lockManager.ownsLock(Fqn.fromString(fqn), WRITE, owner));
      }
      else
      {
         assertTrue("node " + fqn + " is not read-locked by owner " + owner + ". Lock details: " + lockManager.printLockInfo(n), lockManager.ownsLock(Fqn.fromString(fqn), READ, owner));
      }
   }

   public void testConcurrentNodeAccessOnRemovalWithTx() throws Exception
   {
      cache.put("/a/b/c", null);
      tx.begin();
      cache.removeNode("/a/b/c");
      // this node should now be locked.
      TransactionManager tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      Transaction t = tm.suspend();
      // start a new tx
      tm.begin();
      try
      {
         cache.getNode("/a/b/c");// should fail
         fail("Should not be able to get a hold of /a/b/c until the deleting tx completes");
      }
      catch (Exception e)
      {
         // expected
         //cache.getTransactionManager().commit();
         tm.commit();
      }

      tm.resume(t);
      tx.rollback();

      assertNotNull(cache.getNode("/a/b/c"));
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testConcurrentNodeAccessOnRemovalWithoutTx() throws Exception
   {
      cache.put("/a/b/c", null);
      tx.begin();
      cache.removeNode("/a/b/c");
      // this node should now be locked.
      Transaction t = cache.getTransactionManager().suspend();
      Thread th = new Thread()
      {
         public void run()
         {
            try
            {
               cache.getNode("/a/b/c");// should fail

               fail("Should not be able to get a hold of /a/b/c until the deleting tx completes");
            }
            catch (Exception e)
            {
               // expected
            }
         }
      };

      th.start();
      th.join();

      cache.getTransactionManager().resume(t);
      tx.rollback();

      assertNotNull(cache.getNode("/a/b/c"));
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testRemove() throws CacheException, SystemException, NotSupportedException, HeuristicMixedException, HeuristicRollbackException, RollbackException
   {
      cache.put("/a/b/c", null);
      cache.put("/a/b/c/1", null);
      cache.put("/a/b/c/2", null);
      cache.put("/a/b/c/3", null);
      cache.put("/a/b/c/3/a/b/c", null);

      assertEquals(0, cache.getNumberOfLocksHeld());

      tx.begin();
      cache.removeNode("/a/b/c");
      // this used to test for 2 locks held.  After the fixes for JBCACHE-875 however, 2 more locks are acquired - for the root node as well as the deleted node.
      // and since we would lock all children of the deleted node as well, we have 10 locks here.
      assertEquals(10, cache.getNumberOfLocksHeld());
      tx.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testRemoveAndRollback() throws CacheException, SystemException, NotSupportedException, HeuristicMixedException, HeuristicRollbackException,
         RollbackException
   {
      cache.put("/a/b/c", null);
      cache.put("/a/b/c/1", null);
      cache.put("/a/b/c/2", null);
      cache.put("/a/b/c/3", null);
      cache.put("/a/b/c/3/a/b/c", null);

      assertEquals(0, cache.getNumberOfLocksHeld());

      tx.begin();
      cache.removeNode("/a/b/c");
      assertEquals(10, cache.getNumberOfLocksHeld());
      tx.rollback();
      assertEquals(0, cache.getNumberOfLocksHeld());

      assertTrue(cache.exists("/a/b/c"));
      assertTrue(cache.exists("/a/b/c/1"));
      assertTrue(cache.exists("/a/b/c/2"));
      assertTrue(cache.exists("/a/b/c/3"));
      assertTrue(cache.exists("/a/b/c/3/a"));
      assertTrue(cache.exists("/a/b/c/3/a/b"));
      assertTrue(cache.exists("/a/b/c/3/a/b/c"));
   }

   public void testRemoveKeyRollback() throws CacheException, SystemException, NotSupportedException
   {
      cache.put("/bela/ban", "name", "Bela");
      tx.begin();
      cache.remove("/bela/ban", "name");
      assertNull(cache.get("/bela/ban", "name"));
      tx.rollback();
      assertEquals("Bela", cache.get("/bela/ban", "name"));
   }

   public void testRemoveKeyRollback2()
   {
      try
      {
         Map<String, Comparable> m = new HashMap<String, Comparable>();
         m.put("name", "Bela");
         m.put("id", 322649);
         cache.put("/bela/ban", m);
         tx.begin();
         cache.remove("/bela/ban", "name");
         assertNull(cache.get("/bela/ban", "name"));
         tx.rollback();
         assertEquals("Bela", cache.get("/bela/ban", "name"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testRemoveKeyRollback3()
   {
      try
      {
         cache.put("/bela/ban", "name", "Bela");
         tx.begin();
         cache.put("/bela/ban", "name", "Michelle");
         cache.remove("/bela/ban", "name");
         assertNull(cache.get("/bela/ban", "name"));
         tx.rollback();
         assertEquals("Bela", cache.get("/bela/ban", "name"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testDoubleRemovalOfSameData() throws Exception
   {
      tx.begin();
      cache.put("/foo/1", "item", 1);
      assertEquals(cache.get("/foo/1", "item"), 1);
      cache.removeNode("/foo/1");
      assertNull(cache.get("/foo/1", "item"));
      cache.removeNode("/foo/1");
      assertNull(cache.get("/foo/1", "item"));
      tx.rollback();
      assertFalse(cache.exists("/foo/1"));
      assertNull(cache.get("/foo/1", "item"));
   }

   /**
    * put(Fqn, Map) with a previous null map
    */
   public void testPutDataRollback1()
   {
      try
      {
         cache.put("/bela/ban", null);// create a node /bela/ban with a null map
         tx.begin();
         Map<String, Comparable> m = new HashMap<String, Comparable>();
         m.put("name", "Bela");
         m.put("id", 322649);
         cache.put("/bela/ban", m);
         tx.rollback();

         Node n = cache.getNode("/bela/ban");
         if (n.getData() == null)
            return;
         assertEquals("map should be empty", 0, n.getData().size());
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   /**
    * put(Fqn, Map) with a previous non-null map
    */
   public void testputDataRollback2() throws Exception
   {
      Map<String, Comparable> m1, m2;
      m1 = new HashMap<String, Comparable>();
      m1.put("name", "Bela");
      m1.put("id", 322649);
      m2 = new HashMap<String, Comparable>();
      m2.put("other", "bla");
      m2.put("name", "Michelle");

      cache.put("/bela/ban", m1);
      tx.begin();

      cache.put("/bela/ban", m2);
      Map tmp = cache.getNode("/bela/ban").getData();
      assertEquals(3, tmp.size());
      assertEquals("Michelle", tmp.get("name"));
      assertEquals(tmp.get("id"), 322649);
      assertEquals("bla", tmp.get("other"));
      tx.rollback();

      tmp = cache.getNode("/bela/ban").getData();
      assertEquals(2, tmp.size());
      assertEquals("Bela", tmp.get("name"));
      assertEquals(tmp.get("id"), 322649);
   }

   public void testPutRollback()
   {
      try
      {
         cache.put("/bela/ban", null);// /bela/ban needs to exist
         tx.begin();
         cache.put("/bela/ban", "name", "Bela");
         assertEquals("Bela", cache.get("/bela/ban", "name"));
         tx.rollback();
         assertNull(cache.get("/bela/ban", "name"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testPutRollback2()
   {
      try
      {
         cache.put("/bela/ban", "name", "Bela");// /bela/ban needs to exist
         tx.begin();
         cache.put("/bela/ban", "name", "Michelle");
         assertEquals("Michelle", cache.get("/bela/ban", "name"));
         tx.rollback();
         assertEquals("Bela", cache.get("/bela/ban", "name"));
      }
      catch (Throwable t)
      {
         t.printStackTrace();
         fail(t.toString());
      }
   }

   public void testSimpleRollbackTransactions() throws Exception
   {
      final Fqn fqn = Fqn.fromString("/a/b/c");
      tx.begin();
      cache.put(fqn, "entry", "commit");
      tx.commit();

      tx.begin();
      cache.put(fqn, "entry", "rollback");
      cache.removeNode(fqn);
      tx.rollback();
      assertEquals("Node should keep the commited value", "commit", cache.getNode(fqn).get("entry"));

      tx.begin();
      cache.removeNode(fqn);
      cache.put(fqn, "entry", "rollback");
      tx.rollback();

      assertEquals("Node should keep the commited value", "commit", cache.getNode(fqn).get("entry"));// THIS FAILS
   }

   private TransactionManager startTransaction() throws Exception
   {
      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();
      return mgr;
   }

   public void testConcurrentReadAndWriteAccess() throws Exception
   {
      cache.stop();
      cache.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      cache.start();

      cache.put("/1/2/3/4", "foo", "bar");// no TX, no locks held after put() returns

      class Reader extends Thread
      {
         TransactionManager thread_tx;

         public Reader()
         {
            super("Reader");
         }

         public void run()
         {
            try
            {
               thread_tx = startTransaction();
               cache.get("/1/2/3", "foo");// acquires RLs on all 3 nodes
               sleep(2000);
               thread_tx.commit();// releases RLs
            }
            catch (Exception e)
            {
               exception = e;
            }
         }
      }

      class Writer extends Thread
      {
         TransactionManager thread_tx;

         public Writer()
         {
            super("Writer");
         }

         public void run()
         {
            try
            {
               sleep(500);// give the Reader a chance to acquire the RLs
               thread_tx = startTransaction();
               cache.put("/1", "foo", "bar2");// needs to acquired a WL on /1
               thread_tx.commit();
            }
            catch (Exception e)
            {
               exception = e;
            }
         }
      }

      Reader reader = new Reader();
      Writer writer = new Writer();
      reader.start();
      writer.start();
      reader.join();
      writer.join();
      if (exception != null)
      {
         throw exception;
      }
   }

   public void testRemoveAndGetInTx() throws Exception
   {
      Fqn A_B = Fqn.fromString("/a/b");
      Fqn A = Fqn.fromString("/a");

      cache.put(A_B, "k", "v");

      assertTrue(cache.exists(A_B));
      assertTrue(cache.exists(A));

      cache.getTransactionManager().begin();
      cache.removeNode(A);
      cache.get(A_B, "k");
      cache.getTransactionManager().commit();
   }

   public void testRemoveAndPutInTx() throws Exception
   {
      Fqn A_B = Fqn.fromString("/a/b");
      Fqn A = Fqn.fromString("/a");

      cache.put(A_B, "k", "v");

      assertTrue(cache.exists(A_B));
      assertTrue(cache.exists(A));

      cache.getTransactionManager().begin();
      cache.removeNode(A_B);
      cache.put(A_B, "k", "v2");
      cache.getTransactionManager().commit();

      assertTrue(cache.exists(A_B));
      assertTrue(cache.exists(A));

      assert cache.peek(A, true, true).isValid();
      assert cache.peek(A_B, true, true).isValid();

      assertEquals("v2", cache.get(A_B, "k"));
   }

   public void testRemoveParentAndPutInTx() throws Exception
   {
      Fqn A_B = Fqn.fromString("/a/b");
      Fqn A = Fqn.fromString("/a");

      cache.put(A_B, "k", "v");

      assertTrue(cache.exists(A_B));
      assertTrue(cache.exists(A));

      cache.getTransactionManager().begin();
      cache.removeNode(A);
      cache.put(A_B, "k", "v2");
      cache.getTransactionManager().commit();

      assertTrue(cache.exists(A_B));
      assertTrue(cache.exists(A));

      assertEquals("v2", cache.get(A_B, "k"));
   }

   public void testRemoveGrandParentAndPutInTx() throws Exception
   {
      Fqn A_B_C = Fqn.fromString("/a/b/c");
      Fqn A = Fqn.fromString("/a");

      cache.put(A_B_C, "k", "v");

      assertTrue(cache.exists(A_B_C));
      assertTrue(cache.exists(A));

      cache.getTransactionManager().begin();
      cache.removeNode(A);
      cache.put(A_B_C, "k", "v2");
      cache.getTransactionManager().commit();

      assertTrue(cache.exists(A_B_C));
      assertTrue(cache.exists(A));

      assertEquals("v2", cache.get(A_B_C, "k"));
   }

   public void testRootNodeRemoval() throws Exception
   {
      Fqn root = Fqn.ROOT;
      Fqn fqn = Fqn.fromElements(1);
      //put first time
      tx.begin();
      this.cache.put(fqn, "k", "v");
      tx.commit();

      //get works fine
      tx.begin();
      assertEquals("v", this.cache.get(fqn, "k"));
      tx.commit();

      //remove all
      tx.begin();
      this.cache.removeNode(root);
      tx.commit();

      //get returns null - ok
      //put - endless loop
      tx.begin();
      assertNull(this.cache.get(fqn, "k"));
      this.cache.put(fqn, "k", "v");
      tx.commit();
   }

   public void testNodeAdditionAfterRemoval() throws Exception
   {
      Fqn fqn = Fqn.fromString("/1/2/3/4");
      //put first time
      tx.begin();
      this.cache.put(fqn, "k", "v");
      tx.commit();

      //get works fine
      tx.begin();
      assertEquals("v", this.cache.get(fqn, "k"));
      tx.commit();

      //remove all
      tx.begin();
      this.cache.removeNode(Fqn.ROOT);
      tx.commit();

      //get returns null - ok
      //put - endless loop
      tx.begin();
      assertNull(this.cache.get(fqn, "k"));
      this.cache.put(fqn, "k", "v");
      tx.commit();
   }

   public void testRootNodeRemovalRollback() throws Exception
   {
      Fqn root = Fqn.ROOT;
      Fqn fqn = Fqn.fromRelativeElements(root, 1);
      //put first time
      tx.begin();
      this.cache.put(fqn, "k", "v");
      tx.commit();

      //get works fine
      tx.begin();
      assertEquals("v", this.cache.get(fqn, "k"));
      tx.commit();

      //remove all
      tx.begin();
      this.cache.removeNode(root);
      tx.rollback();

      assertEquals("v", this.cache.get(fqn, "k"));
   }
}
