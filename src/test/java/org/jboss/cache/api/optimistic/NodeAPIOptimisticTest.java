package org.jboss.cache.api.optimistic;

import org.jboss.cache.Fqn;
import org.jboss.cache.api.NodeAPITest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.interceptors.OptimisticNodeInterceptor;
import org.jboss.cache.interceptors.PessimisticLockInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.optimistic.TransactionWorkspace;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;
import org.jboss.cache.CacheSPI;

/**
 * An optimistic version of {@link org.jboss.cache.api.NodeAPITest}
 */
@Test(groups = {"functional", "optimistic"}, testName = "api.optimistic.NodeAPIOptimisticTest")
public class NodeAPIOptimisticTest extends NodeAPITest
{
   protected NodeLockingScheme getNodeLockingScheme()
   {
      return NodeLockingScheme.OPTIMISTIC;
   }

   protected void assertNodeLockingScheme()
   {
      assert cache.getConfiguration().getNodeLockingScheme() == NodeLockingScheme.OPTIMISTIC;
      boolean interceptorChainOK = false;

      List<CommandInterceptor> chain = cache.getInterceptorChain();
      for (CommandInterceptor i : chain)
      {
         if (i instanceof PessimisticLockInterceptor) assert false : "Not an optimistic locking chain!!";
         if (i instanceof OptimisticNodeInterceptor) interceptorChainOK = true;
      }

      assert interceptorChainOK : "Not an optimistic locking chain!!";
   }

   @Override
   protected void childrenUnderTxCheck() throws Exception
   {
      TransactionWorkspace<Object, Object> w = getTransactionWorkspace();
      assert w.getNodes().size() == 4 : "Should be 4 nodes in the workspace, not " + w.getNodes().size();
      // test deltas
      List<Set<Fqn>> deltas = w.getNodes().get(Fqn.ROOT).getMergedChildren();
      assert deltas.get(0).size() == 1 : "/ should have 1 child added";
      assert deltas.get(1).size() == 0 : "/ should have 0 children removed";

      deltas = w.getNodes().get(A).getMergedChildren();
      assert deltas.get(0).size() == 2 : "/ should have 2 children added";
      assert deltas.get(1).size() == 0 : "/ should have 0 children removed";

      deltas = w.getNodes().get(A_B).getMergedChildren();
      assert deltas.get(0).size() == 0 : "/a/b should have 0 children added";
      assert deltas.get(1).size() == 0 : "/a/b should have 0 children removed";

      deltas = w.getNodes().get(A_C).getMergedChildren();
      assert deltas.get(0).size() == 0 : "/a/c should have 0 children added";
      assert deltas.get(1).size() == 0 : "/a/c should have 0 children removed";
   }
}
