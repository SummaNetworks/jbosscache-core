/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.lock;

import org.apache.commons.logging.Log;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.transaction.GenericTransactionManagerLookup;
import org.jboss.cache.transaction.TransactionSetup;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.UserTransaction;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Verifies that there are no read locks held when a transaction ends.
 *
 * @author Bela Ban
 * @version $Id: LockReleaseTest.java 7333 2008-12-16 13:46:43Z mircea.markus $
 */
@Test(groups = {"functional"}, sequential = true, testName = "lock.LockReleaseTest")
public class LockReleaseTest
{
   CacheSPI<Object, Object> cache = null;
   UserTransaction tx = null;
   Log log;
   final Fqn NODE1 = Fqn.fromString("/test");
   final Fqn NODE2 = Fqn.fromString("/my/test");
   final String KEY = "key";
   final String VAL1 = "val1";
   final String VAL2 = "val2";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      tx = TransactionSetup.getUserTransaction();
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
      TestingUtil.killTransaction(TransactionSetup.getManager());      

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

   CacheSPI<Object, Object> createCache(IsolationLevel level) throws Exception
   {
      CacheSPI<Object, Object> c = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      c.getConfiguration().setClusterName("test");
      c.getConfiguration().setStateRetrievalTimeout(10000);
      c.getConfiguration().setTransactionManagerLookupClass(GenericTransactionManagerLookup.class.getName());
      c.getConfiguration().setLockAcquisitionTimeout(500);
      c.getConfiguration().setIsolationLevel(level);
      c.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      c.create();
      c.start();
      return c;
   }


   public void testReadWithReadUncommitted() throws Exception
   {
      testReadLockRelease(IsolationLevel.READ_UNCOMMITTED);
   }

   public void testWriteWithReadUncommitted() throws Exception
   {
      testWriteLockRelease(IsolationLevel.READ_UNCOMMITTED);
   }


   public void testReadWithReadCommitted() throws Exception
   {
      testReadLockRelease(IsolationLevel.READ_COMMITTED);
   }

   public void testWriteWithReadCommitted() throws Exception
   {
      testWriteLockRelease(IsolationLevel.READ_COMMITTED);
   }


   public void testReadWithRepeatableRead() throws Exception
   {
      testReadLockRelease(IsolationLevel.REPEATABLE_READ);
   }

   public void testWriteWithRepeatableRead() throws Exception
   {
      testWriteLockRelease(IsolationLevel.REPEATABLE_READ);
   }


   public void testReadWithSerialzable() throws Exception
   {
      testReadLockRelease(IsolationLevel.SERIALIZABLE);
   }

   public void testWriteWithSerializable() throws Exception
   {
      testWriteLockRelease(IsolationLevel.SERIALIZABLE);
   }


   public void testGetKeys() throws Exception
   {
      cache = createCache(IsolationLevel.REPEATABLE_READ);
      // add initial values outside of TX
      cache.put(NODE1, KEY, VAL1);
      cache.put(NODE2, KEY, VAL1);
      assertEquals("we ran outside of a TX, locks should have been released: ", 0, cache.getNumberOfLocksHeld());

      Set<Object> keys = cache.getNode(NODE1).getKeys();
      assertEquals("getKeys() called outside the TX should have released all locks", 0, cache.getNumberOfLocksHeld());

      tx.begin();
      keys = cache.getNode(NODE1).getKeys();
      assertEquals("we should hold 1 read locks now: ", 2, cache.getNumberOfLocksHeld());
      keys = cache.getNode(NODE2).getKeys();
      assertEquals("we should hold 3 read locks now: ", 4, cache.getNumberOfLocksHeld());
      tx.commit();
      assertEquals("we should have released all 3 read locks: ", 0, cache.getNumberOfLocksHeld());
   }


   public void testGetChildrenNames() throws Exception
   {
      cache = createCache(IsolationLevel.REPEATABLE_READ);
      // add initial values outside of TX
      cache.put(NODE1, KEY, VAL1);
      cache.put(NODE2, KEY, VAL1);
      assertEquals("we ran outside of a TX, locks should have been released: ", 0, cache.getNumberOfLocksHeld());

      Set<Object> keys = cache.getNode(NODE2).getChildrenNames();
      assertEquals("getChildrenNames() called outside the TX should have released all locks", 0,
            cache.getNumberOfLocksHeld());

      tx.begin();
      keys = cache.getNode(NODE1).getChildrenNames();
      assertEquals("we should hold 1 read locks now: ", 2, cache.getNumberOfLocksHeld());
      keys = cache.getNode(NODE2).getChildrenNames();
      assertEquals("we should hold 3 read locks now: ", 4, cache.getNumberOfLocksHeld());
      tx.commit();
      assertEquals("we should have released all 3 read locks: ", 0, cache.getNumberOfLocksHeld());
   }

