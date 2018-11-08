package org.jboss.cache.eviction;

import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.RegionImpl;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.RegionRegistry;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;
import org.testng.annotations.AfterMethod;

/**
 * @author Ben Wang, Feb 11, 2004
 * @author Daniel Huang (dhuang@jboss.org)
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.RegionTest")
public class RegionTest
{
   RegionManager regionManager;
   EvictionAlgorithm algorithm;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      algorithm = new LRUAlgorithm();
      regionManager = new RegionManagerImpl();
      ((RegionManagerImpl) regionManager).injectDependencies(null, null, null, null, null, new RegionRegistry());
      Region r = regionManager.getRegion("/a/b", true);//.setEvictionPolicy(new DummyEvictionConfiguration());
      r.setEvictionRegionConfig(new EvictionRegionConfig(r.getFqn(), new LRUAlgorithmConfig()));
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      algorithm = null;
      regionManager = null;
   }
   
   public void testAddedQueue() throws InterruptedException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");

      Region region = regionManager.getRegion("/a/b", true);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.ADD_NODE_EVENT);

      assertEquals("queue size ", 3, getQueueSize((RegionImpl) region));
      EvictionEvent node = takeLastEvent((RegionImpl) region);
      Fqn fqn = node.getFqn();
      assertEquals("DataNode retrieved should be FILO ", fqn, fqn1);
      assertEquals("AddedNode queue size ", 2, getQueueSize((RegionImpl) region));
      fqn = takeLastEvent((RegionImpl) region).getFqn();
      fqn = takeLastEvent((RegionImpl) region).getFqn();
      node = takeLastEvent((RegionImpl) region);
      assertNull("DataNode should be null", node);
   }

   public void testEventQueue() throws InterruptedException
   {
      Fqn fqn1 = Fqn.fromString("/a/b/c");
      Fqn fqn2 = Fqn.fromString("/a/b/d");
      Fqn fqn3 = Fqn.fromString("/a/b/e");

      Region region = regionManager.getRegion("/a/b", true);
      region.registerEvictionEvent(fqn1, EvictionEvent.Type.REMOVE_NODE_EVENT);
      region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      region.registerEvictionEvent(fqn3, EvictionEvent.Type.VISIT_NODE_EVENT);

      assertEquals("RemovedNode queue size ", 3, getQueueSize((RegionImpl) region));
      EvictionEvent.Type event = takeLastEvent((RegionImpl) region).getEventType();
      assertEquals("DataNode retrieved should be: ", EvictionEvent.Type.REMOVE_NODE_EVENT, event);
      takeLastEvent((RegionImpl) region);
      takeLastEvent((RegionImpl) region);
      EvictionEvent node = takeLastEvent((RegionImpl) region);
      assertNull("DataNode should be null", node);
   }

   public void testMassivePutOnQueue()
   {
      Fqn fqn2 = Fqn.fromString("/a/b/d");

      Region region = regionManager.getRegion("/a/b", true);
      // This should succeed, alhtough it will produce warning over the threshold.
      for (int i = 0; i < EvictionConfig.EVENT_QUEUE_SIZE_DEFAULT - 1; i++)
      {
         region.registerEvictionEvent(fqn2, EvictionEvent.Type.ADD_NODE_EVENT);
      }

   }

   EvictionEvent takeLastEvent(RegionImpl r) throws InterruptedException
   {
      return r.getEvictionEventQueue().poll(0, TimeUnit.MILLISECONDS);
   }

   int getQueueSize(RegionImpl r)
   {
      return r.getEvictionEventQueue().size();
   }

}
