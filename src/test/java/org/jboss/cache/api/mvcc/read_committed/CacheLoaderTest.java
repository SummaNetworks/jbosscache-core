package org.jboss.cache.api.mvcc.read_committed;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.api.mvcc.LockAssert;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.loader.DummyInMemoryCacheLoaderTest;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.lock.LockManager;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.read_committed.CacheLoaderTest")
public class CacheLoaderTest extends DummyInMemoryCacheLoaderTest
{
   @Override
   protected void configureCache(CacheSPI cache) throws Exception
   {
      super.configureCache(cache);
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.MVCC);
      cache.getConfiguration().setIsolationLevel(IsolationLevel.READ_COMMITTED);
   }

   @AfterMethod
   public void postTest()
   {
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache);

      LockAssert.assertNoLocks(cr.getComponent(LockManager.class), cr.getComponent(InvocationContextContainer.class));
   }
}
