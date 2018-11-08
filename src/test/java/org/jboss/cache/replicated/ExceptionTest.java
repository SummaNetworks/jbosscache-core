package org.jboss.cache.replicated;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.lock.TimeoutException;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * Tests the type of exceptions thrown for Lock Acquisition Timeouts versus Sync Repl Timeouts
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional"}, testName = "replicated.ExceptionTest")
public class ExceptionTest
{
   private Cache cache1;
   private Cache cache2;
   private Fqn fqn = Fqn.fromString("/a");

   @BeforeMethod
   public void setUp()
   {
      Configuration c = new Configuration();
      c.setSyncCommitPhase(true);
      c.setSyncRollbackPhase(true);
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());      
      cache1 = new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      c = new Configuration();
      c.setSyncCommitPhase(true);
      c.setSyncRollbackPhase(true);
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache2 = new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   @Test(groups = {"functional"}, expectedExceptions = {TimeoutException.class})
   public void testSyncReplTimeout()
   {
      cache1.getConfiguration().setSyncReplTimeout(1); // 1ms.  this is *bound* to fail.
      cache2.getConfiguration().setSyncReplTimeout(1);
      String s = UnitTestConfigurationFactory.getEmptyConfiguration().getClusterConfig();
      String newCfg = UnitTestConfigurationFactory.injectDelay(s, 100, 100);

      cache1.getConfiguration().setClusterConfig(newCfg);
      cache2.getConfiguration().setClusterConfig(newCfg);

      cache1.start();
      cache2.start();

      TestingUtil.blockUntilViewsReceived(60000, cache1, cache2);

      cache1.put(fqn, "k", "v");
   }

   @Test(groups = {"functional"}, expectedExceptions = {TimeoutException.class})
   public void testLockAcquisitionTimeout() throws Exception
   {
      cache2.getConfiguration().setLockAcquisitionTimeout(1);

      cache1.start();
      cache2.start();

      TestingUtil.blockUntilViewsReceived(10000, cache1, cache2);

      // get a lock on cache 2 and hold on to it.
      TransactionManager tm = cache2.getConfiguration().getRuntimeConfig().getTransactionManager();
      tm.begin();
      cache2.put(fqn, "block", "block");
      Transaction t = tm.suspend();

      cache1.put(fqn, "k", "v");
   }
}
