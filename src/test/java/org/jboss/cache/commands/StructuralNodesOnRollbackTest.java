package org.jboss.cache.commands;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.HashMap;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Test to check that structural nodes are being removed on rollback: http://jira.jboss.com/jira/browse/JBCACHE-1365.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "functional", sequential = true, testName = "commands.StructuralNodesOnRollbackTest")
public class StructuralNodesOnRollbackTest
{
   private CacheSPI<Object, Object> cache;
   private TransactionManager txMgr;

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      Configuration cacheConfig = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, false);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(cacheConfig, getClass());
      txMgr = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
      txMgr = null;
   }

   public void testNoTx() throws Exception
   {
      txMgr.begin();
      cache.put("/a/b/c", "k","v");
      NodeSPI<Object,Object> root = cache.getRoot();
      assert root.getChild("a") != null;
      assert root.getChild(Fqn.fromString("/a/b/c")) != null;
      assert cache.exists("/a/b");
      txMgr.rollback();
   }

   public void testPutDataMap() throws Exception
   {
      HashMap map = new HashMap();
      map.put("k", "v");

      assert !cache.exists("/a/b");
      txMgr.begin();
      cache.put("/a/b/c", map);
      assert cache.exists("/a/b");
      txMgr.rollback();
      assert !cache.exists("/a/b");
   }

   public void testPutKeyValueCommand() throws Exception
   {
      assert !cache.exists("/a/b");
      txMgr.begin();
      cache.put("/a/b/c", "key", "value");
      assert cache.exists("/a/b");
      txMgr.rollback();
      assert !cache.exists("/a/b");
   }
}
