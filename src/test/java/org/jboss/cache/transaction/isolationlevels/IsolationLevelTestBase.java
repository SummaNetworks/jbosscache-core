package org.jboss.cache.transaction.isolationlevels;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import static org.jboss.cache.lock.IsolationLevel.*;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Collection;
import java.util.HashSet;

/**
 * Base class for testing isolation levels.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional", "transaction"}, testName = "isolationlevels.IsolationLevelTestBase")
public abstract class IsolationLevelTestBase
{
   protected IsolationLevel isolationLevel;
   protected Cache<String, String> cache;
   protected TransactionManager transactionManager;
   protected Fqn fqn = Fqn.fromString("/a/b/c");
   protected Fqn fqnChild1 = Fqn.fromString("/a/b/c/child1");
   protected Fqn fqnChild2 = Fqn.fromString("/a/b/c/child2");
   protected String k = "key", v = "value";
   protected Collection<IsolationLevel> allowedLevels;

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      UnitTestCacheFactory<String, String> cf = new UnitTestCacheFactory<String, String>();
      cache = cf.createCache(false, getClass());
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
     cache.getConfiguration().setIsolationLevel(isolationLevel);
      cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      // very short so the tests don't take ages
      cache.getConfiguration().setLockAcquisitionTimeout(250);
      cache.start();
      transactionManager = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      allowedLevels = new HashSet<IsolationLevel>();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (transactionManager != null)
      {
         // roll back any ongoing, potentially stuck transactions from failed tests.
         try
         {
            transactionManager.rollback();
         }
         catch (Exception e)
         {
            // no-op
         }
      }
      TestingUtil.killCaches(cache);
      cache = null;
      allowedLevels = null;
   }

   public void testDirtyRead() throws Exception
   {
      // should be allowed in all cases except R_C, R_R and Serializable
      allowedLevels.add(NONE);
      allowedLevels.add(READ_UNCOMMITTED);

      // do a write
      transactionManager.begin();
      cache.put(fqn, k, v);
      Transaction t1 = transactionManager.suspend();

      // and now a simultaneous read
      transactionManager.begin();
      try
      {
         assertEquals(v, cache.get(fqn, k));
         transactionManager.commit();
         if (!allowedLevels.contains(isolationLevel))
         {
            fail("Should have thrown an exception");
         }
      }
      catch (Exception e)
      {
         transactionManager.rollback();
         if (allowedLevels.contains(isolationLevel))
         {
            throw e;
         }
      }

      transactionManager.resume(t1);
      transactionManager.rollback();
   }

   public void testDirtyReadWithNoData() throws Exception
   {
      // should be allowed in all cases except Serializable
      allowedLevels.add(NONE);
      allowedLevels.add(READ_UNCOMMITTED);
      allowedLevels.add(READ_COMMITTED);
      allowedLevels.add(REPEATABLE_READ);

      // do a write
      transactionManager.begin();
      assertNull(cache.get(fqn, k));
      Transaction t1 = transactionManager.suspend();

      // and now a simultaneous read
      transactionManager.begin();
      try
      {
         cache.put(fqn, k, v);
         transactionManager.commit();
         if (!allowedLevels.contains(isolationLevel))
         {
            fail("Should have thrown an exception");
         }
      }
      catch (Exception e)
      {
         transactionManager.rollback();
         if (allowedLevels.contains(isolationLevel))
         {
            throw e;
         }
      }

      transactionManager.resume(t1);
      if (allowedLevels.contains(isolationLevel))
      {
         assertEquals(v, cache.get(fqn, k));
      }
      else
      {
         assertNull(cache.get(fqn, k));
      }
      transactionManager.rollback();
   }

   public void testTwoReads() throws Exception
   {
      // should be allowed in all cases except Serializable
      allowedLevels.add(NONE);
      allowedLevels.add(READ_UNCOMMITTED);
      allowedLevels.add(READ_COMMITTED);
      allowedLevels.add(REPEATABLE_READ);

      // set up some data
      cache.put(fqn, k, v);

      // do a read
      transactionManager.begin();
      assertEquals(v, cache.get(fqn, k));
      Transaction t1 = transactionManager.suspend();

      // and now another simultaneous read
      transactionManager.begin();
      try
      {
         assertEquals(v, cache.get(fqn, k));
         transactionManager.commit();
         if (!allowedLevels.contains(isolationLevel))
         {
            fail("Should have thrown an exception");
         }
      }
      catch (Exception e)
      {
         transactionManager.rollback();
         if (allowedLevels.contains(isolationLevel))
         {
            throw e;
         }
      }

      transactionManager.resume(t1);
      transactionManager.rollback();
   }

   public void testTwoWrites() throws Exception
   {
      // should only be allowed for IsolationLevel.NONE
      allowedLevels.add(NONE);

      // set up some data
      cache.put(fqn, k, v);

      // do a write
      transactionManager.begin();
      cache.put(fqn, k, v);
      Transaction t1 = transactionManager.suspend();

      // and now another simultaneous write
      transactionManager.begin();
      try
      {
         cache.put(fqn, k, v);
         transactionManager.commit();
         if (!allowedLevels.contains(isolationLevel))
         {
            fail("Should have thrown an exception");
         }
      }
      catch (Exception e)
      {
         transactionManager.rollback();
         if (allowedLevels.contains(isolationLevel))
         {
            throw e;
         }
      }

      transactionManager.resume(t1);
      transactionManager.rollback();
   }

   public void testNonRepeatableRead() throws Exception
   {
      // should be allowed in all cases except R_R and Serializable
      allowedLevels.add(NONE);
      allowedLevels.add(READ_UNCOMMITTED);
      allowedLevels.add(READ_COMMITTED);

      // set up some data
      cache.put(fqn, k, v);

      // do a read
      transactionManager.begin();
      assertEquals(v, cache.get(fqn, k));
      Transaction t1 = transactionManager.suspend();

      // and now a simultaneous write
      transactionManager.begin();
      try
      {
         cache.put(fqn, k, v);
         transactionManager.commit();
         if (!allowedLevels.contains(isolationLevel))
         {
            fail("Should have thrown an exception");
         }
      }
      catch (Exception e)
      {
         transactionManager.rollback();
         if (allowedLevels.contains(isolationLevel))
         {
            throw e;
         }
      }

      transactionManager.resume(t1);
      assertEquals(v, cache.get(fqn, k));
      transactionManager.rollback();
   }

   public void testNonRepeatableReadWithNoData() throws Exception
   {
      // should be allowed in all cases except R_R and Serializable
      // This still does happen with R_R though since the database analogy breaks down here.
      // Since the node does not exist, it cannot be locked for repeatable read.
      // See http://www.jboss.com/index.html?module=bb&op=viewtopic&p=4036036

      allowedLevels.add(NONE);
      allowedLevels.add(READ_UNCOMMITTED);
      allowedLevels.add(READ_COMMITTED);
      allowedLevels.add(REPEATABLE_READ);

      // do a read
      transactionManager.begin();
      assertNull(cache.get(fqn, k));
      Transaction t1 = transactionManager.suspend();

      // and now a simultaneous write
      transactionManager.begin();
      try
      {
         cache.put(fqn, k, v);
         transactionManager.commit();
         if (!allowedLevels.contains(isolationLevel))
         {
            fail("Should have thrown an exception");
         }
      }
      catch (Exception e)
      {
         transactionManager.rollback();
         if (allowedLevels.contains(isolationLevel))
         {
            throw e;
         }
      }

      transactionManager.resume(t1);
      if (allowedLevels.contains(isolationLevel))
      {
         assertEquals(v, cache.get(fqn, k));
      }
      else
      {
         assertNull(cache.get(fqn, k));
      }
      transactionManager.rollback();
   }

   public void testPhantomRead() throws Exception
   {
      // should be allowed in all cases except Serializable
      allowedLevels.add(NONE);
      allowedLevels.add(READ_UNCOMMITTED);
      allowedLevels.add(READ_COMMITTED);
      allowedLevels.add(REPEATABLE_READ);

      // set up some data
      cache.put(fqn, k, v);
      cache.put(fqnChild1, k, v);

      // do a read
      transactionManager.begin();
      int numChildren = cache.getRoot().getChild(fqn).getChildren().size();
      assertEquals(1, numChildren);
      Transaction t1 = transactionManager.suspend();

      // and now a simultaneous write
      transactionManager.begin();
      try
      {
         cache.put(fqnChild2, k, v);
         transactionManager.commit();
         if (!allowedLevels.contains(isolationLevel))
         {
            fail("Should have thrown an exception");
         }
      }
      catch (Exception e)
      {
         transactionManager.rollback();
         if (allowedLevels.contains(isolationLevel))
         {
            throw e;
         }
      }

      transactionManager.resume(t1);
      numChildren = cache.getRoot().getChild(fqn).getChildren().size();
      assertEquals(allowedLevels.contains(isolationLevel) ? 2 : 1, numChildren);
      transactionManager.rollback();
   }
}
