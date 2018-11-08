package org.jboss.cache.invalidation;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.*;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import javax.transaction.Transaction;
import javax.transaction.RollbackException;
import java.util.List;
import java.util.ArrayList;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test (groups = "functional", testName = "invalidation.PessAsyncInterceptorTest")
public class PessAsyncInterceptorTest extends AbstractMultipleCachesTest
{
   private CacheSPI<Object, Object> cache1;
   private CacheSPI<Object, Object> cache2;


   protected void createCaches() throws Throwable
   {
      Configuration c = new Configuration();
      c.setStateRetrievalTimeout(3000);
      c.setCacheMode(Configuration.CacheMode.INVALIDATION_ASYNC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, true, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c.clone(), true, getClass());
      TestingUtil.blockUntilViewReceived(cache1, 2, 10000);
      registerCaches(cache1, cache2);
   }

   public void testPessTxAsyncUnableToEvict() throws Exception
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
         assertTrue("Ought to have succeeded!", true);
      }
      catch (RollbackException roll)
      {
         assertTrue("Ought to have succeeded!", false);
      }
   }



}
