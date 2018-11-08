package org.jboss.cache.invalidation;

import org.jboss.cache.*;
import org.jboss.cache.optimistic.DefaultDataVersion;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.config.Configuration;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.assertFalse;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import javax.transaction.Transaction;
import javax.transaction.RollbackException;

/**
 * @author Mircea.Markus@jboss.com
 */                                                   //
@Test( groups = "functional", testName = "invalidation.OptSyncInvalidationTest")
public class OptSyncInvalidationTest extends AbstractMultipleCachesSyncInvalidationTest
{
   protected void createCaches() throws Throwable
   {
      Configuration c = new Configuration();
      c.setStateRetrievalTimeout(3000);
      c.setCacheMode(Configuration.CacheMode.INVALIDATION_SYNC);
      c.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, true, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), true, getClass());
      TestingUtil.blockUntilViewReceived(cache1, 2, 10000);
      registerCaches(cache1, cache2);

   }

   public void testOptSyncUnableToEvict() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");

      cache2.put(fqn, "key", "value");
      assertEquals("value", cache2.get(fqn, "key"));
      Node n = cache1.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n, "Should have been invalidated");
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache1.peek(fqn, true, true), "Should have been invalidated");

      // start a tx that cache1 will have to send out an evict ...
      TransactionManager mgr1 = cache1.getTransactionManager();
      TransactionManager mgr2 = cache2.getTransactionManager();

      mgr1.begin();
      cache1.put(fqn, "key2", "value2");
      Transaction tx1 = mgr1.suspend();
      mgr2.begin();
      cache2.put(fqn, "key3", "value3");
      Transaction tx2 = mgr2.suspend();
      mgr1.resume(tx1);
      // this oughtta fail
      try
      {
         mgr1.commit();
         assertTrue("Ought to have succeeded!", true);
      }
      catch (RollbackException roll)
      {
         assertTrue("Ought to have succeeded!", false);
      }

      mgr2.resume(tx2);
      try
      {
         mgr2.commit();
         assertTrue("Ought to have failed!", false);
      }
      catch (RollbackException roll)
      {
         assertTrue("Ought to have failed!", true);
      }
   }

   public void testOptimistic() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");
      cache1.put(fqn, "key", "value");

      // test that this has NOT replicated, but rather has been invalidated:
      assertEquals("value", cache1.get(fqn, "key"));
      Node n2 = cache2.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n2, "Should have been invalidated");
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache2.peek(fqn, true, true), "Should have been invalidated");

      // now make sure cache2 is in sync with cache1:
      cache2.put(fqn, "key", "value");

      Node n1 = cache1.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n1, "Should have been invalidated");
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache1.peek(fqn, true, true), "Should have been invalidated");

      assertEquals("value", cache2.get(fqn, "key"));

      // now test the invalidation:
      cache1.put(fqn, "key2", "value2");

      assertEquals("value2", cache1.get(fqn, "key2"));
      n2 = cache2.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n2, "Should have been invalidated");
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache2.peek(fqn, false, false), "Should have been invalidated");

      // with tx's
      TransactionManager txm = cache2.getTransactionManager();

      txm.begin();
      cache2.put(fqn, "key", "value");
      assertEquals("value", cache2.get(fqn, "key"));
      assertEquals("value2", cache1.get(fqn, "key2"));
      txm.commit();

      n1 = cache1.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n1, "Should have been invalidated");
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache1.peek(fqn, false, false), "Should have been invalidated");
      assertEquals("value", cache2.get(fqn, "key"));

      // now test the invalidation again
      txm = cache1.getTransactionManager();

      txm.begin();
      cache1.put(fqn, "key2", "value2");
      assertEquals("value", cache2.get(fqn, "key"));
      assertEquals("value2", cache1.get(fqn, "key2"));
      txm.commit();

      assertEquals("value2", cache1.get(fqn, "key2"));
      n2 = cache2.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n2, "Should have been invalidated");
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache2.peek(fqn, false, false), "Should have been invalidated");

      // test a rollback
      txm = cache2.getTransactionManager();

      txm.begin();
      cache2.put(fqn, "key", "value");
      assertEquals("value2", cache1.get(fqn, "key2"));
      assertEquals("value", cache2.get(fqn, "key"));
      txm.rollback();

      assertEquals("value2", cache1.get(fqn, "key2"));
      n2 = cache2.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n2, "Should have been invalidated");
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache2.peek(fqn, false, false), "Should have been invalidated");
   }

   public void dataInconsistency() throws Exception
   {
      Fqn node = Fqn.fromString("/a");
      TransactionManager tm1 = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      TransactionManager tm2 = cache2.getConfiguration().getRuntimeConfig().getTransactionManager();
      tm1.begin();
      cache1.put(node, "k", "v-older");
      Transaction t1 = tm1.suspend();

      tm2.begin();
      cache2.put(node, "k", "v-newer");
      tm2.commit();

      tm1.resume(t1);
      try
      {
         tm1.commit();
         assert false : "Should not be allowed to commit with older data!!";
      }
      catch (Exception good)
      {
      }

      // the NEWER version of the data should be available, not the OLDER one.

      Object val = cache1.get(node, "k");
      assert val == null : "Older data should not have committed";

      val = cache2.get(node, "k");
      assert val.equals("v-newer");

      // test node versions
      NodeSPI n = ((CacheSPI) cache1).peek(node, true, true);
      assert ((DefaultDataVersion) n.getVersion()).getRawVersion() == 1 : "Version should be 1";
   }

}
