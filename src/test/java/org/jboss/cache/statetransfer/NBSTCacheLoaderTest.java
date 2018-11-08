package org.jboss.cache.statetransfer;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoaderConfig;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.IOException;

@Test(groups = "functional", testName = "statetransfer.NBSTCacheLoaderTest", enabled = true)
public class NBSTCacheLoaderTest extends NonBlockingStateTransferTest
{
   int id;
   ThreadLocal<Boolean> sharedCacheLoader = new ThreadLocal<Boolean>()
   {
      protected Boolean initialValue()
      {
         return false;
      }
   };

   @AfterMethod
   public void resetThreadLocal()
   {
      sharedCacheLoader.set(false);
   }


   @Override
   protected CacheSPI<Object, Object> createCache(String name, boolean start) throws IOException
   {
      CacheSPI<Object, Object> c = super.createCache(name, false);

      CacheLoaderConfig clmc = new CacheLoaderConfig();
      DummySharedInMemoryCacheLoaderConfig clc = new DummySharedInMemoryCacheLoaderConfig("store number " + id++);
      clc.setFetchPersistentState(true);
      clc.setProperties("debug=true");
      clmc.setShared(sharedCacheLoader.get());
      clmc.addIndividualCacheLoaderConfig(clc);
      c.getConfiguration().setCacheLoaderConfig(clmc);

      if (start) c.start();
      return c;
   }

   @Override
   protected void writeInitialData(final CacheSPI<Object, Object> cache)
   {
      super.writeInitialData(cache);
      cache.evict(A_B);
      cache.evict(A_C);
      cache.evict(A_D);
   }

   protected void verifyInitialDataOnLoader(CacheSPI<Object, Object> c) throws Exception
   {
      CacheLoader l = TestingUtil.getCacheLoader(c);
      assertEquals("Incorrect name for /a/b on loader", JOE, l.get(A_B).get("name"));
      assertEquals("Incorrect age for /a/b on loader", TWENTY, l.get(A_B).get("age"));
      assertEquals("Incorrect name for /a/c on loader", BOB, l.get(A_C).get("name"));
      assertEquals("Incorrect age for /a/c on loader", FORTY, l.get(A_C).get("age"));
   }

   protected void verifyNoData(CacheSPI<Object, Object> c)
   {
      assert c.getRoot().getChildrenNames().isEmpty(): "Cache should be empty!";
   }

   protected void verifyNoDataOnLoader(CacheSPI<Object, Object> c) throws Exception
   {
      CacheLoader l = TestingUtil.getCacheLoader(c);
      assertNull("Node /a/b should not exist on loader", l.get(A_B));
      assertNull("Node /a/c should not exist on loader", l.get(A_C));
      assertNull("Node /a/d should not exist on loader", l.get(A_D));
   }


   public void testSharedLoader() throws Exception
   {
      sharedCacheLoader.set(true);
      CacheSPI<Object, Object> c1=null, c2= null;
      try
      {
         c1 = createCache("testSharedLoader", true);
         writeInitialData(c1);

         // starting the second cache would initialize an in-memory state transfer but not a persistent one since the loader is shared
         c2 = createCache("testSharedLoader", true);

         TestingUtil.blockUntilViewsReceived(60000, c1, c2);

         verifyInitialDataOnLoader(c1);
         verifyInitialData(c1);

         verifyNoDataOnLoader(c2);
         verifyNoData(c2);
      }
      finally
      {
         TestingUtil.killCaches(c1, c2);
      }
   }
}
