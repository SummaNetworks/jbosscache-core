/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.lock;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.transaction.DummyTransactionManager;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.UserTransaction;
import java.util.Properties;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Tests upgrade locks from read -> write
 *
 * @author Bela Ban
 * @version $Id: UpgradeLockTest.java 7284 2008-12-12 05:00:02Z mircea.markus $
 */
@Test(groups = {"functional"}, sequential = true, testName = "lock.UpgradeLockTest")
public class UpgradeLockTest
{
   CacheSPI<Object, Object> cache = null;
   UserTransaction tx = null;
   Properties p = null;
   //String old_factory = null;
   final String FACTORY = "org.jboss.cache.transaction.DummyContextFactory";
   final String NODE1 = "/test";
   final String NODE2 = "/my/test";
   final String KEY = "key";
   final String VAL1 = "val1";
   final String VAL2 = "val2";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      //old_factory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
      DummyTransactionManager.getInstance();
      if (p == null)
      {
         p = new Properties();
         p.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
      }
      tx = (UserTransaction) new InitialContext(p).lookup("UserTransaction");
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
            
      // We just can't kill DummyTransactionManager. We are sharing single instance in more tests. 
      TestingUtil.killTransaction(DummyTransactionManager.getInstance());
      /*
      if (old_factory != null)
      {
         System.setProperty(Context.INITIAL_CONTEXT_FACTORY, old_factory);
         old_factory = null;
      }
      */
      
      if (tx != null)
      {
         try
         {
            tx.rollback();
         }
         catch (Throwable t)
         {
         }
         tx = null;
      }
   }

   private CacheSPI<Object, Object> createCache(IsolationLevel level)
   {
      CacheSPI<Object, Object> c = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      c.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c.getConfiguration().setClusterName("test");
      c.getConfiguration().setStateRetrievalTimeout(10000);
      c.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.JBossTransactionManagerLookup");
      c.getConfiguration().setLockAcquisitionTimeout(500);
      c.getConfiguration().setIsolationLevel(level);
      c.create();
      c.start();
      return c;
   }


   public void testUpgradeWithNone() throws Exception
   {
      runTestWithIsolationLevel(IsolationLevel.NONE);
   }


   public void testUpgradeWithReadUncommitted() throws Exception
   {
      runTestWithIsolationLevel(IsolationLevel.READ_UNCOMMITTED);
   }

   public void testUpgradeWithReadCommitted() throws Exception
   {
      runTestWithIsolationLevel(IsolationLevel.READ_COMMITTED);
   }

   public void testUpgradeWithRepeatableRead() throws Exception
   {
      runTestWithIsolationLevel(IsolationLevel.REPEATABLE_READ);
   }

   public void testUpgradeWithSerializable() throws Exception
   {
      runTestWithIsolationLevel(IsolationLevel.SERIALIZABLE);
   }

   public void testIsolationLevelSerializable() throws Exception
   {
      _testIsolationLevel(IsolationLevel.SERIALIZABLE);
   }

   public void testIsolationLevelNone() throws Exception
   {
      _testIsolationLevel(IsolationLevel.NONE);
   }


   private void _testIsolationLevel(IsolationLevel l) throws Exception
   {
      cache = createCache(l);
      tx.begin();

      int expected_num_locks = l == IsolationLevel.NONE ? 0 : 2;

      cache.put(NODE1, null);
      assertEquals(expected_num_locks, cache.getNumberOfLocksHeld());

      cache.put(NODE1, null);
      assertEquals(expected_num_locks, cache.getNumberOfLocksHeld());

      tx.rollback();
      assertEquals(0, cache.getNumberOfLocksHeld());
   }


   private void runTestWithIsolationLevel(IsolationLevel level) throws Exception
   {
      cache = createCache(level);
      // add initial values outside of TX
      cache.put(NODE1, KEY, VAL1);
      cache.put(NODE2, KEY, VAL1);

      tx.begin();
      try
      {
         assertEquals(VAL1, cache.get(NODE1, KEY));
         assertEquals(VAL1, cache.get(NODE2, KEY));

         cache.put(NODE1, KEY, VAL2);// causes read lock to upgrade to r/w lock
         cache.put(NODE2, KEY, VAL2);// causes read lock to upgrade to r/w lock
         assertEquals(VAL2, cache.get(NODE1, KEY));
         assertEquals(VAL2, cache.get(NODE2, KEY));
         tx.commit();
      }
      catch (Throwable t)
      {
         if (tx != null)
         {
            tx.rollback();
         }
      }
      assertEquals(VAL2, cache.get(NODE1, KEY));
      assertEquals(VAL2, cache.get(NODE2, KEY));
   }
}
