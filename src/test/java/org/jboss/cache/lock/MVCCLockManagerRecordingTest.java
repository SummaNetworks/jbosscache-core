package org.jboss.cache.lock;

import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.context.MVCCContextFactory;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.concurrent.locks.LockContainer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 3.0
 */
@Test(groups = {"unit", "mvcc"}, testName = "lock.MVCCLockManagerRecordingTest")
public class MVCCLockManagerRecordingTest extends AbstractLockManagerRecordingTest
{
   @BeforeMethod
   public void setUp()
   {
      AbstractLockManagerRecordingTestTL tl = new AbstractLockManagerRecordingTestTL();
      threadLocal.set(tl);
      tl.icc = new InvocationContextContainer();
      MVCCLockManager mvccLockManager = new MVCCLockManager();
      TransactionManager tm = DummyTransactionManager.getInstance();
      mvccLockManager.injectConfiguration(new Configuration());
      mvccLockManager.injectDependencies(null, null, tm, tl.icc);
      mvccLockManager.startLockManager();
      tl.lm = mvccLockManager;
      tl.contextFactory = new MVCCContextFactory();
      tl.icc.injectContextFactory(tl.contextFactory);
   }

   public void testFqnHashing()
   {
      AbstractLockManagerRecordingTestTL tl = threadLocal.get();      
      LockContainer lc = (LockContainer) TestingUtil.extractField(tl.lm, "lockContainer");
      List<Fqn> fqns = new ArrayList<Fqn>();
      fqns.add(Fqn.ROOT);
      fqns.add(Fqn.fromString("/1"));
      fqns.add(Fqn.fromString("/1/2"));
      fqns.add(Fqn.fromString("/1/2/3"));
      fqns.add(Fqn.fromString("/a/b/c/d"));

   }
}
