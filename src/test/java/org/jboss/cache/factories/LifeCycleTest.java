package org.jboss.cache.factories;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.CacheStatus;
import org.jboss.cache.Fqn;
import org.jboss.cache.ReplicationException;
import org.jboss.cache.SuspectException;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.CacheStarted;
import org.jboss.cache.notifications.annotation.CacheStopped;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.util.TestingUtil;
import org.testng.AssertJUnit;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Tests restart (stop-destroy-create-start) of ComponentRegistry
 *
 * @author Bela Ban
 * @version $Id: LifeCycleTest.java 7332 2008-12-16 13:44:26Z mircea.markus $
 */
@Test(groups = "functional", sequential = true, testName = "factories.LifeCycleTest")
public class LifeCycleTest
{
   private CacheSPI<Object, Object>[] c;

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(c);
      c = null;
   }

   @SuppressWarnings("unchecked")
   private void createAndRegisterCache(Configuration.CacheMode mode, boolean start) throws Exception
   {
      CacheSPI<Object, Object> cache = createCache(mode);
      List<CacheSPI<Object, Object>> caches = new LinkedList<CacheSPI<Object, Object>>();
      if (c != null) caches.addAll(Arrays.asList(c));
      caches.add(cache);
      c = caches.toArray(new CacheSPI[]{});
      if (start)
      {
         cache.start();
         if (c.length > 1) TestingUtil.blockUntilViewsReceived(c, 10000);
      }
   }


   public void testLocalRestartNoTransactions() throws Exception
   {
      createAndRegisterCache(Configuration.CacheMode.LOCAL, true);

      c[0].put("/a/b/c", null);
      assertTrue(c[0].getNumberOfNodes() > 0);
      assertEquals(0, c[0].getNumberOfLocksHeld());

      restartCache(c[0]);

      assertEquals(0, c[0].getNumberOfNodes());
      assertEquals(0, c[0].getNumberOfLocksHeld());
   }

   public void testLocalRestartWithTransactionsPessimistic() throws Exception
   {
      createAndRegisterCache(Configuration.CacheMode.LOCAL, false);
      c[0].getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c[0].start();

      TransactionManager tm = beginTransaction();

      c[0].put("/a/b/c", null);
      assertTrue(c[0].getNumberOfNodes() > 0);
      assertEquals(4, c[0].getNumberOfLocksHeld());

      restartCache(c[0]);

      //assertEquals(4, cache.getNumberOfLocksHeld());
      assertEquals(0, c[0].getNumberOfNodes());

      tm.rollback();
      assertEquals(0, c[0].getNumberOfLocksHeld());
   }

   public void testStartNoCreate() throws Exception
   {
      createAndRegisterCache(Configuration.CacheMode.LOCAL, false);
      c[0].start();

      c[0].put("/a/b/c", null);
      assertTrue(c[0].getNumberOfNodes() > 0);
      assertEquals(0, c[0].getNumberOfLocksHeld());

      restartCache(c[0]);

      assertEquals(0, c[0].getNumberOfNodes());
      assertEquals(0, c[0].getNumberOfLocksHeld());
   }

   public void testReStartNoCreate() throws Exception
   {
      createAndRegisterCache(Configuration.CacheMode.LOCAL, false);
      c[0].start();
      c[0].stop();
      c[0].start();

      c[0].put("/a/b/c", null);
      assertTrue(c[0].getNumberOfNodes() > 0);
      assertEquals(0, c[0].getNumberOfLocksHeld());

      restartCache(c[0]);

      assertEquals(0, c[0].getNumberOfNodes());
      assertEquals(0, c[0].getNumberOfLocksHeld());
   }

   public void testDuplicateInvocation() throws Exception
   {
      createAndRegisterCache(Configuration.CacheMode.LOCAL, false);
      c[0].create();
      c[0].start();
      c[0].create();
      c[0].start();

      c[0].put("/a/b/c", null);
      assertTrue(c[0].getNumberOfNodes() > 0);
      assertEquals(0, c[0].getNumberOfLocksHeld());

      restartCache(c[0]);

      assertEquals(0, c[0].getNumberOfNodes());
      assertEquals(0, c[0].getNumberOfLocksHeld());

      c[0].stop();
      c[0].destroy();
      c[0].stop();
      c[0].destroy();
   }

   public void testFailedStart() throws Exception
   {

      createAndRegisterCache(Configuration.CacheMode.LOCAL, false);
      AssertJUnit.assertEquals("Correct state", CacheStatus.INSTANTIATED, c[0].getCacheStatus());

      DisruptLifecycleListener listener = new DisruptLifecycleListener();
      c[0].addCacheListener(listener);

      c[0].create();

      listener.disrupt = true;

      assertEquals("Correct state", CacheStatus.CREATED, c[0].getCacheStatus());
      try
      {
         c[0].start();
         fail("Listener did not prevent start");
      }
      catch (CacheException good)
      {
      }

      assertEquals("Correct state", CacheStatus.FAILED, c[0].getCacheStatus());

      c[0].addCacheListener(listener);
      listener.disrupt = false;

      c[0].start();

      assertEquals("Correct state", CacheStatus.STARTED, c[0].getCacheStatus());

      c[0].put("/a/b/c", null);
      assertTrue(c[0].getNumberOfNodes() > 0);
      assertEquals(0, c[0].getNumberOfLocksHeld());

      listener.disrupt = true;
      c[0].addCacheListener(listener);

      try
      {
         c[0].stop();
         fail("Listener did not prevent stop");
      }
      catch (CacheException good)
      {
      }

      assertEquals("Correct state", CacheStatus.FAILED, c[0].getCacheStatus());

      listener.disrupt = false;

      c[0].stop();
      assertEquals("Correct state", CacheStatus.STOPPED, c[0].getCacheStatus());
      c[0].destroy();
      assertEquals("Correct state", CacheStatus.DESTROYED, c[0].getCacheStatus());
   }

   public void testInvalidStateInvocations() throws Exception
   {
      createAndRegisterCache(Configuration.CacheMode.LOCAL, false);
      try
      {
         c[0].get(Fqn.ROOT, "k");
         fail("Cache isn't ready!");
      }
      catch (IllegalStateException good)
      {
      }

      c[0].create();
      try
      {
         c[0].get(Fqn.ROOT, "k");
         fail("Cache isn't ready!");
      }
      catch (IllegalStateException good)
      {
      }

      c[0].start();
      c[0].get(Fqn.ROOT, "k"); // should work

      c[0].stop();

      try
      {
         c[0].get(Fqn.ROOT, "k");
         fail("Cache isn't ready!");
      }
      catch (IllegalStateException good)
      {
      }

      c[0].destroy();
      try
      {
         c[0].get(Fqn.ROOT, "k");
         fail("Cache isn't ready!");
      }
      catch (IllegalStateException good)
      {
      }
   }



   public void testInvalidStateTxCommit() throws Exception
   {
      createAndRegisterCache(Configuration.CacheMode.LOCAL, true);
      c[0].getTransactionManager().begin();
      c[0].put(Fqn.ROOT, "k1", "v1");
      c[0].put(Fqn.ROOT, "k2", "v2");

      // now DIRECTLY change the status of c.
      ComponentRegistry cr0 = TestingUtil.extractComponentRegistry(c[0]);
      cr0.state = CacheStatus.STOPPING;

      try
      {
         c[0].getTransactionManager().commit();
         fail("Cache isn't STARTED!");
      }
      catch (RollbackException good)
      {
      }
   }

   public void testInvalidStateTxRollback() throws Exception
   {
      createAndRegisterCache(Configuration.CacheMode.LOCAL, true);
      c[0].getTransactionManager().begin();
      c[0].put(Fqn.ROOT, "k1", "v1");
      c[0].put(Fqn.ROOT, "k2", "v2");

      // now DIRECTLY change the status of c.
      ComponentRegistry cr0 = TestingUtil.extractComponentRegistry(c[0]);
      cr0.state = CacheStatus.STOPPING;

      // rollbacks should just log a message
      c[0].getTransactionManager().rollback();
   }


   private CacheSPI<Object, Object> createCache(Configuration.CacheMode cache_mode)
   {
      Configuration c = new Configuration();
      c.setCacheMode(cache_mode);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      CacheSPI<Object, Object> retval = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      return retval;
   }


   private TransactionManager beginTransaction() throws SystemException, NotSupportedException
   {
      TransactionManager mgr = c[0].getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();
      return mgr;
   }


   private void startCache(CacheSPI c)
   {
      c.create();
      c.start();
   }

   private void stopCache(CacheSPI c)
   {
      c.stop();
      c.destroy();
   }

   private void restartCache(CacheSPI c) throws Exception
   {
      stopCache(c);
      startCache(c);
   }

   @CacheListener
   public class DisruptLifecycleListener
   {
      private boolean disrupt;

      @CacheStarted
      public void cacheStarted(Event e)
      {
         if (disrupt) throw new IllegalStateException("I don't want to start");
      }

      @CacheStopped
      public void cacheStopped(Event e)
      {
         if (disrupt) throw new IllegalStateException("I don't want to stop");
      }

      public void setDisrupt(boolean disrupt)
      {
         this.disrupt = disrupt;
      }
   }
}
