/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Option;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, sequential = true, testName = "options.CacheModeLocalSimpleTest")
public class CacheModeLocalSimpleTest
{
   private CacheSPI<Object, Object> cache1, cache2;
   private Option cacheModeLocal;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration c = new Configuration();
      c.setCacheMode("REPL_SYNC");
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      c = new Configuration();
      c.setCacheMode("REPL_SYNC");
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      cacheModeLocal = new Option();
      cacheModeLocal.setCacheModeLocal(true);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   public void testCacheModeLocalWithTx() throws Exception
   {
      doTest(false);
   }

   public void testCacheModeLocalOptimisticWithTx() throws Exception
   {
      doTest(true);
   }

   private void doTest(boolean optimistic) throws Exception
   {
      if (optimistic)
      {
         cache1.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
         cache2.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
         cache1.getConfiguration().setSyncCommitPhase(true);
         cache1.getConfiguration().setSyncRollbackPhase(true);
         cache2.getConfiguration().setSyncCommitPhase(true);
         cache2.getConfiguration().setSyncRollbackPhase(true);
      }

      cache1.start();
      cache2.start();

      TestingUtil.blockUntilViewsReceived(10000, cache1, cache2);

      TransactionManager mgr = cache1.getTransactionManager();
      mgr.begin();

      cache1.put(Fqn.fromString("/replicate"), "k", "v");
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put(Fqn.fromString("/not-replicate"), "k", "v");

      mgr.commit();
      Thread.sleep(3000);

      assertEquals("v", cache1.get("/replicate", "k"));
      assertEquals("v", cache1.get("/not-replicate", "k"));

      assertEquals("v", cache2.get("/replicate", "k"));
      assertNull(cache2.get("/not-replicate", "k"));
   }
}
