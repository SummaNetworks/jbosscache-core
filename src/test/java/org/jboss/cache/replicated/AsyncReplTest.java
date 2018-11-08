/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.replicated;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;

/**
 * Unit test for replicated async CacheSPI. Use locking and multiple threads to test
 * concurrent access to the tree.
 *
 * @version $Revision: 7422 $
 */
@Test(groups = {"functional", "jgroups"}, testName = "replicated.AsyncReplTest")
public class AsyncReplTest
{
   private class AsyncReplTestTL {
      private CacheSPI<Object, Object> cache1, cache2;
      private ReplicationListener replListener1, replListener2;
   }
   
   private ThreadLocal<AsyncReplTestTL> threadLocal = new ThreadLocal<AsyncReplTestTL>();

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      AsyncReplTestTL tl = new AsyncReplTestTL();
      threadLocal.set(tl);
      tl.cache1 = createCache("CacheGroup");
      tl.replListener1 = ReplicationListener.getReplicationListener(tl.cache1);

      tl.cache2 = createCache("CacheGroup");
      tl.replListener2 = ReplicationListener.getReplicationListener(tl.cache2);
   }


   private CacheSPI<Object, Object> createCache(String name) throws Exception
   {
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_ASYNC);
      c.setClusterName(name);
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      // Call the hook that allows mux integration
      configureMultiplexer(cache);

      cache.create();
      cache.start();

      validateMultiplexer(cache);

      return cache;
   }

   /**
    * Provides a hook for multiplexer integration. This default implementation
    * is a no-op; subclasses that test mux integration would override
    * to integrate the given cache with a multiplexer.
    * <p/>
    * param cache a cache that has been configured but not yet created.
    */
   protected void configureMultiplexer(Cache cache) throws Exception
   {
      // default does nothing
   }

   /**
    * Provides a hook to check that the cache's channel came from the
    * multiplexer, or not, as expected.  This default impl asserts that
    * the channel did not come from the multiplexer.
    *
    * @param cache a cache that has already been started
    */
   protected void validateMultiplexer(Cache cache)
   {
      assertFalse("Cache is not using multiplexer", cache.getConfiguration().isUsingMultiplexer());
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      AsyncReplTestTL tl = threadLocal.get();
      TestingUtil.killCaches(tl.cache1, tl.cache2);
      threadLocal.set(null);
   }

   public void testTxCompletion() throws Exception
   {
      AsyncReplTestTL tl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = tl.cache1;
      CacheSPI<Object, Object> cache2 = tl.cache2;
      ReplicationListener replListener1 = tl.replListener1;
      ReplicationListener replListener2 = tl.replListener2;
      
      // test a very simple replication.
      Fqn fqn = Fqn.fromString("/a");
      String key = "key";

      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(fqn, key, "value1");
      // allow for replication
      replListener2.waitForReplicationToOccur();
      assertEquals("value1", cache1.get(fqn, key));
      assertEquals("value1", cache2.get(fqn, key));

      TransactionManager mgr = cache1.getTransactionManager();
      mgr.begin();

      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(fqn, key, "value2");
      assertEquals("value2", cache1.get(fqn, key));
      assertEquals("value1", cache2.get(fqn, key));

      mgr.commit();
      replListener2.waitForReplicationToOccur();

      assertEquals("value2", cache1.get(fqn, key));
      assertEquals("value2", cache2.get(fqn, key));

      mgr.begin();
      cache1.put(fqn, key, "value3");
      assertEquals("value3", cache1.get(fqn, key));
      assertEquals("value2", cache2.get(fqn, key));

      mgr.rollback();

      assertEquals("value2", cache1.get(fqn, key));
      assertEquals("value2", cache2.get(fqn, key));
   }

   public void testPutShouldNotReplicateToDifferentCluster()
   {
      AsyncReplTestTL tl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = tl.cache1;
      CacheSPI<Object, Object> cache2 = tl.cache2;
      ReplicationListener replListener1 = tl.replListener1;
      ReplicationListener replListener2 = tl.replListener2;

      CacheSPI<Object, Object> cache3 = null, cache4 = null;
      try
      {
         cache3 = createCache("DifferentGroup");
         cache4 = createCache("DifferentGroup");
         replListener2.expect(PutKeyValueCommand.class);
         cache1.put("/a/b/c", "age", 38);
         // because we use async repl, modfication may not yet have been propagated to cache2, so
         // we have to wait a little
         replListener2.waitForReplicationToOccur(500);
         assertNull("Should not have replicated", cache3.get("/a/b/c", "age"));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
      finally
      {
         if (cache3 != null)
         {
            cache3.stop();
         }
         if (cache4 != null)
         {
            cache4.stop();
         }
      }
   }

   public void testStateTransfer()
   {
      AsyncReplTestTL tl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = tl.cache1;
      CacheSPI<Object, Object> cache2 = tl.cache2;
      ReplicationListener replListener1 = tl.replListener1;
      ReplicationListener replListener2 = tl.replListener2;

      CacheSPI<Object, Object> cache4 = null;
      try
      {
         cache1.put("a/b/c", "age", 38);
         cache4 = createCache("CacheGroup");
         assertEquals(3, cache4.getMembers().size());// cache1, cache2 and cache4
         assertEquals("\"age\" should be 38", 38, cache4.get("/a/b/c", "age"));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
      finally
      {
         if (cache4 != null)
         {
            cache4.stop();
         }
      }
   }

   public void testAsyncReplDelay()
   {
      Integer age;
      AsyncReplTestTL tl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = tl.cache1;
      CacheSPI<Object, Object> cache2 = tl.cache2;
      ReplicationListener replListener1 = tl.replListener1;
      ReplicationListener replListener2 = tl.replListener2;

      try
      {
         cache1.put("/a/b/c", "age", 38);

         // value on cache2 may be 38 or not yet replicated
         age = (Integer) cache2.get("/a/b/c", "age");
         assertTrue("should be either null or 38", age == null || age == 38);
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }

   public void testAsyncReplTxDelay()
   {
      Integer age;
      AsyncReplTestTL tl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = tl.cache1;
      CacheSPI<Object, Object> cache2 = tl.cache2;
      ReplicationListener replListener1 = tl.replListener1;
      ReplicationListener replListener2 = tl.replListener2;

      try
      {
         TransactionManager tm = cache1.getTransactionManager();
         tm.begin();
         cache1.put("/a/b/c", "age", 38);
         tm.commit();

         // value on cache2 may be 38 or not yet replicated
         age = (Integer) cache2.get("/a/b/c", "age");
         assertTrue("should be either null or 38", age == null || age == 38);
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }
}
