package org.jboss.cache.options;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.lock.LockType;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * Tests forcing a write lock to be obtained on a node
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional", "pessimistic"}, sequential = true, testName = "options.ForceWriteLockTest")
public class ForceWriteLockTest
{
   protected CacheSPI<String, String> cache;
   private Fqn fqn = Fqn.fromString("/a/b");
   private TransactionManager tm;
   protected NodeLockingScheme nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      Configuration c = new Configuration();
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      c.setNodeLockingScheme(nodeLockingScheme);
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(c, getClass());
      tm = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testControl() throws Exception
   {
      cache.put(fqn, "k", "v");
      tm.begin();
      cache.get(fqn, "k");
      // parent should be read-locked!!
      assertLocked(cache.getInvocationContext().getGlobalTransaction(), fqn.getParent(), false);
      assertLocked(cache.getInvocationContext().getGlobalTransaction(), fqn, false);
      tm.commit();
      assertNotLocked(fqn);
   }

   public void testForceWithGetTx() throws Exception
   {
      cache.put(fqn, "k", "v");
      tm.begin();
      cache.getInvocationContext().getOptionOverrides().setForceWriteLock(true);
      cache.get(fqn, "k");
      // parent should be read-locked!!
      assertLocked(cache.getInvocationContext().getGlobalTransaction(), fqn.getParent(), false);
      assertLocked(cache.getInvocationContext().getGlobalTransaction(), fqn, true);
      tm.commit();
      assertNotLocked(fqn);

      // now test normal operation
      testControl();
   }

   public void testForceWithPutTx() throws Exception
   {
      cache.put(fqn, "k", "v");
      tm.begin();
      cache.getInvocationContext().getOptionOverrides().setForceWriteLock(true);
      cache.put(fqn, "k", "v2");
      // parent should be read-locked!!
      assertLocked(cache.getInvocationContext().getGlobalTransaction(), fqn.getParent(), false);
      assertLocked(cache.getInvocationContext().getGlobalTransaction(), fqn, true);
      tm.commit();
      assertNotLocked(fqn);

      // now test normal operation
      testControl();
   }

   public void testForceWithRemoveTx() throws Exception
   {
      cache.put(fqn, "k", "v");
      tm.begin();
      cache.getInvocationContext().getOptionOverrides().setForceWriteLock(true);
      cache.remove(fqn, "k");
      // parent should be read-locked!!
      assertLocked(cache.getInvocationContext().getGlobalTransaction(), fqn.getParent(), false);
      assertLocked(cache.getInvocationContext().getGlobalTransaction(), fqn, true);
      tm.commit();
      assertNotLocked(fqn);

      // now test normal operation
      testControl();
   }

   protected void assertNotLocked(Fqn fqn)
   {
      assert !TestingUtil.extractLockManager(cache).isLocked(cache.peek(fqn, true)) : "Node " + fqn + " is locked!!";
   }

   protected void assertLocked(Object owner, Fqn fqn, boolean write_locked)
   {
      LockManager lm = TestingUtil.extractLockManager(cache);
      NodeSPI<String, String> n = cache.peek(fqn, true);
      if (owner == null) owner = Thread.currentThread();
      assertTrue("node " + fqn + " is not locked", lm.isLocked(n));
      if (write_locked)
      {
         assertTrue("node " + fqn + " is not write-locked by owner " + owner, lm.ownsLock(fqn, LockType.WRITE, owner));
      }
      else
      {
         assertTrue("node " + fqn + " is not read-locked by owner " + owner, lm.ownsLock(fqn, LockType.READ, owner));
      }
   }
}
