package org.jboss.cache.api.mvcc.read_committed;

import org.jboss.cache.api.mvcc.LockAssert;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.passivation.PassivationTestsBase;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.read_committed.PassivationTest")
public class PassivationTest extends PassivationTestsBase
{
   @Override
   protected void configureCache() throws Exception
   {
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.MVCC);
      cache.getConfiguration().setIsolationLevel(IsolationLevel.READ_COMMITTED);

      String binName = "bin-" + Thread.currentThread().getName();

      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummySharedInMemoryCacheLoader.class.getName(),
            "debug=true\nbin=" + binName, false, true, false, false, false);
      clc.setPassivation(true);
      cache.getConfiguration().setCacheLoaderConfig(clc);
   }

   @AfterMethod
   public void postTest()
   {
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache);

      LockAssert.assertNoLocks(cr.getComponent(LockManager.class), cr.getComponent(InvocationContextContainer.class));
   }
}
