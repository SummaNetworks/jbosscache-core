package org.jboss.cache.commands;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "functional", sequential = true, testName = "commands.RollbackOnNoOpTest")
public class RollbackOnNoOpTest
{
   private CacheSPI<Object, Object> cache;
   private TransactionManager txMgr;

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      Configuration cacheConfig = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, false);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(cacheConfig, false, getClass());
      cache.start();
      txMgr = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
      txMgr = null;
   }

   public void testRollbackOnRemoveNodeDoesNotFail() throws Exception
   {
      txMgr.begin();
      cache.remove("/blah/blah", "non-exist");
      txMgr.rollback();
   }

   public void testRollbackOnClearData() throws Exception
   {
      txMgr.begin();
      cache.clearData("/blah/blah");
      txMgr.rollback();
   }

   public void testCreateNodeCommand() throws Exception
   {
      cache.put("/blah/blah", "key", "value");
      txMgr.begin();
      cache.clearData("/blah/blah");
      txMgr.rollback();
      assert cache.get("/blah/blah", "key") != null;
   }

   public void testRemoveKeyCommand() throws Exception
   {
      txMgr.begin();
      cache.remove("/blah/blah", "key");
      txMgr.rollback();
   }

   public void testRemoveNodeCommand() throws Exception
   {
      cache.put("/blah/blah", "key", "value");
      txMgr.begin();
      cache.removeNode("/blah");
      txMgr.rollback();
   }
}
