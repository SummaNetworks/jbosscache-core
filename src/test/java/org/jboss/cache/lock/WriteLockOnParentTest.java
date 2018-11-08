package org.jboss.cache.lock;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Collections;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

@Test(groups = {"functional"}, sequential =  true, testName = "lock.WriteLockOnParentTest")
public class WriteLockOnParentTest
{
   private CacheSPI<Object, Object> cache;
   private TransactionManager tm;
   private Fqn a = Fqn.fromString("/a"), a_b = Fqn.fromString("/a/b"), a_c = Fqn.fromString("/a/c");

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      // reduce LAT so the test runs faster
      cache.getConfiguration().setLockAcquisitionTimeout(500);

      cache.start();
      tm = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (tm.getTransaction() != null)
      {
         try
         {
            tm.rollback();
         }
         catch (Exception e)
         {
            // do sweet F.A.
         }
      }
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testDefaultCfg()
   {
      assertFalse("Locking of parent nodes for child inserts and removes should be false by default", cache.getConfiguration().isLockParentForChildInsertRemove());
   }

   public void testDefaultChildInsert() throws Exception
   {
      cache.put(a, Collections.emptyMap());

      assertNotNull("/a should exist", cache.peek(a, false));

      // concurrent insert of /a/b and /a/c
      tm.begin();
      cache.put(a_b, Collections.emptyMap());
      Transaction t1 = tm.suspend();

      tm.begin();
      cache.put(a_c, Collections.emptyMap());
      tm.commit();

      tm.resume(t1);
      tm.commit();

      assertNotNull("/a/b should exist", cache.peek(a_b, false));
      assertNotNull("/a/c should exist", cache.peek(a_c, false));
   }

   public void testLockParentChildInsert() throws Exception
   {
      cache.getConfiguration().setLockParentForChildInsertRemove(true);
      cache.put(a, Collections.emptyMap());

      assertNotNull("/a should exist", cache.peek(a, false));

      // concurrent insert of /a/b and /a/c
      tm.begin();
      cache.put(a_b, Collections.emptyMap());
      Transaction t1 = tm.suspend();

      tm.begin();
      try
      {
         cache.put(a_c, Collections.emptyMap());
         fail("Should not get here.");
      }
      catch (TimeoutException e)
      {
         // expected
      }
      tm.commit();

      tm.resume(t1);
      tm.commit();

      assertNotNull("/a/b should exist", cache.peek(a_b, false));
      assertNull("/a/c should not exist", cache.peek(a_c, false));
   }

   public void testDefaultChildRemove() throws Exception
   {
      cache.put(a, Collections.emptyMap());
      cache.put(a_b, Collections.emptyMap());
      cache.put(a_c, Collections.emptyMap());

      assertNotNull("/a should exist", cache.peek(a, false));
      assertNotNull("/a/b should exist", cache.peek(a_b, false));
      assertNotNull("/a/c should exist", cache.peek(a_c, false));

      // concurrent remove of /a/b and /a/c
      tm.begin();
      cache.removeNode(a_b);
      Transaction t1 = tm.suspend();

      tm.begin();
      cache.removeNode(a_c);
      tm.commit();

      tm.resume(t1);
      tm.commit();

      assertNotNull("/a should exist", cache.peek(a, false));
      assertNull("/a/b should not exist", cache.peek(a_b, false));
      assertNull("/a/c should not exist", cache.peek(a_c, false));
   }

   public void testLockParentChildRemove() throws Exception
   {
      cache.getConfiguration().setLockParentForChildInsertRemove(true);

      cache.put(a, Collections.emptyMap());
      cache.put(a_b, Collections.emptyMap());
      cache.put(a_c, Collections.emptyMap());

      assertNotNull("/a should exist", cache.peek(a, false));
      assertNotNull("/a/b should exist", cache.peek(a_b, false));
      assertNotNull("/a/c should exist", cache.peek(a_c, false));

      // concurrent remove of /a/b and /a/c
      tm.begin();
      cache.removeNode(a_b);
      Transaction t1 = tm.suspend();

      tm.begin();
      try
      {
         cache.removeNode(a_c);
         fail("Should not get here.");
      }
      catch (TimeoutException e)
      {
         // expected
      }
      tm.commit();

      tm.resume(t1);
      tm.commit();

      assertNotNull("/a should exist", cache.peek(a, false));
      assertNull("/a/b should not exist", cache.peek(a_b, false));
      assertNotNull("/a/c should exist", cache.peek(a_c, false));
   }

   public void testPerNodeConfigurationDefaultLock() throws Exception
   {
      testPerNodeConfiguration(true);
   }

   public void testPerNodeConfigurationDefaultNoLock() throws Exception
   {
      testPerNodeConfiguration(false);
   }

   private void testPerNodeConfiguration(boolean defaultLock) throws Exception
   {
      cache.getConfiguration().setLockParentForChildInsertRemove(defaultLock);

      cache.put(a, Collections.emptyMap());
      if (!defaultLock)
      {
         // set a per-node lock for a
         cache.getRoot().getChild(a).setLockForChildInsertRemove(true);
      }

      cache.put(a_b, Collections.emptyMap());
      cache.put(a_c, Collections.emptyMap());

      assertNotNull("/a should exist", cache.peek(a, false));
      assertNotNull("/a/b should exist", cache.peek(a_b, false));
      assertNotNull("/a/c should exist", cache.peek(a_c, false));

      // concurrent remove of /a/b and /a/c
      tm.begin();
      cache.removeNode(a_b);
      Transaction t1 = tm.suspend();

      tm.begin();
      try
      {
         cache.removeNode(a_c);
         fail("Should not get here.");
      }
      catch (TimeoutException e)
      {
         // expected
      }
      tm.commit();

      tm.resume(t1);
      tm.commit();

      assertNotNull("/a should exist", cache.peek(a, false));
      assertNull("/a/b should not exist", cache.peek(a_b, false));
      assertNotNull("/a/c should exist", cache.peek(a_c, false));

      Fqn b = Fqn.fromString("/b");
      Fqn b_b = Fqn.fromString("/b/b");
      Fqn b_c = Fqn.fromString("/b/c");
      cache.put(b, Collections.emptyMap());

      if (defaultLock)
      {
         // set a per-node locking config for node b
         cache.getRoot().getChild(b).setLockForChildInsertRemove(false);
      }


      cache.put(b_b, Collections.emptyMap());
      cache.put(b_c, Collections.emptyMap());

      assertNotNull("/a should exist", cache.peek(b, false));
      assertNotNull("/a/b should exist", cache.peek(b_b, false));
      assertNotNull("/a/c should exist", cache.peek(b_c, false));

      // concurrent remove of /a/b and /a/c
      tm.begin();
      cache.removeNode(b_b);
      t1 = tm.suspend();

      tm.begin();
      cache.removeNode(b_c);
      tm.commit();

      tm.resume(t1);
      tm.commit();

      assertNotNull("/b should exist", cache.peek(b, false));
      assertNull("/b/b should not exist", cache.peek(b_b, false));
      assertNull("/b/c should not exist", cache.peek(b_c, false));
   }

}
