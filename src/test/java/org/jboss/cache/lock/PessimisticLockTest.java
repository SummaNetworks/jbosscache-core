package org.jboss.cache.lock;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import static org.jboss.cache.lock.LockType.READ;
import static org.jboss.cache.lock.LockType.WRITE;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * basic locking test
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = "functional", sequential = true, testName = "lock.PessimisticLockTest")
public class PessimisticLockTest
{
   private Cache<Object, Object> cache;
   private TransactionManager tm;
   private Fqn fqn = Fqn.fromString("/a/b/c");
   private LockManager lockManager;

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      cache = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.start();
      tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      lockManager = TestingUtil.extractLockManager(cache);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   private void assertNoStaleLocks()
   {
      CacheSPI spi = (CacheSPI) cache;
      assert spi.getNumberOfLocksHeld() == 0 : "Should have no stale locks!";
   }

   public void testPut() throws Exception
   {
      cache.put(fqn, "k", "v");

      assertNoStaleLocks();

      tm.begin();
      cache.put(fqn, "k2", "v2");
      NodeSPI<Object, Object> n = (NodeSPI<Object, Object>) cache.getRoot().getChild(fqn);

      assertFalse(lockManager.isLocked(n, READ));
      assertTrue(lockManager.isLocked(n, WRITE));
      assertTrue(lockManager.isLocked(n.getParentDirect(), READ));
      assertFalse(lockManager.isLocked(n.getParentDirect(), WRITE));
      assertTrue(lockManager.isLocked(n.getParentDirect().getParentDirect(), READ));
      assertFalse(lockManager.isLocked(n.getParentDirect().getParentDirect(), WRITE));

      tm.commit();

      assertNoStaleLocks();
   }

   public void testGet() throws Exception
   {
      cache.put(fqn, "k", "v");

      assertNoStaleLocks();

      tm.begin();
      cache.get(fqn, "k2");
      NodeSPI<Object, Object> n = (NodeSPI<Object, Object>) cache.getRoot().getChild(fqn);

      assertTrue(lockManager.isLocked(n, READ));
      assertFalse(lockManager.isLocked(n, WRITE));
      assertTrue(lockManager.isLocked(n.getParentDirect(), READ));
      assertFalse(lockManager.isLocked(n.getParentDirect(), WRITE));
      assertTrue(lockManager.isLocked(n.getParentDirect().getParentDirect(), READ));
      assertFalse(lockManager.isLocked(n.getParentDirect().getParentDirect(), WRITE));

      tm.commit();

      assertNoStaleLocks();
   }

   public void testRemove() throws Exception
   {
      cache.put(fqn, "k", "v");

      assertNoStaleLocks();

      tm.begin();
      cache.remove(fqn, "k2");
      NodeSPI<Object, Object> n = (NodeSPI<Object, Object>) cache.getRoot().getChild(fqn);

      assertFalse(lockManager.isLocked(n, READ));
      assertTrue(lockManager.isLocked(n, WRITE));
      assertTrue(lockManager.isLocked(n.getParentDirect(), READ));
      assertFalse(lockManager.isLocked(n.getParentDirect(), WRITE));
      assertTrue(lockManager.isLocked(n.getParentDirect().getParentDirect(), READ));
      assertFalse(lockManager.isLocked(n.getParentDirect().getParentDirect(), WRITE));

      tm.commit();

      assertNoStaleLocks();

   }
}
