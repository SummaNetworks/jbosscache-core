package org.jboss.cache.eviction;

import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.RegionRegistry;
import org.jboss.cache.config.EvictionRegionConfig;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Region Manager unit tests.
 *
 * @author Ben Wang, Feb 11, 2004
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = "functional", testName = "eviction.RegionManagerTest")
public class RegionManagerTest
{
   private final Fqn DEFAULT_REGION = Fqn.ROOT;
   Fqn A_B_C = Fqn.fromString("/a/b/c");
   Fqn A_B = Fqn.fromString("/a/b");
   Fqn A_BC = Fqn.fromString("/a/bc");
   Fqn AOP = Fqn.fromString("/aop");

   EvictionRegionConfig config = new EvictionRegionConfig(null, new NullEvictionAlgorithmConfig());

   public void testCreateRegion()
   {
      RegionManager regionManager = new RegionManagerImpl();
      ((RegionManagerImpl) regionManager).injectDependencies(null, null, null, null, null, new RegionRegistry());
      regionManager.setUsingEvictions(true);
      regionManager.getRegion(DEFAULT_REGION, true).setEvictionRegionConfig(config);
      regionManager.getRegion(A_B_C, true).setEvictionRegionConfig(config);
      regionManager.getRegion(A_B, true).setEvictionRegionConfig(config);
      regionManager.getRegion(AOP, true).setEvictionRegionConfig(config);

      List<Region> regions = regionManager.getAllRegions(Region.Type.ANY);
      assertEquals("Region size ", 4, regions.size());
   }

   public void testCreateRegion2()
   {

      RegionManager regionManager = new RegionManagerImpl();
      ((RegionManagerImpl) regionManager).injectDependencies(null, null, null, null, null, new RegionRegistry());
      regionManager.setUsingEvictions(true);
      regionManager.getRegion(A_B_C, true).setEvictionRegionConfig(config);
      regionManager.getRegion(A_B, true).setEvictionRegionConfig(config);
      regionManager.getRegion(DEFAULT_REGION, true).setEvictionRegionConfig(config);

      List<Region> regions = regionManager.getAllRegions(Region.Type.ANY);
      assertEquals("Region size ", 3, regions.size());
      assertEquals("Region 0", DEFAULT_REGION, regions.get(0).getFqn());
      assertEquals("Region 1 ", A_B, regions.get(1).getFqn());
      assertEquals("Region 2 ", A_B_C, regions.get(2).getFqn());
      Region region = regionManager.getRegion("/a/b/c/d", false);
      assertNotNull("Region ", region);
      assertEquals("Region ", A_B_C, region.getFqn());
      region = regionManager.getRegion(A_B, false);
      assertNotNull("Region ", region);
      assertEquals("Region ", A_B, region.getFqn());
      region = regionManager.getRegion("/a", false);
      // Should be default.
      assertNotNull("Region ", region);
      assertEquals("Region ", DEFAULT_REGION, region.getFqn());
   }

   public void testNoDefaultRegion()
   {
      RegionManager regionManager = new RegionManagerImpl();
      ((RegionManagerImpl) regionManager).injectDependencies(null, null, null, null, null, new RegionRegistry());
      regionManager.setUsingEvictions(true);
      regionManager.getRegion(A_B_C, true).setEvictionRegionConfig(config);
      regionManager.getRegion(A_B, true).setEvictionRegionConfig(config);

      regionManager.getRegion(Fqn.fromString("/a"), Region.Type.EVICTION, false);
   }

   public void testGetRegion()
   {
      RegionManager regionManager = new RegionManagerImpl();
      ((RegionManagerImpl) regionManager).injectDependencies(null, null, null, null, null, new RegionRegistry());
      regionManager.setUsingEvictions(true);
      regionManager.getRegion(DEFAULT_REGION, true).setEvictionRegionConfig(config);
      regionManager.getRegion(A_BC, true).setEvictionRegionConfig(config);
      regionManager.getRegion(A_B, true).setEvictionRegionConfig(config);

      Region region = regionManager.getRegion(A_BC, true);
      assertNotSame("Region ", DEFAULT_REGION, region.getFqn());
   }

   public void testRegionOrdering() throws Exception
   {
      Fqn A_B_C_D_E = Fqn.fromString("/a/b/c/d/e/");
      Fqn A_B_C_D = Fqn.fromString("/a/b/c/d/");

      RegionManager rm = new RegionManagerImpl();
      ((RegionManagerImpl) rm).injectDependencies(null, null, null, null, null, new RegionRegistry());
      rm.setUsingEvictions(true);
      rm.getRegion(DEFAULT_REGION, true).setEvictionRegionConfig(config);
      rm.getRegion(A_B_C_D_E, true).setEvictionRegionConfig(config);
      rm.getRegion(A_B_C_D, true).setEvictionRegionConfig(config);
      rm.getRegion(A_B_C, true).setEvictionRegionConfig(config);

      Region region = rm.getRegion("/a/b/c/d/e/f", false);
      Region region2 = rm.getRegion("/e/f/g", false);

      assertEquals(A_B_C_D_E, region.getFqn());
      assertEquals(DEFAULT_REGION, region2.getFqn());

      List<Region> regions = rm.getAllRegions(Region.Type.ANY);
      for (int i = 0; i < regions.size(); i++)
      {
         switch (i)
         {
            case 0:
               assertEquals(DEFAULT_REGION, regions.get(i).getFqn());
               break;
            case 1:
               assertEquals(A_B_C, regions.get(i).getFqn());
               break;
            case 2:
               assertEquals(A_B_C_D, regions.get(i).getFqn());
               break;
            case 3:
               assertEquals(A_B_C_D_E, regions.get(i).getFqn());
               break;
            default:
               fail("This error condition should never be reached");
               break;
         }
      }
   }
}
