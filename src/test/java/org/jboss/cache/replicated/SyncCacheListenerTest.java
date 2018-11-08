/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.replicated;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.annotation.NodeRemoved;
import org.jboss.cache.notifications.event.NodeEvent;
import org.jboss.cache.transaction.DummyTransactionManager;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.naming.Context;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Test out the TreeCacheListener
 *
 * @version $Revision: 7301 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "replicated.SyncCacheListenerTest")
public class SyncCacheListenerTest
{
   private CacheSPI<Object, Object> cache1, cache2;
   private final static Log log_ = LogFactory.getLog(SyncCacheListenerTest.class);
   //private String old_factory = null;
   private final static String FACTORY = "org.jboss.cache.transaction.DummyContextFactory";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      //old_factory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, FACTORY);

      initCaches();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      // We just can't kill DummyTransactionManager. We are sharing single instance in more tests.
      TestingUtil.killTransaction(DummyTransactionManager.getInstance());
      destroyCaches();
      /*
      if (old_factory != null)
      {
         System.setProperty(Context.INITIAL_CONTEXT_FACTORY, old_factory);
         old_factory = null;
      }
       */
   }

   private void initCaches()
   {
      Configuration conf1 = new Configuration();
      Configuration conf2 = new Configuration();
      
      conf1.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      conf2.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      conf1.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      conf2.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      conf1.setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      conf2.setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);

      conf1.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      conf2.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      /*
       cache1.setTransactionManagerLookupClass("org.jboss.cache.transaction.GenericTransactionManagerLookup");
       cache2.setTransactionManagerLookupClass("org.jboss.cache.transaction.GenericTransactionManagerLookup");
       */
      conf1.setLockAcquisitionTimeout(5000);
      conf2.setLockAcquisitionTimeout(5000);
      
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(conf1, false, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(conf2, false, getClass());

      cache1.start();
      cache2.start();
   }

   private void destroyCaches()
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   public void testSyncTxRepl() throws Exception
   {
      Integer age;
      TransactionManager tm = cache1.getTransactionManager();

      tm.begin();
      Transaction tx = tm.getTransaction();
      Listener lis = new Listener();
      cache1.getNotifier().addCacheListener(lis);
      lis.put("/a/b/c", "age", 38);

      tm.suspend();
      assertNull("age on cache2 must be null as the TX has not yet been committed", cache2.get("/a/b/c", "age"));
      tm.resume(tx);
      tm.commit();

      // value on cache2 must be 38
      age = (Integer) cache2.get("/a/b/c", "age");
      assertNotNull("\"age\" obtained from cache2 must be non-null ", age);
      assertTrue("\"age\" must be 38", age == 38);
   }

   public void testRemoteCacheListener() throws Exception
   {
      Integer age;
      RemoteListener lis = new RemoteListener();
      cache2.getNotifier().addCacheListener(lis);
      cache1.put("/a/b/c", "age", 38);

      // value on cache2 must be 38
      age = (Integer) cache2.get("/a/b/c", "age");
      assertNotNull("\"age\" obtained from cache2 must be non-null ", age);
      assertTrue("\"age\" must be 38", age == 38);
      cache1.remove("/a/b/c", "age");
   }

   public void testSyncRepl() throws Exception
   {
      Integer age;
      Listener lis = new Listener();
      cache1.addCacheListener(lis);
      lis.put("/a/b/c", "age", 38);

      // value on cache2 must be 38
      age = (Integer) cache2.get("/a/b/c", "age");
      assertNotNull("\"age\" obtained from cache2 must be non-null ", age);
      assertTrue("\"age\" must be 38", age == 38);
   }

   public void testSyncTxReplMap() throws Exception
   {
      Integer age;
      TransactionManager tm = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      tm.begin();
      Transaction tx = tm.getTransaction();
      Listener lis = new Listener();
      cache1.getNotifier().addCacheListener(lis);
      Map<String, Comparable> map = new HashMap<String, Comparable>();
      map.put("age", 38);
      map.put("name", "Ben");
      lis.put("/a/b/c", map);
      tm.suspend();
      assertNull("age on cache2 must be null as the TX has not yet been committed", cache2.get("/a/b/c", "age"));
      tm.resume(tx);
      tm.commit();

      // value on cache2 must be 38
      age = (Integer) cache2.get("/a/b/c", "age");
      assertNotNull("\"age\" obtained from cache2 must be non-null ", age);
      assertTrue("\"age\" must be 38", age == 38);
   }

   public void testSyncReplMap() throws Exception
   {
      Integer age;

      Listener lis = new Listener();
      cache1.getNotifier().addCacheListener(lis);
      Map<String, Comparable> map = new HashMap<String, Comparable>();
      map.put("age", 38);
      map.put("name", "Ben");
      lis.put("/a/b/c", map);

      // value on cache2 must be 38
      age = (Integer) cache2.get("/a/b/c", "age");
      assertNotNull("\"age\" obtained from cache2 must be non-null ", age);
      assertTrue("\"age\" must be 38", age == 38);
   }

   @CacheListener
   public class Listener
   {
      Object key_ = null;

      public void put(String fqn, Object key, Object val)
      {
         key_ = key;
         cache1.put(fqn, key, val);
      }

      public void put(String fqn, Map map)
      {
         if (map.size() == 0)
            fail("put(): map size can't be 0");
         Set<String> set = map.keySet();
         key_ = set.iterator().next();// take anyone
         cache1.put(fqn, map);
      }

      @NodeModified
      public void nodeModified(NodeEvent ne)
      {
         if (!ne.isPre())
         {
            log_.debug("nodeModified visited with fqn: " + ne.getFqn());
            try
            {
               // test out if we can get the read lock since there is a write lock going as well.
               cache1.get(ne.getFqn(), key_);
            }
            catch (CacheException e)
            {
               e.printStackTrace();//To change body of catch statement use File | Settings | File Templates.
               fail("nodeModified: test failed with exception: " + e);
            }
         }
      }

   }

   @CacheListener
   public class RemoteListener
   {

      @NodeRemoved
      @NodeModified
      public void callback(NodeEvent e)
      {
         log_.debug("Callback got event " + e);
         assertFalse("node was removed on remote cache so isLocal should be false", e.isOriginLocal());
      }
   }

}
