package org.jboss.cache.lock;

import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnversionedNode;
import org.jboss.cache.factories.context.ContextFactory;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.invocation.NodeInvocationDelegate;
import static org.jboss.cache.lock.LockType.WRITE;
import org.jboss.cache.transaction.DummyTransaction;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.transaction.GlobalTransaction;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;

@Test(groups = "unit")
public abstract class AbstractLockManagerRecordingTest
{
   protected class AbstractLockManagerRecordingTestTL {
      public LockManager lm;
      public InvocationContextContainer icc;
      protected ContextFactory contextFactory;
   }
   protected ThreadLocal<AbstractLockManagerRecordingTestTL> threadLocal = new ThreadLocal<AbstractLockManagerRecordingTestTL>();
   protected boolean fqnBasedLocking = true;

   @AfterMethod
   public void tearDown()
   {
      AbstractLockManagerRecordingTestTL tl = threadLocal.get();
      if (tl != null) {
         tl.lm = null;
         threadLocal.set(null);
      }
   }

   public void testRecordingLocksNoTx() throws InterruptedException
   {
      AbstractLockManagerRecordingTestTL tl = threadLocal.get();
      LockManager lm = tl.lm;
      Fqn fqn = Fqn.fromString("/a/b/c");
      NodeSPI node = createNode(fqn);
      InvocationContext ctx = tl.icc.get();

      // lock and record.
      lm.lockAndRecord(node, WRITE, ctx);
      assert ctx.getLocks().contains(fqnBasedLocking ? fqn : node.getLock());
      assert ctx.getLocks().size() == 1;
      assert lm.isLocked(node) : "Should be locked";
      lm.unlock(ctx);
      assert !lm.isLocked(node) : "Should not be locked";
   }

   public void testRecordingLocksWithTx() throws InterruptedException, SystemException, RollbackException
   {
      AbstractLockManagerRecordingTestTL tl = threadLocal.get();
      LockManager lm = tl.lm;
      Fqn fqn = Fqn.fromString("/a/b/c");
      NodeSPI node = createNode(fqn);
      InvocationContext ctx = tl.icc.get();
      ctx.setGlobalTransaction(new GlobalTransaction());
      ctx.setTransaction(new DummyTransaction(DummyTransactionManager.getInstance()));
      ctx.setTransactionContext(tl.contextFactory.createTransactionContext(ctx.getTransaction()));

      // lock and record.
      lm.lockAndRecord(node, WRITE, ctx);
      assert ctx.getLocks().contains(fqnBasedLocking ? fqn : node.getLock());
      assert ctx.getTransactionContext().getLocks().size() == 1;
      assert lm.isLocked(node) : "Should be locked";
      lm.unlock(ctx);
      assert !lm.isLocked(node) : "Should not be locked";
   }

   protected NodeSPI createNode(Fqn fqn)
   {
      UnversionedNode un = new UnversionedNode(fqn);
      return new NodeInvocationDelegate(un);
   }
}
