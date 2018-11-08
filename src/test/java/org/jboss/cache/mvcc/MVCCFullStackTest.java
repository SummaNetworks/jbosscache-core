package org.jboss.cache.mvcc;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.interceptors.MVCCLockingInterceptor;
import org.jboss.cache.lock.IsolationLevel;
import static org.jboss.cache.lock.IsolationLevel.*;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 3.0
 */
@Test(groups = "functional", sequential = true, testName = "mvcc.MVCCFullStackTest")
public class MVCCFullStackTest
{
   CacheSPI<Object, Object> cache;

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testDefaultConfiguration()
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(getClass());

      assert TestingUtil.findInterceptor(cache, MVCCLockingInterceptor.class) != null : "MVCC interceptor should be in stack";
      assert cache.getConfiguration().getNodeLockingScheme() == NodeLockingScheme.MVCC;
   }

   public void testIsolationLevelUpgrade1()
   {
      isoLevelTest(NONE, READ_COMMITTED);
   }

   public void testIsolationLevelUpgrade2()
   {
      isoLevelTest(READ_UNCOMMITTED, READ_COMMITTED);
   }

   public void testIsolationLevelDowngrade()
   {
      isoLevelTest(SERIALIZABLE, REPEATABLE_READ);
   }

   public void testIsolationLevelNoUpgrade1()
   {
      isoLevelTest(READ_COMMITTED, READ_COMMITTED);
   }

   public void testIsolationLevelNoUpgrade2()
   {
      isoLevelTest(REPEATABLE_READ, REPEATABLE_READ);
   }

   private void isoLevelTest(IsolationLevel configuredWith, IsolationLevel expected)
   {
      Configuration c = new Configuration();
      c.setNodeLockingScheme(NodeLockingScheme.MVCC);
      c.setIsolationLevel(configuredWith);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
      assert cache.getConfiguration().getIsolationLevel() == expected : "Expected to change isolation level from " + configuredWith + " to " + expected + " but was " + cache.getConfiguration().getIsolationLevel();
   }
}
