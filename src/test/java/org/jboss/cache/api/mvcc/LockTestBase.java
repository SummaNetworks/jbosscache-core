package org.jboss.cache.api.mvcc;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.lock.TimeoutException;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Collections;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 3.0
 */
@Test(groups = {"functional", "mvcc"})
public abstract class LockTestBase extends AbstractSingleCacheTest
{
   protected Fqn A = Fqn.fromString("/a");
   protected Fqn AB = Fqn.fromString("/a/b");
   protected Fqn ABC = Fqn.fromString("/a/b/c");
   protected Fqn ABCD = Fqn.fromString("/a/b/c/d");
   protected boolean repeatableRead = true;
   protected boolean lockParentForChildInsertRemove = false;

   public Cache<String, String> cache;
   public TransactionManager tm;
   public LockManager lockManager;
   public InvocationContextContainer icc;


   public CacheSPI createCache()
   {
      cache = new UnitTestCacheFactory<String, String>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.LOCAL), false, getClass());
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.MVCC);
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setIsolationLevel(repeatableRead ? IsolationLevel.REPEATABLE_READ : IsolationLevel.READ_COMMITTED);
      cache.getConfiguration().setLockParentForChildInsertRemove(lockParentForChildInsertRemove);
      // reduce lock acquisition timeout so this doesn't take forever to run
      cache.getConfiguration().setLockAcquisitionTimeout(200); // 200 ms
      cache.start();
      lockManager = TestingUtil.extractComponentRegistry(cache).getComponent(LockManager.class);
      icc = TestingUtil.extractComponentRegistry(cache).getComponent(InvocationContextContainer.class);
      tm = TestingUtil.extractComponentRegistry(cache).getComponent(TransactionManager.class);
      return (CacheSPI) cache;
   }

   protected void assertLocked(Fqn fqn)
   {
      LockAssert.assertLocked(fqn, lockManager, icc);
   }

   protected void assertNotLocked(Fqn fqn)
   {
      LockAssert.assertNotLocked(fqn, lockManager, icc);
   }

   protected void assertNoLocks()
   {
      LockAssert.assertNoLocks(lockManager, icc);
   }

   public void testLocksOnPutKeyVal() throws Exception
   {
      tm.begin();
      cache.put(AB, "k", "v");
      if (lockParentForChildInsertRemove)
         assertLocked(Fqn.ROOT);
      else
         assertNotLocked(Fqn.ROOT);
      assertLocked(A);
      assertLocked(AB);
      assertNotLocked(ABC);
      tm.commit();

      assertNoLocks();

      tm.begin();
      assert cache.get(AB, "k").equals("v");
      assertNotLocked(Fqn.ROOT);
      assertNotLocked(A);
      assertNotLocked(AB);
      assertNotLocked(ABC);
      tm.commit();

      assertNoLocks();

      tm.begin();
      cache.put(ABC, "k", "v");
      assertNotLocked(Fqn.ROOT);
      assertNotLocked(A);
      if (lockParentForChildInsertRemove)
         assertLocked(AB);
      else
         assertNotLocked(AB);
      assertLocked(ABC);
      tm.commit();

      assertNoLocks();
   }

   public void testLocksOnPutData() throws Exception
   {

      tm.begin();
      cache.put(AB, Collections.singletonMap("k", "v"));
      if (lockParentForChildInsertRemove)
         assertLocked(Fqn.ROOT);
      else
         assertNotLocked(Fqn.ROOT);
      assertLocked(A);
      assertLocked(AB);
      assertNotLocked(ABC);
      assert "v".equals(cache.get(AB, "k"));
      tm.commit();
      assert "v".equals(cache.get(AB, "k"));
      assertNoLocks();

      tm.begin();
      assert "v".equals(cache.get(AB, "k"));
      assertNotLocked(Fqn.ROOT);
      assertNotLocked(A);
      assertNotLocked(AB);
      assertNotLocked(ABC);
      tm.commit();

      assertNoLocks();

      tm.begin();
      cache.put(ABC, Collections.singletonMap("k", "v"));
      assertNotLocked(Fqn.ROOT);
      assertNotLocked(A);
      if (lockParentForChildInsertRemove)
         assertLocked(AB);
      else
         assertNotLocked(AB);
      assertLocked(ABC);
      tm.commit();

      assertNoLocks();
   }

   public void testLocksOnRemoveNode() throws Exception
   {

      // init some data on a node
      cache.put(AB, Collections.singletonMap("k", "v"));

      assert "v".equals(cache.get(AB, "k"));

      tm.begin();
      cache.removeNode(AB);
      assertLocked(AB);
      if (lockParentForChildInsertRemove)
         assertLocked(A);
      else
         assertNotLocked(A);
      assertNotLocked(Fqn.ROOT);
      tm.commit();
      assert cache.getNode(AB) == null : "Should not exist";
      assertNoLocks();
   }

   public void testLocksOnEvictNode() throws Exception
   {

      // init some data on a node
      cache.put(AB, Collections.singletonMap("k", "v"));

      assert "v".equals(cache.get(AB, "k"));

      tm.begin();
      cache.evict(AB);
      assertLocked(AB);
      if (lockParentForChildInsertRemove)
         assertLocked(A);
      else
         assertNotLocked(A);
      assertNotLocked(Fqn.ROOT);
      tm.commit();
      assert cache.getNode(AB) == null : "Should not exist";
      assertNoLocks();
   }

   public void testLocksOnEvictRecursiveNode() throws Exception
   {

      // init some data on a node
      cache.put(AB, Collections.singletonMap("k", "v"));
      cache.put(ABC, Collections.singletonMap("k", "v"));
      cache.put(ABCD, Collections.singletonMap("k", "v"));

      assert "v".equals(cache.get(AB, "k"));
      assert "v".equals(cache.get(ABC, "k"));
      assert "v".equals(cache.get(ABCD, "k"));

      tm.begin();
      cache.evict(AB, true);
      assertLocked(AB);
      assertLocked(ABC);
      assertLocked(ABCD);
      if (lockParentForChildInsertRemove)
         assertLocked(A);
      else
         assertNotLocked(A);
      assertNotLocked(Fqn.ROOT);
      tm.commit();
      assert cache.getNode(AB) == null : "Should not exist";
      assertNoLocks();
   }

   public void testLocksOnRemoveNonexistentNode() throws Exception
   {

      assert cache.getNode(AB) == null : "Should not exist";

      tm.begin();
      cache.removeNode(AB);
      assertLocked(AB);
      if (lockParentForChildInsertRemove)
         assertLocked(A);
      else
         assertNotLocked(A);
      assertNotLocked(Fqn.ROOT);
      tm.commit();
      assert cache.getNode(AB) == null : "Should not exist";
      assertNoLocks();
   }

   public void testLocksOnEvictNonexistentNode() throws Exception
   {

      assert cache.getNode(AB) == null : "Should not exist";

      tm.begin();
      cache.evict(AB);
      assertLocked(AB);
      if (lockParentForChildInsertRemove)
         assertLocked(A);
      else
         assertNotLocked(A);
      assertNotLocked(Fqn.ROOT);
      tm.commit();
      assert cache.getNode(AB) == null : "Should not exist";
      assertNoLocks();
   }

   public void testLocksOnRemoveData() throws Exception
   {

      // init some data on a node
      cache.put(AB, "k", "v");
      cache.put(AB, "k2", "v2");

      assert "v".equals(cache.get(AB, "k"));
      assert "v2".equals(cache.get(AB, "k2"));

      // remove
      tm.begin();
      Object x = cache.remove(AB, "k");
      assert x.equals("v");
      assertLocked(AB);
      assertNotLocked(A);
      assertNotLocked(Fqn.ROOT);
      tm.commit();
      assert cache.get(AB, "k") == null : "Should not exist";
      assert "v2".equals(cache.get(AB, "k2"));
      assertNoLocks();

      // clearData
      tm.begin();
      cache.clearData(AB);
      assertLocked(AB);
      assertNotLocked(A);
      assertNotLocked(Fqn.ROOT);
      tm.commit();

      assert cache.get(AB, "k") == null : "Should not exist";
      assert cache.get(AB, "k2") == null : "Should not exist";
      assertNoLocks();

      // nonexistent key
      assert cache.get(AB, "k3") == null : "Should not exist";
      tm.begin();
      cache.remove(AB, "k3");
      assertLocked(AB);
      assertNotLocked(A);
      assertNotLocked(Fqn.ROOT);
      tm.commit();
      assertNoLocks();
   }

   public void testLocksOnRemoveDataNonExistentNode() throws Exception
   {

      assert cache.getNode(AB) == null : "Should not exist";

      tm.begin();
      cache.remove(AB, "k");
      assertNotLocked(AB);
      assertNotLocked(A);
      assertNotLocked(Fqn.ROOT);
      tm.commit();
      assert cache.getNode(AB) == null : "Should not exist";
   }

   public void testReadMethods() throws Exception
   {

      cache.put(AB, "k", "v");

      tm.begin();
      assert "v".equals(cache.get(AB, "k"));
      assertNoLocks();
      tm.commit();
      assertNoLocks();

      tm.begin();
      assert cache.getData(AB).containsKey("k");
      assertNoLocks();
      tm.commit();
      assertNoLocks();

      tm.begin();
      assert cache.getKeys(AB).contains("k");
      assertNoLocks();
      tm.commit();
      assertNoLocks();

      tm.begin();
      assert cache.getNode(AB) != null;
      assertNoLocks();
      tm.commit();
      assertNoLocks();

      tm.begin();
      assert cache.getNode(A) != null;
      assert !(cache.getNode(A).getChildrenNames().isEmpty());
      assert cache.getNode(A).getChildrenNames().contains(AB.getLastElement());
      assertNoLocks();
      tm.commit();
      assertNoLocks();
   }

   public void testWriteDoesntBlockRead() throws Exception
   {

      cache.put(AB, "k", "v");

      // start a write.
      tm.begin();
      cache.put(AB, "k2", "v2");
      assertLocked(AB);
      Transaction write = tm.suspend();

      // now start a read and confirm that the write doesn't block it.
      tm.begin();
      assert "v".equals(cache.get(AB, "k"));
      assert null == cache.get(AB, "k2") : "Should not see uncommitted changes";
      Transaction read = tm.suspend();

      // commit the write
      tm.resume(write);
      tm.commit();

      assertNoLocks();

      tm.resume(read);
      if (repeatableRead)
         assert null == cache.get(AB, "k2") : "Should have repeatable read";
      else
         assert "v2".equals(cache.get(AB, "k2")) : "Read committed should see committed changes";
      tm.commit();
      assertNoLocks();
   }

   public void testWriteDoesntBlockReadNonexistent() throws Exception
   {

      // start a write.
      tm.begin();
      cache.put(AB, "k", "v");
      assertLocked(AB);
      Transaction write = tm.suspend();

      // now start a read and confirm that the write doesn't block it.
      tm.begin();
      assert null == cache.get(AB, "k") : "Should not see uncommitted changes";
      assert null == cache.getNode(AB);
      Transaction read = tm.suspend();

      // commit the write
      tm.resume(write);
      tm.commit();

      assertNoLocks();

      tm.resume(read);
      if (repeatableRead)
      {
         assert null == cache.get(AB, "k") : "Should have repeatable read";
         assert null == cache.getNode(AB);
      }
      else
      {
         assert "v".equals(cache.get(AB, "k")) : "Read committed should see committed changes";
         assert null != cache.getNode(AB);
      }
      tm.commit();
      assertNoLocks();
   }

   public void testConcurrentWriters() throws Exception
   {

      tm.begin();
      cache.put(AB, "k", "v");
      Transaction t1 = tm.suspend();

      tm.begin();
      try
      {
         cache.put(AB, "k", "v");
         assert false : "Should fail lock acquisition";
      }
      catch (TimeoutException expected)
      {
//         expected.printStackTrace();  // for debugging
      }
      tm.commit();
      tm.resume(t1);
      tm.commit();
      assertNoLocks();
   }

   public void testRollbacks() throws Exception
   {

      cache.put(AB, "k", "v");
      tm.begin();
      assert "v".equals(cache.get(AB, "k"));
      Transaction reader = tm.suspend();

      tm.begin();
      cache.put(AB, "k", "v2");
      tm.rollback();

      tm.resume(reader);
      assert "v".equals(cache.get(AB, "k")) : "Expecting 'v' but was " + cache.get(AB, "k");
      tm.commit();

      // even after commit
      assert "v".equals(cache.get(AB, "k"));
      assertNoLocks();
   }

   public void testRollbacksOnNullNode() throws Exception
   {

      tm.begin();
      assert null == cache.get(AB, "k");
      assert null == cache.getNode(AB);
      Transaction reader = tm.suspend();

      tm.begin();
      cache.put(AB, "k", "v");
      assert null != cache.getNode(AB);
      assert "v".equals(cache.get(AB, "k"));
      tm.rollback();

      tm.resume(reader);
      assert null == cache.get(AB, "k") : "Expecting null but was " + cache.get(AB, "k");
      assert null == cache.getNode(AB);
      tm.commit();

      // even after commit
      assert null == cache.get(AB, "k");
      assert null == cache.getNode(AB);
      assertNoLocks();
   }

   public void testPhantomChildren() throws Exception
   {

      cache.put(AB, "k", "v");
      assert cache.getNode(AB).getChildren().size() == 0;
      assert cache.getNode(A).getChildren().size() == 1;

      tm.begin();
      cache.put(ABC, "k", "v");
      assert cache.getRoot().hasChild(ABC);
      assert cache.getNode(ABC) != null;
      assert cache.getNode(AB).getChild(ABC.getLastElement()) != null;
      assert cache.getNode(AB).getChildren().size() == 1;
      Transaction t = tm.suspend();


      assert cache.getNode(ABC) == null;
      assert cache.getNode(AB).getChild(ABC.getLastElement()) == null;
      assert cache.getNode(AB).getChildren().size() == 0;

      tm.resume(t);
      assert cache.getRoot().hasChild(ABC);
      assert cache.getNode(ABC) != null;
      tm.commit();

      assert cache.getNode(ABC) != null;
      assert cache.getNode(AB).getChild(ABC.getLastElement()) != null;
      assert cache.getNode(AB).getChildren().size() == 1;
   }

   public void testChildCount() throws Exception
   {

      cache.put(AB, "k", "v");
      assert cache.getNode(AB).getChildren().size() == 0;
      assert cache.getNode(A).getChildren().size() == 1;

      tm.begin();
      assert cache.getNode(AB).getChildren().size() == 0;
      assert cache.getNode(A).getChildren().size() == 1;
      cache.removeNode(AB);
      assert cache.getNode(A).getChildren().size() == 0;
      assert cache.getNode(A).hasChild(AB.getLastElement()) == false;
      assert cache.getNode(AB) == null;
      Transaction t = tm.suspend();


      assert cache.getNode(AB) != null;
      assert cache.getNode(A).getChild(AB.getLastElement()) != null;
      assert cache.getNode(A).getChildren().size() == 1;

      tm.resume(t);
      assert cache.getNode(A).getChildren().size() == 0;
      assert cache.getNode(A).hasChild(AB.getLastElement()) == false;
      assert cache.getNode(AB) == null;
      tm.commit();

      assert cache.getNode(A).getChildren().size() == 0;
      assert cache.getNode(A).hasChild(AB.getLastElement()) == false;
      assert cache.getNode(AB) == null;
   }

   public void testOverwritingOnInsert() throws Exception
   {

      cache.put(AB, "k", "v");

      tm.begin();
      cache.put(ABC, "k", "v");
      assert "v".equals(cache.get(ABC, "k"));
      assert "v".equals(cache.get(AB, "k"));
      Transaction t1 = tm.suspend();

      tm.begin();
      cache.put(AB, "k", "v2");
      assert "v2".equals(cache.get(AB, "k"));
      assert null == cache.get(ABC, "k");
      Transaction t2 = tm.suspend();

      tm.resume(t1);
      t1.commit();

      assert "v".equals(cache.get(AB, "k"));
      assert "v".equals(cache.get(ABC, "k"));

      tm.resume(t2);
      t2.commit();

      assert "v2".equals(cache.get(AB, "k"));
      assert "v".equals(cache.get(ABC, "k"));
   }

   public void testOverwritingOnInsert2() throws Exception
   {

      cache.put(AB, "k", "v");

      tm.begin();
      cache.put(AB, "k", "v2");
      assert "v2".equals(cache.get(AB, "k"));
      assert null == cache.get(ABC, "k");
      Transaction t1 = tm.suspend();

      tm.begin();
      cache.put(ABC, "k", "v");
      assert "v".equals(cache.get(ABC, "k"));
      assert "v".equals(cache.get(AB, "k"));
      Transaction t2 = tm.suspend();

      tm.resume(t1);
      t1.commit();

      assert "v2".equals(cache.get(AB, "k"));
      assert null == cache.get(ABC, "k");

      tm.resume(t2);
      t2.commit();

      assert "v2".equals(cache.get(AB, "k"));
      assert "v".equals(cache.get(ABC, "k"));
   }

   public void testOverwritingOnInsert3() throws Exception
   {

      cache.put(AB, "k", "v");

      tm.begin();
      cache.put(AB, "k", "v2");
      assert "v2".equals(cache.get(AB, "k"));
      assert null == cache.get(ABC, "k");
      Transaction t1 = tm.suspend();

      tm.begin();
      cache.put(ABC, "k", "v");
      assert "v".equals(cache.get(ABC, "k"));
      assert "v".equals(cache.get(AB, "k"));
      tm.commit();

      assert "v".equals(cache.get(ABC, "k"));
      assert "v".equals(cache.get(AB, "k"));

      tm.resume(t1);
      t1.commit();

      assert "v2".equals(cache.get(AB, "k"));
      assert "v".equals(cache.get(ABC, "k"));
   }

   public void testConcurrentInsertRemove1() throws Exception
   {

      cache.put(AB, "k", "v");

      tm.begin();
      cache.put(ABC, "k", "v");
      assert "v".equals(cache.get(AB, "k"));
      assert "v".equals(cache.get(ABC, "k"));
      Transaction t1 = tm.suspend();

      tm.begin();
      cache.removeNode(AB);
      assert null == cache.get(ABC, "k");
      assert null == cache.get(AB, "k");
      tm.commit();

      assert null == cache.get(ABC, "k");
      assert null == cache.get(AB, "k");

      tm.resume(t1);
      t1.commit();

      assert null == cache.get(ABC, "k");
      assert null == cache.get(AB, "k");
   }

   public void testConcurrentInsertRemove2() throws Exception
   {

      cache.put(AB, "k", "v");

      tm.begin();
      cache.removeNode(AB);
      assert null == cache.get(ABC, "k");
      assert null == cache.get(AB, "k");
      Transaction t1 = tm.suspend();

      tm.begin();
      assert "v".equals(cache.get(AB, "k"));
      cache.put(ABC, "k", "v");
      assert "v".equals(cache.get(ABC, "k"));
      tm.commit();

      assert "v".equals(cache.get(AB, "k"));
      assert "v".equals(cache.get(ABC, "k"));

      tm.resume(t1);
      t1.commit();

      assert null == cache.get(ABC, "k");
      assert null == cache.get(AB, "k");
   }
}
