package org.jboss.cache.api.mvcc.read_committed;

import javax.transaction.TransactionManager;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Node;
import org.jboss.cache.api.NodeAPITest;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.interceptors.MVCCLockingInterceptor;
import org.jboss.cache.interceptors.OptimisticNodeInterceptor;
import org.jboss.cache.interceptors.PessimisticLockInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

import java.util.List;

/**
 * An MVCC version of {@link org.jboss.cache.api.NodeAPITest}
 */
@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.read_committed.NodeAPIMVCCTest")
public class NodeAPIMVCCTest extends NodeAPITest
{
   protected NodeLockingScheme getNodeLockingScheme()
   {
      return NodeLockingScheme.MVCC;
   }

   @Override
   protected void configure(Configuration c)
   {
      c.setIsolationLevel(IsolationLevel.READ_COMMITTED);
   }

   protected void assertNodeLockingScheme()
   {
      assert cache.getConfiguration().getNodeLockingScheme() == NodeLockingScheme.MVCC;
      boolean interceptorChainOK = false;

      List<CommandInterceptor> chain = cache.getInterceptorChain();
      for (CommandInterceptor i : chain)
      {
         if (i instanceof PessimisticLockInterceptor) assert false : "Not an MVCC locking chain!!";
         if (i instanceof OptimisticNodeInterceptor) assert false : "Not an MVCC locking chain!!";
         if (i instanceof MVCCLockingInterceptor) interceptorChainOK = true;

      }

      assert interceptorChainOK : "Not an MVCC locking chain!!";
   }

   @Override
   public void testLocking()
   {
      // no op - this is tested separately
   }

   @Override
   protected void childrenUnderTxCheck() throws Exception
   {
      assert cache.getNode(A_B) != null;
      assert cache.getNode(A_C) != null;

      assert cache.getInvocationContext().getLocks().contains(A);
      assert cache.getInvocationContext().getLocks().contains(A_B);
      assert cache.getInvocationContext().getLocks().contains(A_C);
   }
}