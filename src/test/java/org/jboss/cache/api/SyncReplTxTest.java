/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.api;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.Node;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "jgroups", "transaction", "pessimistic"}, sequential = true, testName = "api.SyncReplTxTest")
public class SyncReplTxTest
{
   private List<CacheSPI<Object, Object>> caches;
   protected NodeLockingScheme nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws CloneNotSupportedException
   {
      caches = new ArrayList<CacheSPI<Object, Object>>();
      CacheSPI<Object, Object> cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());

      cache1.getConfiguration().setNodeLockingScheme(nodeLockingScheme);
      cache1.getConfiguration().setSyncCommitPhase(true);
      cache1.getConfiguration().setSyncRollbackPhase(true);
      cache1.getConfiguration().setFetchInMemoryState(false);

      configure(cache1.getConfiguration());

      cache1.start();

      CacheSPI<Object, Object> cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(cache1.getConfiguration().clone(), false, getClass());

      cache2.start();

      caches.add(cache1);
      caches.add(cache2);

      TestingUtil.blockUntilViewsReceived(caches.toArray(new Cache[0]), 10000);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(caches.toArray(new Cache[caches.size()]));
   }

   protected void configure(Configuration c)
   {
      // to be overridden
   }

   private TransactionManager beginTransaction(Cache<Object, Object> cache) throws NotSupportedException, SystemException
   {
      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();
      return mgr;
   }

   public void testBasicOperation() throws SystemException, NotSupportedException, HeuristicMixedException, HeuristicRollbackException, RollbackException
   {
      assertClusterSize("Should only be 2  caches in the cluster!!!", 2);
      assertInvocationContextInitState();

      Fqn f = Fqn.fromString("/test/data");
      String k = "key", v = "value";

      assertNull("Should be null", caches.get(0).getRoot().getChild(f));
      assertNull("Should be null", caches.get(1).getRoot().getChild(f));

      Node<Object, Object> node = caches.get(0).getRoot().addChild(f);

      assertNotNull("Should not be null", node);

      TransactionManager tm = beginTransaction(caches.get(0));
      node.put(k, v);
      Transaction tx1 = caches.get(0).getInvocationContext().getTransaction();
      assertNotNull("Transaction can't be null ", tx1);
      tm.commit();

      assertEquals(v, node.get(k));
      assertEquals(v, caches.get(0).get(f, k));
      assertEquals("Should have replicated", v, caches.get(1).get(f, k));
   }

   private void assertClusterSize(String message, int size)
   {
      for (Cache<Object, Object> c : caches)
      {
         assertClusterSize(message, size, c);
      }
   }

   private void assertClusterSize(String message, int size, Cache<Object, Object> c)
   {
      assertEquals(message, size, c.getMembers().size());
   }

   private void assertInvocationContextInitState()
   {
      for (Cache<Object, Object> c : caches)
      {
         assertInvocationContextInitState(c);
      }
   }

   private void assertInvocationContextInitState(Cache<Object, Object> c)
   {
      InvocationContext ctx = c.getInvocationContext();
      InvocationContext control;
      control = ctx.copy();

      control.reset();

      assertEquals("Should be equal", control, ctx);
   }
}