package org.jboss.cache.api.mvcc;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.api.NodeMoveAPITest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"})
public abstract class NodeMoveMvccTestBase extends NodeMoveAPITest
{
   static final Fqn A_B = Fqn.fromRelativeFqn(A, B);
   static final Fqn A_B_C = Fqn.fromRelativeFqn(A_B, C);
   static final Fqn A_B_C_E = Fqn.fromRelativeFqn(A_B_C, E);
   static final Fqn A_B_D = Fqn.fromRelativeFqn(A_B, D);
   static final Fqn C_E = Fqn.fromRelativeFqn(C, E);
   static final Fqn D_B = Fqn.fromRelativeFqn(D, B);
   static final Fqn D_B_C = Fqn.fromRelativeFqn(D_B, C);


   public NodeMoveMvccTestBase()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }

   @Override
   protected void checkLocks()
   {
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache);
      LockManager lm = cr.getComponent(LockManager.class);
      InvocationContextContainer icc = cr.getComponent(InvocationContextContainer.class);

      LockAssert.assertNotLocked(A, lm, icc);
      LockAssert.assertNotLocked(Fqn.ROOT, lm, icc);
      LockAssert.assertLocked(C, lm, icc);
      LockAssert.assertLocked(A_B, lm, icc);
      LockAssert.assertLocked(A_B_C, lm, icc);
   }

   @Override
   protected void checkLocksDeep()
   {
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache);
      LockManager lm = cr.getComponent(LockManager.class);
      InvocationContextContainer icc = cr.getComponent(InvocationContextContainer.class);

      LockAssert.assertNotLocked(A, lm, icc);
      LockAssert.assertNotLocked(Fqn.ROOT, lm, icc);
      LockAssert.assertNotLocked(A_B_D, lm, icc);

      // /a/b, /c, /c/e, /a/b/c and /a/b/c/e should all be locked.
      LockAssert.assertLocked(A_B, lm, icc);
      LockAssert.assertLocked(C, lm, icc);
      LockAssert.assertLocked(C_E, lm, icc);
      LockAssert.assertLocked(A_B_C, lm, icc);
      LockAssert.assertLocked(A_B_C_E, lm, icc);
   }

   @Override
   protected void assertNoLocks()
   {
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache);
      LockManager lm = cr.getComponent(LockManager.class);
      InvocationContextContainer icc = cr.getComponent(InvocationContextContainer.class);
      LockAssert.assertNoLocks(lm, icc);
   }

   public void testNonexistentSource()
   {
      cache.put(A_B_C, "k", "v");
      assert "v".equals(cache.get(A_B_C, "k"));
      assert 1 == cache.getNode(A_B).getChildren().size();
      assert cache.getNode(A_B).getChildrenNames().contains(C.getLastElement());
      assert !cache.getNode(A_B).getChildrenNames().contains(D.getLastElement());

      cache.move(D, A_B);

      assert "v".equals(cache.get(A_B_C, "k"));
      assert 1 == cache.getNode(A_B).getChildren().size();
      assert cache.getNode(A_B).getChildrenNames().contains(C.getLastElement());
      assert !cache.getNode(A_B).getChildrenNames().contains(D.getLastElement());
   }

   public void testNonexistentTarget()
   {
      cache.put(A_B_C, "k", "v");
      assert "v".equals(cache.get(A_B_C, "k"));
      assert 1 == cache.getNode(A_B).getChildren().size();
      assert cache.getNode(A_B).getChildrenNames().contains(C.getLastElement());
      assert null == cache.getNode(D);

      cache.move(A_B, D);

      assert null == cache.getNode(A_B_C);
      assert null == cache.getNode(A_B);
      assert null != cache.getNode(D);
      assert null != cache.getNode(D_B);
      assert null != cache.getNode(D_B_C);
      assert "v".equals(cache.get(D_B_C, "k"));
   }
}
