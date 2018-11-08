package org.jboss.cache.marshall;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.RegionManager;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Test on ERegionManager class, from a marshalling perspective.
 */
@Test(groups = {"functional"}, sequential = true, testName = "marshall.RegionManagerTest")
public class RegionManagerTest
{
   private final Fqn DEFAULT_REGION = Fqn.ROOT;
   private RegionManager r;
   private Configuration c;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      CacheSPI cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(getClass());
      r = cache.getRegionManager();
      c = cache.getConfiguration();
   }

   @AfterMethod(alwaysRun = false)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(r.getCache());
      r = null;
   }

   public void testGetAllMarshallingRegions()
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b");
      Fqn fqn3 = Fqn.fromString("/aop");

      List<Region> expected = new ArrayList<Region>(4);

      Region region = r.getRegion(DEFAULT_REGION, true);
      region.registerContextClassLoader(getClass().getClassLoader());
      assertEquals(DEFAULT_REGION, region.getFqn());
      expected.add(region);

      region = r.getRegion(fqn1, true);
      region.registerContextClassLoader(getClass().getClassLoader());
      assertEquals(fqn1, region.getFqn());
      expected.add(region);

      region = r.getRegion(fqn2, true);
      region.registerContextClassLoader(getClass().getClassLoader());
      assertEquals(fqn2, region.getFqn());
      expected.add(region);

      region = r.getRegion(fqn3, true);
      region.registerContextClassLoader(getClass().getClassLoader());
      assertEquals(fqn3, region.getFqn());
      expected.add(region);

      // should sort these now ...
      Collections.sort(expected);
      Iterator<Region> expectedRegions = expected.iterator();

      for (Region reg : r.getAllRegions(Region.Type.MARSHALLING))
      {
         assertSame("Unexpected region " + reg, expectedRegions.next(), reg);
      }

      assertFalse("Should not be expecting any more regions", expectedRegions.hasNext());
   }

   public void testNoDefaultRegion()
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/");

      r.getRegion(fqn1, true);
      r.getRegion(fqn2, true);

      Region region = null;
      try
      {
         region = r.getRegion("/a", false);
      }
      catch (Exception e)
      {
         fail("If we don't set the default region, it still should be ok!");
      }

      assertNull("Default region is not null!", region);
   }


   public void testGetParentRegion()
   {
      String fqn1 = "/a/b/c";
      String fqn2 = "/a/b";
      String fqn3 = "/a";

      r.getRegion(fqn1, true);
      r.getRegion(fqn3, true);

      Region region = r.getRegion(fqn2, false);
      assertEquals("Should be the same region as in " + fqn3, r.getRegion(fqn3, false), region);
   }

   public void testRemoveRegion()
   {
      String fqn1 = "/a";
      String fqn2 = "/a/b";
      String fqn3 = "/a/b/c";

      Region r1 = r.getRegion(fqn1, true);
      Region r2 = r.getRegion(fqn2, true);
      Region r3 = r.getRegion(fqn3, true);

      assertEquals("Expecting 3 regions", 3, r.getAllRegions(Region.Type.ANY).size());

      // test that removal doesn't affect parent traversal.
      assertEquals(r3, r.getRegion(fqn3, false));

      r.removeRegion(Fqn.fromString(fqn3));

      assertEquals("Expecting 2 regions", 2, r.getAllRegions(Region.Type.ANY).size());

      // test that removal doesn't affect parent traversal.
      assertEquals("Should have retrieved parent region", r2, r.getRegion(fqn3, false));

      r.removeRegion(Fqn.fromString(fqn2));

      assertEquals("Expecting 1 region", 1, r.getAllRegions(Region.Type.ANY).size());

      // test that removal doesn't affect parent traversal.
      assertEquals("Should have retrieved parent region", r1, r.getRegion(fqn3, false));

      r.removeRegion(Fqn.fromString(fqn1));

      assertEquals("Expecting 0 regions", 0, r.getAllRegions(Region.Type.ANY).size());
   }

   public void testGetRegionsMethods()
   {
      String f1 = "/a", f2 = "/b", f3 = "/c", f4 = "/d";

      r.setDefaultInactive(true);

      @SuppressWarnings("unused")
      Region r1 = r.getRegion(f1, true), r2 = r.getRegion(f2, true), r3 = r.getRegion(f3, true), r4 = r.getRegion(f4, true);

      assertEquals("4 regions should exist", 4, r.getAllRegions(Region.Type.ANY).size());

      assertEquals("None of the regions should marshalling or active", 0, r.getAllRegions(Region.Type.MARSHALLING).size());

      r3.registerContextClassLoader(getClass().getClassLoader());
      r3.activate();

      assertEquals("r3 should be marshalling and active", 1, r.getAllRegions(Region.Type.MARSHALLING).size());
      assertSame("r3 should be marshalling and active", r3, r.getAllRegions(Region.Type.MARSHALLING).get(0));

      r4.activate();// but don't se a class loader

      assertEquals("r3 should be marshalling and active", 1, r.getAllRegions(Region.Type.MARSHALLING).size());
      assertSame("r3 should be marshalling and active", r3, r.getAllRegions(Region.Type.MARSHALLING).get(0));

      r2.registerContextClassLoader(getClass().getClassLoader());// but don't activate

      assertEquals("r3 should be marshalling and active", 1, r.getAllRegions(Region.Type.MARSHALLING).size());
      assertSame("r3 should be marshalling and active", r3, r.getAllRegions(Region.Type.MARSHALLING).get(0));

      r2.activate();

      assertEquals("r2 + r3 should be marshalling and active", 2, r.getAllRegions(Region.Type.MARSHALLING).size());
      assertSame("r2 should be marshalling and active", r2, r.getAllRegions(Region.Type.MARSHALLING).get(0));
      assertSame("r3 should be marshalling and active", r3, r.getAllRegions(Region.Type.MARSHALLING).get(1));

      r4.registerContextClassLoader(getClass().getClassLoader());

      assertEquals("r2 + r3 + r4 should be marshalling and active", 3, r.getAllRegions(Region.Type.MARSHALLING).size());
      assertSame("r2 should be marshalling and active", r2, r.getAllRegions(Region.Type.MARSHALLING).get(0));
      assertSame("r3 should be marshalling and active", r3, r.getAllRegions(Region.Type.MARSHALLING).get(1));
      assertSame("r4 should be marshalling and active", r4, r.getAllRegions(Region.Type.MARSHALLING).get(2));
   }
}
