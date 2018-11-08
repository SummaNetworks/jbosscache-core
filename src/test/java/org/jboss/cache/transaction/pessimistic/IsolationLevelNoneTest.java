package org.jboss.cache.transaction.pessimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

/**
 * Tests whether modifications within callbacks (TreeCacheListener) are handled correctly
 *
 * @author Bela Ban
 * @version $Id: IsolationLevelNoneTest.java 7451 2009-01-12 11:38:59Z mircea.markus $
 */
@Test(groups = {"functional", "transaction"}, sequential = true, testName = "transaction.pessimistic.IsolationLevelNoneTest")
public class IsolationLevelNoneTest
{
   CacheSPI<String, String> cache = null;
   final Fqn FQN = Fqn.fromString("/a/b/c");
   final String KEY = "key";
   final String VALUE = "value";
   TransactionManager tm;

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   public void testWithoutTransactions() throws Exception
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(false, getClass());
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setIsolationLevel(IsolationLevel.NONE);
      cache.start();
      cache.put(FQN, KEY, VALUE);
      cache.put(FQN + "/d", KEY, VALUE);
      Node node = cache.peek(FQN, false, false);
      assertTrue(node != null && node.getKeys().contains(KEY));
      assertEquals(VALUE, cache.get(FQN, KEY));
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testWithTransactions() throws Exception
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(false, getClass());
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.getConfiguration().setIsolationLevel(IsolationLevel.NONE);
      cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      cache.start();
      tm = startTransaction();
      cache.put(FQN, KEY, VALUE);
      cache.put(FQN + "/d", KEY, VALUE);
      Node node = cache.peek(FQN, false, false);
      assertTrue(node != null && node.getKeys().contains(KEY));
      assertEquals(VALUE, cache.get(FQN, KEY));
      assertEquals(0, cache.getNumberOfLocksHeld());
      tm.commit();
   }

   public void testWithTransactionsRepeatableRead() throws Exception
   {
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(false, getClass());
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      cache.start();
      tm = startTransaction();
      cache.put(FQN, KEY, VALUE);
      cache.put(FQN + "/d", KEY, VALUE);
      Node node = cache.peek(FQN, false, false);
      assertTrue(node != null && node.getKeys().contains(KEY));
      assertEquals(VALUE, cache.get(FQN, KEY));
      assertEquals(5, cache.getNumberOfLocksHeld());
      tm.commit();
   }

   private TransactionManager startTransaction() throws SystemException, NotSupportedException
   {
      TransactionManager mgr = cache.getTransactionManager();
      mgr.begin();
      return mgr;
   }
}
