package org.jboss.cache;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeCreated;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.util.CachePrinter;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * Tests whether modifications within callbacks (TreeCacheListener) are handled correctly
 *
 * @author Bela Ban
 * @version $Id: CallbackTest.java 7305 2008-12-12 08:49:20Z mircea.markus $
 */
@Test(groups = "functional", testName = "CallbackTest")
public class CallbackTest
{
   CacheSPI<Object, Object> cache = null;
   final Fqn FQN_A = Fqn.fromString("/a");
   final Fqn FQN_B = Fqn.fromString("/b");
   static final String KEY = "key";
   static final String VALUE = "value";

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
   }

   public void testLocalPutCallbackWithoutTransaction() throws Exception
   {
      cache = createCache(Configuration.CacheMode.LOCAL, IsolationLevel.SERIALIZABLE);
      cache.addCacheListener(new PutListener(cache));

      cache.put(FQN_A, null);
      assertTrue(cache.exists(FQN_A));
      assertTrue(cache.exists(FQN_B));//created by callback
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testLocalGetCallbackSameFqnWithoutTransaction() throws Exception
   {
      cache = createCache(Configuration.CacheMode.LOCAL, IsolationLevel.SERIALIZABLE);
      cache.getNotifier().addCacheListener(new GetListener(cache, FQN_A));

      cache.put(FQN_A, null);
      assertTrue(cache.exists(FQN_A));
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testLocalGetCallbackDifferentFqnWithoutTransaction() throws Exception
   {
      cache = createCache(Configuration.CacheMode.LOCAL, IsolationLevel.SERIALIZABLE);
      cache.put(FQN_B, null);
      cache.getNotifier().addCacheListener(new GetListener(cache, FQN_B));

      cache.put("/a", null);
      assertTrue(cache.exists(FQN_A));
      assertTrue(cache.exists(FQN_B));
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testLocalCallbackWithTransaction() throws Exception
   {
      cache = createCache(Configuration.CacheMode.LOCAL, IsolationLevel.SERIALIZABLE);
      cache.getNotifier().addCacheListener(new PutListener(cache));

      TransactionManager tm = startTransaction();
      cache.put(FQN_A, null);
      tm.commit();
      assertTrue(cache.exists(FQN_A));
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testLocalCallbackWithException() throws Exception
   {
      cache = createCache(Configuration.CacheMode.LOCAL, IsolationLevel.SERIALIZABLE);
      cache.getNotifier().addCacheListener(new ExceptionListener());
      TransactionManager tm = startTransaction();
      try
      {
         cache.put(FQN_A, null);
         tm.rollback();
      }
      catch (RuntimeException ex)
      {
         tm.rollback();
      }
      assertFalse(cache.exists(FQN_A));
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   private CacheSPI<Object, Object> createCache(Configuration.CacheMode mode, IsolationLevel level)
   {
      Configuration c = new Configuration();
      c.setCacheMode(mode);
      c.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c.setIsolationLevel(level);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      return (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
   }

   private TransactionManager startTransaction()
   {
      TransactionManager mgr = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      try
      {
         mgr.begin();
         return mgr;
      }
      catch (Throwable t)
      {
         return null;
      }
   }

   @CacheListener
   public class ExceptionListener
   {
      @NodeCreated
      public void nodeCreated(Event e)
      {
         if (e.isPre()) throw new RuntimeException("this will cause the TX to rollback");
      }
   }


   @CacheListener
   public class GetListener
   {
      CacheSPI<Object, Object> c;
      Fqn my_fqn;

      public GetListener(CacheSPI<Object, Object> c, Fqn my_fqn)
      {
         this.c = c;
         this.my_fqn = my_fqn;
      }

      @NodeCreated
      public void nodeCreated(Event e)
      {
         if (!e.isPre())
         {
            try
            {
               Node<?, ?> n = c.getNode(this.my_fqn);
               assertNotNull(n);
            }
            catch (CacheException ex)
            {
               fail("listener was unable to do a get(" + my_fqn + ") during callback: " + ex);
            }
         }
      }

   }

   @CacheListener
   public class PutListener
   {
      CacheSPI<Object, Object> c;

      public PutListener(CacheSPI<Object, Object> c)
      {
         this.c = c;
      }

      @NodeCreated
      public void nodeCreated(Event e)
      {
         if (!e.isPre())
         {
            try
            {
               if (!c.exists(FQN_B))
               {
                  c.put(FQN_B, KEY, VALUE);
               }
            }
            catch (CacheException ex)
            {
               fail("listener was unable to update cache during callback: " + ex);
            }
         }
      }

   }
}
