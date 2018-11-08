/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.invalidation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tests the async interceptor
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "jgroups"}, testName = "invalidation.CacheLoaderInvalidationTest")
public class CacheLoaderInvalidationTest
{
   private static Log log = LogFactory.getLog(CacheLoaderInvalidationTest.class);
   private CacheSPI<Object, Object> cache1, cache2;
   private Set<CacheSPI> toClean = new HashSet<CacheSPI>();

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache1, cache2);
      for (CacheSPI c : toClean) TestingUtil.killCaches(c);
      toClean.clear();
   }


   public void testOptimisticWithCacheLoader() throws Exception
   {
      List<CacheSPI<Object, Object>> caches = createCachesWithSharedCL(true);
      cache1 = caches.get(0);
      cache2 = caches.get(1);

      Fqn fqn = Fqn.fromString("/a/b");
      TransactionManager mgr = caches.get(0).getTransactionManager();
      assertNull("Should be null", caches.get(0).get(fqn, "key"));
      assertNull("Should be null", caches.get(1).get(fqn, "key"));
      mgr.begin();
      caches.get(0).put(fqn, "key", "value");
      assertEquals("value", caches.get(0).get(fqn, "key"));
      assertNull("Should be null", caches.get(1).get(fqn, "key"));
      mgr.commit();
      assertEquals("value", caches.get(1).get(fqn, "key"));
      assertEquals("value", caches.get(0).get(fqn, "key"));

      mgr.begin();
      caches.get(0).put(fqn, "key2", "value2");
      assertEquals("value2", caches.get(0).get(fqn, "key2"));
      assertNull("Should be null", caches.get(1).get(fqn, "key2"));
      mgr.rollback();
      assertEquals("value", caches.get(1).get(fqn, "key"));
      assertEquals("value", caches.get(0).get(fqn, "key"));
      assertNull("Should be null", caches.get(0).get(fqn, "key2"));
      assertNull("Should be null", caches.get(1).get(fqn, "key2"));
   }


   protected CacheSPI<Object, Object> createUnstartedCache(boolean optimistic) throws Exception
   {
      Configuration c = new Configuration();
      //c.setClusterName("MyCluster");
      c.setStateRetrievalTimeout(3000);
      c.setCacheMode(Configuration.CacheMode.INVALIDATION_SYNC);
      if (optimistic) c.setNodeLockingScheme("OPTIMISTIC");
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");

      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      toClean.add(cache);
      return cache;
   }

   protected CacheSPI<Object, Object> createCache(boolean optimistic) throws Exception
   {
      CacheSPI<Object, Object> cache = createUnstartedCache(optimistic);
      cache.start();
      toClean.add(cache);
      return cache;
   }

   protected List<CacheSPI<Object, Object>> createCachesWithSharedCL(boolean optimistic) throws Exception
   {
      List<CacheSPI<Object, Object>> caches = new ArrayList<CacheSPI<Object, Object>>();
      caches.add(createUnstartedCache(optimistic));
      caches.add(createUnstartedCache(optimistic));

      caches.get(0).getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(getClass()));
      caches.get(1).getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(getClass()));

      caches.get(0).start();
      caches.get(1).start();
      toClean.addAll(caches);
      return caches;
   }

   protected void doRegionBasedTest(boolean optimistic) throws Exception
   {
      List<CacheSPI<Object, Object>> caches = new ArrayList<CacheSPI<Object, Object>>();
      caches.add(createUnstartedCache(false));
      caches.add(createUnstartedCache(false));
      cache1 = caches.get(0);
      cache2 = caches.get(1);

      caches.get(0).getConfiguration().setUseRegionBasedMarshalling(true);
      caches.get(1).getConfiguration().setUseRegionBasedMarshalling(true);

      if (optimistic)
      {
         caches.get(0).getConfiguration().setNodeLockingScheme("OPTIMISTIC");
         caches.get(1).getConfiguration().setNodeLockingScheme("OPTIMISTIC");
      }

      caches.get(0).start();
      caches.get(1).start();

      TestingUtil.blockUntilViewsReceived(caches.toArray(new CacheSPI[0]), 5000);

      Fqn fqn = Fqn.fromString("/a/b");

      assertNull("Should be null", caches.get(0).getNode(fqn));
      assertNull("Should be null", caches.get(1).getNode(fqn));

      caches.get(0).put(fqn, "key", "value");
      assertEquals("expecting value", "value", caches.get(0).get(fqn, "key"));
      Node n = caches.get(1).getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n, "Should have been invalidated");

      // now put in caches.get(1), should fire an eviction
      caches.get(1).put(fqn, "key", "value2");
      assertEquals("expecting value2", "value2", caches.get(1).get(fqn, "key"));
      n = caches.get(0).getNode(fqn);
      CacheLoaderInvalidationTest.assertHasBeenInvalidated(n, "Should have been invalidated");
   }



   static CacheLoaderConfig getCacheLoaderConfig(Class requestor) throws Exception
   {
      return UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "",
            "org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader", "bin=" + requestor , false, false, false, false, false);
   }

   static void assertHasBeenInvalidated(Node n, String message)
   {
      // depending on how n was retrieved!
      if (n == null)
      {
         assert true : message;
      }
      else
      {
         assert !n.isValid() : message;
      }
   }

   static void checkRemoteNodeIsRemoved(Node<Object, Object> remoteNode)
   {
      assertHasBeenInvalidated(remoteNode, "Should have been removed");
      // Recursively check any children
      if (remoteNode != null)
      {
         for (Node<Object, Object> child : remoteNode.getChildren())
         {
            checkRemoteNodeIsRemoved(child);
         }
      }
   }

}
