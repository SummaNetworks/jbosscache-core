package org.jboss.cache.loader;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Modification;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.statetransfer.DefaultStateTransferManager;
import org.jboss.util.stream.MarshalledValueInputStream;
import org.jboss.util.stream.MarshalledValueOutputStream;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;

@Test(groups = "functional", testName = "loader.AsyncFileCacheLoaderTest")
public class AsyncFileCacheLoaderTest 
{
   private CacheSPI<Object, Object> cache;

   protected void configureCache() throws Exception
   {
      configureCache("", true);
   }

   protected void configureCache(String props, boolean async) throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory
            .buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.FileCacheLoader", props, async, false, true, false, false));
      cache.create();
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   public void testRestrictionOnAddingToQueue() throws Exception
   {
      configureCache();
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      loader.remove(Fqn.fromString("/blah"));

      loader.put(Fqn.fromString("/blah"), "one", "two");
      loader.put(Fqn.fromString("/blah"), "three", "four");
      loader.put(Fqn.fromString("/blah"), "five", "six");
      loader.put(Fqn.fromString("/blah"), "seven", "eight");

      // stop the cache loader
      loader.stop();
      try
      {
         loader.remove(Fqn.fromString("/blah"));
         assertTrue("Should have restricted this entry from being made", false);
      }
      catch (CacheException e)
      {
         assertTrue(true);
      }

      // clean up
      loader.start();
      loader.remove(Fqn.fromString("/blah"));
   }

   public void testPutImmediate() throws Exception
   {
      configureCache(
            "cache.async.put=false\n" +
                  "cache.async.pollWait=10000\n" +
                  "", true);
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      Fqn fqn = Fqn.fromString("/a/b/c/d");
      HashMap<Object, Object> map = new HashMap<Object, Object>();
      map.put("c", "d");
      // Three kinds of puts!
      Modification mod = new Modification(Modification.ModificationType.PUT_KEY_VALUE, fqn, "e", "f");
      loader.put(fqn, map);
      loader.put(fqn, "a", "b");
      loader.put(Collections.singletonList(mod));
      assertEquals("put right away", 3, loader.get(fqn).size());
      loader.remove(fqn);
   }

   public void testBounded() throws Exception
   {
      configureCache(
            "cache.async.queueSize=1\n" +
                  "cache.async.pollWait=10\n" +
                  "", true);
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      Fqn fqn = Fqn.fromString("/bound");
      loader.remove(fqn);
      // You can't really see it block though :-/
      for (int i = 0; i < 50; i++)
      {
         cache.put(fqn, "key" + i, "value1");
      }
      //this happens async, so expect this will happen at a further point in time
      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() - start < 50000)
      {
         if (loader.get(fqn) != null && loader.get(fqn).size() == 50) break;
         Thread.sleep(100);
      }
      assertEquals(50, loader.get(fqn).size());
      loader.remove(fqn);
   }

   public void testNoReturnOld() throws Exception
   {
      configureCache(
            "cache.async.returnOld=false\n" +
                  "cache.async.pollWait=10\n" +
                  "", true);
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      cache.put(Fqn.ROOT, "key1", "value1");
      Thread.sleep(100);
      assertEquals(null, loader.put(Fqn.ROOT, "key1", "value1"));
      assertEquals(null, loader.remove(Fqn.ROOT, "key1"));
      loader.remove(Fqn.ROOT);
   }

   public void testStoreState() throws Exception
   {
      configureCache("", false);
      Fqn X = Fqn.fromString("/x");
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      loader.remove(X);
      cache.put(X, "key1", "value1");
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      //os.close();
      assertTrue(baos.size() > 0);
      loader.remove(X);

      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      MarshalledValueInputStream is = new MarshalledValueInputStream(bais);
      loader.storeEntireState(is);
      //is.close();
      assertEquals("X found", true, loader.exists(X));
      loader.remove(X);
   }

   public void testMultipleThreads() throws Exception
   {
      configureCache(
            "cache.async.queueSize=1\n" +
                  "cache.async.pollWait=10\n" +
                  "cache.async.threadPoolSize=5", true);
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      Fqn fqn = Fqn.fromString("/bound");
      loader.remove(fqn);
      // You can't really see it block though :-/
      for (int i = 0; i < 50; i++)
      {
         cache.put(fqn, "key" + i, "value1");
      }
      //this happens async, so expect this will happen at a further point in time
      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() - start < 50000)
      {
         if (loader.get(fqn) != null && loader.get(fqn).size() == 50) break;
         Thread.sleep(100);
      }
      assertEquals(50, loader.get(fqn).size());
      loader.remove(fqn);
   }
}
