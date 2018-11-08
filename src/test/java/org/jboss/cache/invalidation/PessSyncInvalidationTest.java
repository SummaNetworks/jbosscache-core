package org.jboss.cache.invalidation;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.optimistic.DefaultDataVersion;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional", testName = "invalidation.PessSyncInvalidationTest")
public class PessSyncInvalidationTest extends AbstractMultipleCachesSyncInvalidationTest
{

   protected void createCaches() throws Throwable
   {
      Configuration c = new Configuration();
      c.setStateRetrievalTimeout(3000);
      c.setCacheMode(Configuration.CacheMode.INVALIDATION_SYNC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, true, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), true, getClass());
      TestingUtil.blockUntilViewReceived(cache1, 2, 10000);
      registerCaches(cache1, cache2);
   }

   public void testPessimisticNonTransactional() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");
      cache1.put(fqn, "key", "value");

      // test that this has NOT replicated, but rather has been invalidated:
      assertEquals("value", cache1.get(fqn, "key"));
      assertNull("Should NOT have replicated!", cache2.getNode(fqn));

      // now make sure cache2 is in sync with cache1:
      cache2.put(fqn, "key", "value");

      // since the node already exists even PL will not remove it - but will invalidate it's data
      Node n = cache1.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n, "Should have been invalidated");
      assertEquals("value", cache2.get(fqn, "key"));

      // now test the invalidation:
      cache1.put(fqn, "key2", "value2");
      assertEquals("value2", cache1.get(fqn, "key2"));
      n = cache2.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n, "Should have been invalidated");
   }


   public void testUnnecessaryEvictions() throws Exception
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");

      cache1.put(fqn1, "hello", "world");

      assertEquals("world", cache1.get(fqn1, "hello"));
      assertNull(cache2.get(fqn1, "hello"));

      cache2.put(fqn2, "hello", "world");
      assertEquals("world", cache1.get(fqn1, "hello"));
      assertNull(cache2.get(fqn1, "hello"));
      assertEquals("world", cache2.get(fqn2, "hello"));
      assertNull(cache1.get(fqn2, "hello"));

      cache2.put(fqn1, "hello", "world");
      assertEquals("world", cache2.get(fqn1, "hello"));
      assertEquals("world", cache2.get(fqn2, "hello"));
      assertNull(cache1.get(fqn1, "hello"));
      assertNull(cache1.get(fqn2, "hello"));
   }

   public void testPessimisticTransactional() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");
      cache1.put(fqn, "key", "value");

      // test that this has NOT replicated, but rather has been invalidated:
      assertEquals("value", cache1.get(fqn, "key"));
      assertNull("Should NOT have replicated!", cache2.getNode(fqn));

      // now make sure cache2 is in sync with cache1:
      // make sure this is in a tx
      TransactionManager txm = cache2.getTransactionManager();
      assertEquals("value", cache1.get(fqn, "key"));

      txm.begin();
      cache2.put(fqn, "key", "value");
      assertEquals("value", cache2.get(fqn, "key"));
      txm.commit();

      // since the node already exists even PL will not remove it - but will invalidate it's data
      Node n = cache1.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n, "Should have been invalidated");
      assertEquals("value", cache2.get(fqn, "key"));

      // now test the invalidation again
      txm = cache1.getTransactionManager();
      assertEquals("value", cache2.get(fqn, "key"));

      txm.begin();
      cache1.put(fqn, "key2", "value2");
      assertEquals("value2", cache1.get(fqn, "key2"));
      txm.commit();

      assertEquals("value2", cache1.get(fqn, "key2"));
      // since the node already exists even PL will not remove it - but will invalidate it's data
      n = cache2.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n, "Should have been invalidated");

      // test a rollback
      txm = cache2.getTransactionManager();
      assertEquals("value2", cache1.get(fqn, "key2"));

      txm.begin();
      cache2.put(fqn, "key", "value");
      assertEquals("value", cache2.get(fqn, "key"));
      txm.rollback();

      assertEquals("value2", cache1.get(fqn, "key2"));
      n = cache2.getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n, "Should have been invalidated");
   }

   public void testPessTxSyncUnableToEvict() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");

      cache1.put("/a/b", "key", "value");
      assertEquals("value", cache1.get(fqn, "key"));
      assertNull(cache2.getNode(fqn));

      // start a tx that cacahe1 will have to send out an evict ...
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
         assertTrue("Ought to have failed!", false);
      }
      catch (RollbackException roll)
      {
         assertTrue("Ought to have failed!", true);
      }

      mgr2.resume(tx2);
      try
      {
         mgr2.commit();
         assertTrue("Ought to have succeeded!", true);
      }
      catch (RollbackException roll)
      {
         assertTrue("Ought to have succeeded!", false);
      }
   }

   /**
    * Test for JBCACHE-1298.
    *
    * @throws Exception
    */
   public void testAddOfDeletedNonExistent() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");

      assertNull("Should be null", cache1.getNode(fqn));
      assertNull("Should be null", cache2.getNode(fqn));

      // OK, here's the real test
      TransactionManager tm = cache2.getTransactionManager();
      tm.begin();
      try
      {
         // Remove a node that doesn't exist in cache2
         cache2.removeNode(fqn);
         tm.commit();
      }
      catch (Exception e)
      {
         String msg = "Unable to remove non-existent node " + fqn;
         fail(msg + " -- " + e);
      }

      // Actually, it shouldn't have been invalidated, should be null
      // But, this assertion will pass if it is null, and we want
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache1.getNode(fqn), "Should have been invalidated");
      assertNull("Should be null", cache2.getNode(fqn));

      cache1.getInvocationContext().getOptionOverrides().setDataVersion(new DefaultDataVersion());
      cache1.put(fqn, "key", "value");

      assertEquals("value", cache1.getNode(fqn).get("key"));
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(cache2.getNode(fqn), "Should have been invalidated");
   }

}
