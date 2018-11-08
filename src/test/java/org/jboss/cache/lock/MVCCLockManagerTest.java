package org.jboss.cache.lock;

import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnversionedNode;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.context.MVCCContextFactory;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.invocation.NodeInvocationDelegate;
import static org.jboss.cache.lock.LockType.READ;
import static org.jboss.cache.lock.LockType.WRITE;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.List;

@Test(groups = {"unit", "mvcc"}, sequential = true, testName = "lock.MVCCLockManagerTest")
public class MVCCLockManagerTest
{
   MVCCLockManager lm;
   InvocationContextContainer icc;

   @BeforeMethod
   public void setUp()
   {
      icc = new InvocationContextContainer();
      icc.injectContextFactory(new MVCCContextFactory());
      lm = new MVCCLockManager();
      TransactionManager tm = getTransactionManager();
      lm.injectConfiguration(new Configuration());
      lm.injectDependencies(null, null, tm, icc);
      lm.startLockManager();
   }

   protected TransactionManager getTransactionManager()
   {
      return DummyTransactionManager.getInstance(); // use this to ensure the lock manager uses ownable reentrant locks
   }

   @AfterMethod
   public void tearDown()
   {
      lm = null;
   }

   public void testUsingReadLocks()
   {
      // using any READ locks should be no-ops.
      lm.isLocked(null, READ);

      lm.getReadOwners(Fqn.ROOT);

      try
      {
         lm.getReadOwners((NodeSPI) null);
      }
      catch (NullPointerException npe)
      {
         // since we're passing in a null.
      }
   }

   public void testLockReentrancy() throws InterruptedException
   {
      Fqn fqn = Fqn.fromString("/a/b/c");
      NodeSPI node = new NodeInvocationDelegate(new UnversionedNode(fqn));

      assert lm.lock(fqn, WRITE, null);
      assert lm.isLocked(node);

      assert lm.lock(node, WRITE, null);
      assert lm.isLocked(node);

      lm.unlock(node, null);

      assert lm.isLocked(node) : "Should still be locked";
      assert lm.ownsLock(node, Thread.currentThread());

      lm.unlock(fqn, null);

      assert !lm.isLocked(node) : "Should not be locked";
   }

   public void testSpreadingOfLocks()
   {
      List<Fqn> fqns = new ArrayList<Fqn>(11);
      fqns.add(Fqn.fromString("/"));
      fqns.add(Fqn.fromString("/a"));
      fqns.add(Fqn.fromString("/a/b"));
      fqns.add(Fqn.fromString("/a/b/c"));
      fqns.add(Fqn.fromString("/a/b/c/d"));
      fqns.add(Fqn.fromString("/a/b/c/e"));
      fqns.add(Fqn.fromString("/A"));
      fqns.add(Fqn.fromString("/A/B"));
      fqns.add(Fqn.fromString("/A/B/C"));
      fqns.add(Fqn.fromString("/A/B/C/D"));
      fqns.add(Fqn.fromString("/A/B/C/E"));
   }
}
