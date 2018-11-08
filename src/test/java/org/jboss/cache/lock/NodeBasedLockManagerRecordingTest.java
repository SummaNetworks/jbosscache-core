package org.jboss.cache.lock;

import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.PessimisticUnversionedNode;
import org.jboss.cache.factories.context.PessimisticContextFactory;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.invocation.NodeInvocationDelegate;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 3.0
 */
@Test(groups = "unit", testName = "lock.NodeBasedLockManagerRecordingTest")
public class NodeBasedLockManagerRecordingTest extends AbstractLockManagerRecordingTest
{
   @BeforeMethod
   public void setUp()
   {
      AbstractLockManagerRecordingTestTL tl = new AbstractLockManagerRecordingTestTL();
      threadLocal.set(tl);      
      tl.icc = new InvocationContextContainer();
      tl.lm = new NodeBasedLockManager();
      PessimisticContextFactory pcf = new PessimisticContextFactory();
      tl.icc.injectContextFactory(pcf);
      tl.contextFactory = pcf;
      fqnBasedLocking = false;
   }

   @Override
   protected NodeSPI createNode(Fqn fqn)
   {
      PessimisticUnversionedNode un = new PessimisticUnversionedNode(fqn.getLastElement(), fqn, null, null);
      un.injectDependencies(null, null, null);
      un.injectLockStrategyFactory(new LockStrategyFactory());
      return new NodeInvocationDelegate(un);
   }
}
