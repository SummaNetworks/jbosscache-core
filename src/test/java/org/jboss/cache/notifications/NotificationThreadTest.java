package org.jboss.cache.notifications;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.notifications.annotation.*;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertNotSame;
import static org.testng.AssertJUnit.assertSame;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Tests the threading model used when calling notifications
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = "functional", sequential = true, testName = "notifications.NotificationThreadTest")
public class NotificationThreadTest
{
   private Cache<String, String> cache1, cache2;

   private TestCacheListener listener;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      // need 2 caches to test viewChange notifications

      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      Configuration conf1 = new Configuration();
      Configuration conf2 = new Configuration();

      conf1.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      conf2.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      conf1.setSyncCommitPhase(true);
      conf2.setSyncCommitPhase(true);
      conf1.setSyncRollbackPhase(true);
      conf2.setSyncRollbackPhase(true);
      conf1.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      conf2.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      conf1.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyInMemoryCacheLoader.class.getName(), (Properties) null, false, false, false, false, false));
      
      cache1 = instance.createCache(conf1, false, getClass());
      cache2 = instance.createCache(conf2, false, getClass());

      listener = new TestCacheListener();
      cache1.addCacheListener(listener);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   public void testPessimisticWithCacheLoader() throws Throwable
   {
      doTest(false);
   }

   public void testOptimisticWithCacheLoader() throws Throwable
   {
      cache1.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      cache2.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      doTest(false);
   }

   public void testPessimisticWithPassivation() throws Throwable
   {
      cache1.getConfiguration().getCacheLoaderConfig().setPassivation(true);
      doTest(false);
   }

   public void testOptimisticWithPassivation() throws Throwable
   {
      cache1.getConfiguration().getCacheLoaderConfig().setPassivation(true);
      cache1.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      cache2.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      doTest(false);
   }

   public void testPessimisticWithCacheLoaderTx() throws Throwable
   {
      doTest(true);
   }

   public void testOptimisticWithCacheLoaderTx() throws Throwable
   {
      cache1.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      cache2.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      doTest(true);
   }

   public void testPessimisticWithPassivationTx() throws Throwable
   {
      cache1.getConfiguration().getCacheLoaderConfig().setPassivation(true);
      doTest(true);
   }

   public void testOptimisticWithPassivationTx() throws Throwable
   {
      cache1.getConfiguration().getCacheLoaderConfig().setPassivation(true);
      cache1.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      cache2.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      doTest(true);
   }

   private void doTest(boolean tx) throws Throwable
   {
      // stop and start events
      cache1.stop();

      cache1.start();
      cache2.start();

      TransactionManager tm = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();

      listener.sameThreadExpected = true;
      listener.mainThread = Thread.currentThread();
      Fqn fqn = Fqn.fromString("/a/b/c");

      // basic node manipulation events
      if (tx)
         tm.begin();
      cache1.put(fqn, "k", "v");
      if (tx)
         tm.commit();
      if (tx)
         tm.begin();
      cache1.get(fqn, "k");
      if (tx)
         tm.commit();
      if (tx)
         tm.begin();
      cache1.put(fqn, "k", "v2");
      if (tx)
         tm.commit();
      if (tx)
         tm.begin();
      cache1.removeNode(fqn);
      if (tx)
         tm.commit();
      if (tx)
         tm.begin();
      cache1.put(fqn, "k", "v3");
      if (tx)
         tm.commit();
      if (tx)
         tm.begin();
      // eviction
      cache1.evict(fqn, true);
      if (tx)
         tm.commit();
      if (tx)
         tm.begin();
      // and cache loading or activation
      cache1.get(fqn, "k");
      if (tx)
         tm.commit();
      if (tx)
         tm.begin();
      // move event
      cache1.move(fqn, Fqn.ROOT);
      if (tx)
         tm.commit();

      // now a view-change - will be in a different thread
      listener.sameThreadExpected = false;
      cache2.stop();

      // short sleep in case some events are in different threads
      TestingUtil.sleepThread(500);

      // now test for exceptions
      for (Throwable e : listener.exceptions)
         throw e;
   }

   @CacheListener
   public class TestCacheListener
   {
      boolean sameThreadExpected;
      Thread mainThread;
      List<Throwable> exceptions = new LinkedList<Throwable>();

      @NodeCreated
      @NodeModified
      @NodeRemoved
      @NodeVisited
      @NodeEvicted
      @NodeLoaded
      @NodeMoved
      @NodeActivated
      @NodePassivated
      @CacheStarted
      @CacheStopped
      @ViewChanged
      @TransactionCompleted
      @TransactionRegistered
      public void testCallbackThread(Event e)
      {
         try
         {
            if (sameThreadExpected)
               assertSame(mainThread, Thread.currentThread());
            else assertNotSame(mainThread, Thread.currentThread());
         }
         catch (Throwable t)
         {
            exceptions.add(t);
         }
      }
   }

}