   public void testPrint() throws Exception
   {
      cache = createCache(IsolationLevel.REPEATABLE_READ);
      // add initial values outside of TX
      cache.put(NODE1, KEY, VAL1);
      cache.put(NODE2, KEY, VAL1);
      assertEquals("we ran outside of a TX, locks should have been released: ", 0, cache.getNumberOfLocksHeld());

      cache.getNode(NODE1);
      assertEquals("print() called outside the TX should have released all locks", 0, cache.getNumberOfLocksHeld());

      tx.begin();
      cache.getNode(NODE1);
      assertEquals("we should hold 1 read locks now (for print()): ", 2, cache.getNumberOfLocksHeld());
      cache.getNode(NODE2);
      assertEquals("we should hold 3 read locks now (for print()): ", 4, cache.getNumberOfLocksHeld());
      tx.commit();
      assertEquals("we should have released all 3 read locks: ", 0, cache.getNumberOfLocksHeld());
   }


   private void testReadLockRelease(IsolationLevel level) throws Exception
   {
      cache = createCache(level);
      // add initial values outside of TX
      cache.put(NODE1, KEY, VAL1);
      cache.put(NODE2, KEY, VAL1);

      assertEquals("we ran outside of a TX, locks should have been released: ", 0, cache.getNumberOfLocksHeld());

      tx.begin();
      assertEquals(VAL1, cache.get(NODE1, KEY));
      assertEquals(VAL1, cache.get(NODE2, KEY));
      assertEquals("we should hold 3 read locks now: ", 4, cache.getNumberOfLocksHeld());
      tx.commit();
      assertEquals("we should have released all 3 read locks: ", 0, cache.getNumberOfLocksHeld());
   }

   private void testWriteLockRelease(IsolationLevel level) throws Exception
   {
      cache = createCache(level);
      // add initial values outside of TX
      cache.put(NODE1, KEY, VAL1);
      cache.put(NODE2, KEY, VAL1);

      assertEquals("we ran outside of a TX, locks should have been released: ", 0, cache.getNumberOfLocksHeld());

      tx.begin();
      cache.put(NODE1, KEY, VAL1);
      cache.put(NODE2, KEY, VAL1);
      assertEquals("we should hold 3 write locks now: ", 4, cache.getNumberOfLocksHeld());
      tx.commit();
      assertEquals("we should have released all 3 write locks: ", 0, cache.getNumberOfLocksHeld());
   }

   /**
    * Tests that when an acquisition timeout occurs locks are being released.
    */
   public void testNodeReleaseOnAcquisitionTimeout() throws Exception
   {
      cache = createCache(IsolationLevel.REPEATABLE_READ);
      cache.put("/a/b", "key", "value");
      cache.put("/c", "key", "value");
      final CountDownLatch rLockAcquired = new CountDownLatch(1);
      final CountDownLatch wlTimeouted = new CountDownLatch(1);
      final CountDownLatch txLocksReleased = new CountDownLatch(1);


      Thread thread = new Thread()
      {
         public void run()
         {
            try
            {
               cache.getTransactionManager().begin();
               cache.get("/a/b", "key"); //at this point we have an RL on /c and /c/d
               rLockAcquired.countDown();
               wlTimeouted.await(60, TimeUnit.SECONDS); //wait a long time but die eventually

               cache.getTransactionManager().commit();//here we are releasing locks

               txLocksReleased.countDown();
            }
            catch (Exception ex)
            {
               ex.printStackTrace();
            }
         }
      };

      thread.start();

      rLockAcquired.await();

      try
      {
         cache.move("/a/b", "c"); //acquired RL on /a and /a/b
         fail("expected timeout here");
      }
      catch (TimeoutException e)
      {
         wlTimeouted.countDown();
      }

      txLocksReleased.await(); //wait for tx locks to be released

      assertEquals(0, cache.getNumberOfLocksHeld());
   }
}
