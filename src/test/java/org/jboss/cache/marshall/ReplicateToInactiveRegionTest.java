package org.jboss.cache.marshall;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;

@Test(groups = {"functional", "jgroups"}, sequential = true, testName = "marshall.ReplicateToInactiveRegionTest")
public class ReplicateToInactiveRegionTest
{
   List<CacheSPI<Object, Object>> caches;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      caches = new ArrayList<CacheSPI<Object, Object>>(2);
      caches.add(createCache());
      caches.add(createCache());
      TestingUtil.blockUntilViewsReceived(caches.toArray(new CacheSPI[]{}), 10000);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(caches.get(0), caches.get(1));
      caches = null;
   }

   private CacheSPI<Object, Object> createCache()
   {
      Configuration c = new Configuration();
      c.setCacheMode("REPL_SYNC");
      c.setUseRegionBasedMarshalling(true);
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      cache.start();
      return cache;
   }

   public void testTransferToInactiveRegion()
   {
      Fqn f = Fqn.fromString("/a/b");

      caches.get(0).put(f, "k", "v");

      assertEquals("v", caches.get(0).get(f, "k"));
      assertEquals("v", caches.get(1).get(f, "k"));

      // create the region on cache 0, make sure it is marked as a MARSHALLING region by attaching a class loader to it.
      Region region0 = caches.get(0).getRegionManager().getRegion(f, true);
      region0.registerContextClassLoader(this.getClass().getClassLoader()); // just to make sure this is recognised as a marshalling region.
      assertTrue("Should be active by default", region0.isActive());
      // make sure this newly created region is "recognised" as a marshalling region.
      assertTrue(caches.get(0).getRegionManager().getAllRegions(Region.Type.MARSHALLING).contains(region0));

      // now create a region on cache 1, as above.
      Region region1 = caches.get(1).getRegionManager().getRegion(f, true);
      region1.registerContextClassLoader(this.getClass().getClassLoader()); // just to make sure this is recognised as a marshalling region.
      assertTrue("Should be active by default", region1.isActive());
      // make sure this newly created region is "recognised" as a marshalling region.
      assertTrue(caches.get(1).getRegionManager().getAllRegions(Region.Type.MARSHALLING).contains(region1));

      // now deactivate the region on cache 1.
      region1.deactivate();
      assertFalse("Should be have deactivated", region1.isActive());

      caches.get(0).put(f, "k", "v2");
      assertEquals("v2", caches.get(0).get(f, "k"));
      assertNull(caches.get(1).get(f, "k"));

   }
}

