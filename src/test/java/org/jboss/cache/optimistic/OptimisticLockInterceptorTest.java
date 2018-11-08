package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.commands.VisitableCommand;
import org.jboss.cache.interceptors.OptimisticInterceptor;
import org.jboss.cache.interceptors.OptimisticLockingInterceptor;
import org.jboss.cache.lock.LockType;
import static org.jboss.cache.lock.LockType.READ;
import static org.jboss.cache.lock.LockType.WRITE;
import org.jboss.cache.lock.NodeLock;
import org.jboss.cache.util.TestingUtil;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * // Test for JBCACHE-1228
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.OptimisticLockInterceptorTest")
public class OptimisticLockInterceptorTest extends AbstractOptimisticTestCase
{
   private CacheSPI<Object, Object> cache;
   private LockReportInterceptor lri;
   private Fqn parent = Fqn.fromString("/parent");
   private Fqn child = Fqn.fromString("/parent/child");
   private TransactionManager tm;

   @BeforeMethod
   protected void setUp() throws Exception
   {
      cache = createCache();
      lri = new LockReportInterceptor();
      TestingUtil.extractComponentRegistry(cache).wireDependencies(lri);

      TestingUtil.injectInterceptor(cache, lri, OptimisticLockingInterceptor.class);

      cache.put(child, "key", "value");

      tm = cache.getTransactionManager();
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testPut() throws Exception
   {
      tm.begin();
      cache.put(child, "key2", "value2");
      lri.reset();
      lri.expectsReadLock(Fqn.ROOT);
      lri.expectsReadLock(parent);
      lri.expectsWriteLock(child);
      tm.commit();
      lri.assertReceivedExpectedLocks();

      assertNoStaleLocks();
   }

   public void testGet() throws Exception
   {
      tm.begin();
      cache.get(child, "key2");
      lri.reset();
      // nothing is stale, expecting nothing here.
      tm.commit();
      lri.assertReceivedExpectedLocks();

      assertNoStaleLocks();
   }

   public void testRemove() throws Exception
   {
      tm.begin();
      cache.remove(child, "key2");
      lri.reset();
      lri.expectsReadLock(Fqn.ROOT);
      lri.expectsReadLock(parent);
      lri.expectsWriteLock(child);
      tm.commit();
      lri.assertReceivedExpectedLocks();

      assertNoStaleLocks();
   }

   public void testPutLockParentForCIR() throws Exception
   {
      cache.getConfiguration().setLockParentForChildInsertRemove(true);
      cache.removeNode(parent);
      cache.put(parent, "k", "v");

      tm.begin();
      cache.put(child, "key2", "value2");
      lri.reset();
      lri.expectsReadLock(Fqn.ROOT);
      lri.expectsWriteLock(parent);
      lri.expectsWriteLock(child);
      tm.commit();
      lri.assertReceivedExpectedLocks();

      assertNoStaleLocks();
   }

   public void testGetLockParentForCIR() throws Exception
   {
      cache.getConfiguration().setLockParentForChildInsertRemove(true);
      tm.begin();
      cache.get(child, "key2");
      lri.reset();
      // nothing is stale, expecting nothing here.
      tm.commit();
      lri.assertReceivedExpectedLocks();

      assertNoStaleLocks();
   }

   public void testRemoveLockParentForCIR() throws Exception
   {
      cache.getConfiguration().setLockParentForChildInsertRemove(true);
      tm.begin();
      cache.removeNode(child);
      lri.reset();
      lri.expectsReadLock(Fqn.ROOT);
      lri.expectsWriteLock(parent);
      lri.expectsWriteLock(child);
      tm.commit();
      lri.assertReceivedExpectedLocks();

      assertNoStaleLocks();
   }


   public void testPutNodeNotExists() throws Exception
   {
      cache.removeNode(Fqn.ROOT);
      tm.begin();
      cache.put(child, "key2", "value2");
      lri.reset();
      lri.expectsReadLock(Fqn.ROOT);
      lri.expectsWriteLock(parent);
      lri.expectsWriteLock(child);
      tm.commit();
      lri.assertReceivedExpectedLocks();

      assertNoStaleLocks();
   }

   public void testGetNodeNotExists() throws Exception
   {
      cache.removeNode(Fqn.ROOT);
      tm.begin();
      cache.get(child, "key2");
      lri.reset();
      // nothing is stale, expecting nothing here.
      tm.commit();
      lri.assertReceivedExpectedLocks();

      assertNoStaleLocks();
   }

   public void testRemoveNodeNotExists() throws Exception
   {
      cache.removeNode(Fqn.ROOT);
      tm.begin();
      cache.remove(child, "key2");
      lri.reset();
      // nothing is stale, expecting nothing here.
      tm.commit();
      lri.assertReceivedExpectedLocks();

      assertNoStaleLocks();
   }

   private void assertNoStaleLocks()
   {
      assert cache.getNumberOfLocksHeld() == 0;
   }
}

class LockReportInterceptor extends OptimisticInterceptor
{
   private Map<Fqn, LockType> expected = new HashMap<Fqn, LockType>();
   private Map<Fqn, LockType> actual = new HashMap<Fqn, LockType>();

   void reset()
   {
      expected.clear();
      actual.clear();
   }

   void assertReceivedExpectedLocks()
   {
      AssertJUnit.assertEquals(expected, actual);
   }

   void expectsReadLock(Fqn f)
   {
      expected.put(f, READ);
   }

   void expectsWriteLock(Fqn f)
   {
      expected.put(f, WRITE);
   }


   @Override
   public Object handleDefault(InvocationContext ctx, VisitableCommand command) throws Throwable
   {
      TransactionWorkspace w = getTransactionWorkspace(ctx);
      Map nodeMap = w.getNodes();
      for (Iterator i = nodeMap.keySet().iterator(); i.hasNext();)
      {
         WorkspaceNode wn = (WorkspaceNode) nodeMap.get(i.next());
         NodeSPI n = wn.getNode();
         NodeLock lock = n.getLock();
         if (lock.isLocked())
         {
            actual.put(n.getFqn(), lock.isReadLocked() ? READ : WRITE);
         }
      }
      return invokeNextInterceptor(ctx, command);
   }
}
