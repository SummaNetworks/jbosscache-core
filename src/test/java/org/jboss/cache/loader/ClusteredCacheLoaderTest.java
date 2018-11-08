/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.*;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.TimeoutException;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;

/**
 * Tests ClusteredCacheLoader
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, sequential = true, testName = "loader.ClusteredCacheLoaderTest")
public class ClusteredCacheLoaderTest extends AbstractMultipleCachesTest
{
   private static Log log = LogFactory.getLog(ClusteredCacheLoaderTest.class);
   private CacheSPI<Object, Object> cache1, cache2;
   private CacheLoader loader1, loader2;
   private Fqn fqn = Fqn.fromString("/a");
   private String key = "key";

   protected boolean useRegionBasedMarshalling = false;


   protected void createCaches() throws Throwable
   {
      Configuration c1 = new Configuration();
      Configuration c2 = new Configuration();
      c1.setStateRetrievalTimeout(2000);
      c2.setStateRetrievalTimeout(2000);
      c1.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c2.setCacheMode(Configuration.CacheMode.REPL_SYNC);

      c1.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.ClusteredCacheLoader",
            "timeout=5000", false, false, false, false, false));
      c2.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.ClusteredCacheLoader",
            "timeout=5000", false, false, false, false, false));

      c1.setUseRegionBasedMarshalling(useRegionBasedMarshalling);
      c2.setUseRegionBasedMarshalling(useRegionBasedMarshalling);
      
      
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c1, false, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c2, false, getClass());
      cache1.getConfiguration().setSerializationExecutorPoolSize(0);
      cache2.getConfiguration().setSerializationExecutorPoolSize(0);


      if (useRegionBasedMarshalling)
      {
         cache1.getRegionManager().getRegion(fqn, Region.Type.MARSHALLING, true).registerContextClassLoader(this.getClass().getClassLoader());
         cache2.getRegionManager().getRegion(fqn, Region.Type.MARSHALLING, true).registerContextClassLoader(this.getClass().getClassLoader());
      }

      cache1.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      cache2.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);

      cache1.start();
      cache2.start();

      loader1 = cache1.getCacheLoaderManager().getCacheLoader();
      loader2 = cache2.getCacheLoaderManager().getCacheLoader();
      registerCaches(cache1, cache2);
   }

   public void testGetKeyValue() throws Exception
   {
      cache1.put(fqn, key, "value");

      log.info("Finished put");
      // test that this has propagated.
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

      cache1.evict(fqn);

      // now cache 1 should not have this but cache 2 should.
      // loader1 looks at cache2 while loader2 looks at cache1
      assertEquals("value", loader1.get(fqn).get(key));
      assertNull("Expecting null", loader2.get(fqn));
      //        // calling a get on cache1 should cause the loader to retrieve the node from cache2
      assertEquals("value", cache1.get(fqn, key));
      //        // and now loader2 should see it
      //        assertEquals("value", loader2.get(fqn).get(key));
   }

   public void testGet() throws Exception
   {
      cache1.put(fqn, key, "value");

      // test that this has propagated.
      Map map = loader1.get(fqn);
      assertTrue("Should contain key", map.containsKey(key));
      assertEquals("value", map.get(key));
      assertEquals(1, map.size());

      map = loader2.get(fqn);
      assertTrue("Should contain key", map.containsKey(key));
      assertEquals("value", map.get(key));
      assertEquals(1, map.size());

      cache1.evict(fqn);

      // now cache 1 should not have this but cache 2 should.
      // loader1 looks at cache2 while loader2 looks at cache1
      map = loader1.get(fqn);
      assertTrue(map.containsKey(key));
      assertEquals("value", map.get(key));
      assertEquals(1, map.size());

      assertNull("Expecting null", loader2.get(fqn));
      map = loader2.get(fqn);
      assertNull("Should be null", map);

      // calling a get on cache1 should cause the loader to retrieve the node from cache2
      assertEquals("value", cache1.get(fqn, key));
      // and now loader2 should see it
      map = loader2.get(fqn);
      assertTrue(map.containsKey(key));
      assertEquals("value", map.get(key));
      assertEquals(1, map.size());
   }

   public void testGetChildrenNames() throws Exception
   {
      cache1.put(fqn, key, "value");
      Fqn child1 = Fqn.fromRelativeElements(fqn, "child1");
      Fqn child2 = Fqn.fromRelativeElements(fqn, "child2");
      Fqn child3 = Fqn.fromRelativeElements(fqn, "child3");
      cache1.put(child1, key, "value");
      cache1.put(child2, key, "value");
      cache1.put(child3, key, "value");

      // test that this has propagated.
      Set childNames = loader1.getChildrenNames(fqn);
      assertEquals(3, childNames.size());
      childNames = loader2.getChildrenNames(fqn);
      assertEquals(3, childNames.size());

      cache1.evict(child1);
      cache1.evict(child2);
      cache1.evict(child3);
      cache1.evict(fqn);

      // now cache 1 should not have this but cache 2 should.
      // loader1 looks at cache2 while loader2 looks at cache1
      assert cache1.peek(fqn, false) == null;
      childNames = loader1.getChildrenNames(fqn);
      assert cache1.peek(fqn, false) == null;
      assertEquals(3, childNames.size());

//      cache1.evict(child1);
//      cache1.evict(child2);
//      cache1.evict(child3);
      cache1.evict(fqn, true);
//      cache2.evict(child1);
//      cache2.evict(child2);
//      cache2.evict(child3);
//      cache2.evict(fqn, true);
      childNames = loader2.getChildrenNames(fqn);
      assertNull("should be null", childNames);
      // calling a get on cache1 should cause the loader to retrieve the node from cache2
      cache1.evict(fqn, true);
      assertEquals("value", cache1.get(fqn, key));
      // load up children
      assertEquals("value", cache1.get(child1, key));
      assertEquals("value", cache1.get(child2, key));
      assertEquals("value", cache1.get(child3, key));
      // and now loader2 should see it
      childNames = loader2.getChildrenNames(fqn);
      assertEquals(3, childNames.size());
   }

   public void testExists() throws Exception
   {
      cache1.put(fqn, key, "value");

      // test that this has propagated.
      assertTrue("should exist", loader1.exists(fqn));
      assertTrue("should exist", loader2.exists(fqn));

      cache1.evict(fqn);

      // now cache 1 should not have this but cache 2 should.
      // loader1 looks at cache2 while loader2 looks at cache1
      assertTrue("should exist", loader1.exists(fqn));
      assertTrue("should not exist", !loader2.exists(fqn));
      // calling a get on cache1 should cause the loader to retrieve the node from cache2
      assertEquals("value", cache1.get(fqn, key));
      // and now loader2 should see it
      assertTrue("should exist", loader2.exists(fqn));
   }

   //todo - mmarkus - extract these as testng.xml configs withe different values for different environements  (for this test e.g. number of loops)
   public void testCacheLoaderThreadSafety() throws Throwable
   {
      threadSafetyTest(true);
   }

   public void testCacheLoaderThreadSafetyMultipleFqns() throws Exception
   {
      threadSafetyTest(false);
   }

   protected void threadSafetyTest(final boolean singleFqn) throws Exception
   {
      final CountDownLatch latch = new CountDownLatch(1);
      final Fqn fqn = Fqn.fromString("/a/b/c");
      final List<Fqn> fqns = new ArrayList<Fqn>(30);
      final Random r = new Random();
      if (!singleFqn)
      {
         for (int i = 0; i < 30; i++)
         {
            Fqn f = Fqn.fromString("/a/b/c/" + i);
            fqns.add(f);
            cache2.put(f, "k", "v");
            cache1.evict(f);
         }
      }
      else
      {
         cache2.put(fqn, "k", "v");
         cache1.evict(fqn);
      }
      final int loops = 25; // was 300
      final Set<Exception> exceptions = new CopyOnWriteArraySet<Exception>();

      Thread evictor = new Thread("Evictor")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  Fqn f = singleFqn ? fqn : fqns.get(r.nextInt(fqns.size()));
                  cache1.evict(f);
                  TestingUtil.sleepRandom(50);
               }
            }
            catch (TimeoutException te)
            {
               // doesn't matter if we hit these on occasion
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };

      evictor.start();

      Thread writer = new Thread("Writer")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  Fqn f = singleFqn ? fqn : fqns.get(r.nextInt(fqns.size()));
                  cache2.put(f, "k", "v");
                  TestingUtil.sleepRandom(50);
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };

      writer.start();


      Thread reader1 = new Thread("Reader-1")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  loader1.get(singleFqn ? fqn : fqns.get(r.nextInt(fqns.size())));
                  TestingUtil.sleepRandom(50);
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };
      reader1.start();

      Thread reader2 = new Thread("Reader-2")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  loader1.getChildrenNames(singleFqn ? fqn.getParent() : fqns.get(r.nextInt(fqns.size())).getParent());
                  TestingUtil.sleepRandom(50);
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };
      reader2.start();

      Thread reader3 = new Thread("Reader-3")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  loader1.getChildrenNames(singleFqn ? fqn : fqns.get(r.nextInt(fqns.size())));
                  TestingUtil.sleepRandom(50);
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };
      reader3.start();

      latch.countDown();
      reader1.join();
      reader2.join();
      reader3.join();
      evictor.join();
      writer.join();

      for (Exception e : exceptions) throw e;
   }

}
