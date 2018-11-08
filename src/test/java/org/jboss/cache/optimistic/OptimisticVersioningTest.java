/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.optimistic;

import junit.framework.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.Cache;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.config.Configuration;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * Unit test that covers versioning of data and workspace nodes when using optimistic locking.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@SuppressWarnings("unchecked")
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.OptimisticVersioningTest")
public class OptimisticVersioningTest extends AbstractOptimisticTestCase
{
   private static final Log log = LogFactory.getLog(OptimisticVersioningTest.class);

   CacheSPI cache1, cache2;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache1 = createReplicatedCache(Configuration.CacheMode.REPL_SYNC);
      cache2 = createReplicatedCache(Configuration.CacheMode.REPL_SYNC);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      super.tearDown();
     TestingUtil.killCaches((Cache<Object, Object>) cache1);
     TestingUtil.killCaches((Cache<Object, Object>) cache2);
     cache1 = null;
      cache2 = null;
   }

   public void testVersionPropagation()
   {
      Fqn fqn = Fqn.fromString("/a/b");
      String key = "key";

      cache1.put(fqn, key, "value");

      DataVersion v1 = cache1.getNode(fqn).getVersion();
      DataVersion v2 = cache2.getNode(fqn).getVersion();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("Version info should have propagated", v1, v2);

      // change stuff in the node again...
      cache1.put(fqn, key, "value2");

      v1 = cache1.getNode(fqn).getVersion();
      v2 = cache2.getNode(fqn).getVersion();

      assertEquals("value2", cache1.get(fqn, key));
      assertEquals("value2", cache2.get(fqn, key));
      assertEquals("Version info should have propagated", v1, v2);
   }

   public void testTwoCachesUpdatingSimultaneously() throws Exception
   {
      TransactionManager mgr1 = cache1.getTransactionManager();
      TransactionManager mgr2 = cache2.getTransactionManager();
      Transaction tx1, tx2;

      Fqn fqn = Fqn.fromString("/a/b");
      String key = "key";

      cache1.put(fqn, key, "value");

      DataVersion v1 = cache1.getNode(fqn).getVersion();
      DataVersion v2 = cache2.getNode(fqn).getVersion();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("Version info should have propagated", v1, v2);

      // Start a tx on cache 1
      mgr1.begin();
      cache1.put(fqn, key, "value2");
      tx1 = mgr1.suspend();

      // start a tx on cache 2
      mgr2.begin();
      cache2.put(fqn, key, "value3");
      tx2 = mgr2.suspend();

      // which tx completes, which fail?
      mgr1.resume(tx1);
      // should succeed...
      mgr1.commit();

      try
      {
         mgr2.resume(tx2);
         mgr2.commit();
         assertTrue("Should have failed", false);
      }
      catch (RollbackException rbe)
      {
         assertTrue("Should have failed", true);
      }

      // data versions should be in sync.
      v1 = cache1.getNode(fqn).getVersion();
      v2 = cache2.getNode(fqn).getVersion();

      assertEquals("Version info should have propagated", v1, v2);
   }

   public void testRemovalWithSpecifiedVersion() throws Exception
   {
      Fqn fqn = Fqn.fromString("/test/node");

      Node root = cache1.getRoot();
      cache1.getInvocationContext().getOptionOverrides().setDataVersion(new NonLockingDataVersion());
      root.addChild(fqn);
      cache1.getInvocationContext().getOptionOverrides().setDataVersion(new NonLockingDataVersion());
      cache1.removeNode(fqn);

      Assert.assertNull(cache1.getRoot().getChild(fqn));
   }

   private static class NonLockingDataVersion implements DataVersion
   {

      /**
       * The serialVersionUID
       */
      private static final long serialVersionUID = 1L;

      public boolean newerThan(DataVersion dataVersion)
      {

         if (dataVersion instanceof DefaultDataVersion)
         {
            log.info("unexpectedly validating against a DefaultDataVersion", new Exception("Just a stack trace"));
         }
         else
         {
            log.trace("non locking lock check...");
         }
         return false;
      }

   }
}
