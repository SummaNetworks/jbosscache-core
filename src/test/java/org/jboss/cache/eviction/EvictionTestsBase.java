package org.jboss.cache.eviction;

import org.easymock.EasyMock;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.RegionRegistry;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionAlgorithmConfig;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionWatcher;

import java.util.concurrent.TimeUnit;

public abstract class EvictionTestsBase
{
   public EvictionAlgorithm createAndAssignToRegion(String fqnString, RegionManager regionManager, EvictionAlgorithmConfig config)
   {
      Fqn fqn = Fqn.fromString(fqnString);
      Configuration c = new Configuration();
      EvictionConfig evictionConfig = new EvictionConfig();
      evictionConfig.setWakeupInterval(-1);
      c.setEvictionConfig(evictionConfig);
      EvictionRegionConfig erc = new EvictionRegionConfig(fqn, config);
      c.getEvictionConfig().addEvictionRegionConfig(erc);
      CacheSPI mockCache = EasyMock.createNiceMock(CacheSPI.class);
      EasyMock.replay(mockCache);
      ((RegionManagerImpl) regionManager).injectDependencies(mockCache, c, null, null, null, new RegionRegistry());
      Region r = regionManager.getRegion(fqn, Region.Type.EVICTION, true);
      r.setEvictionRegionConfig(erc);

      ((RegionManagerImpl) regionManager).start();
      return (EvictionAlgorithm) TestingUtil.extractField(r, "evictionAlgorithm");
   }

   /**
    * Blocks until an eviction event is seen on the given cache for the given array of Fqns.  Returns true if the eviction event
    * is received, false if it times out.
    *
    * @param cache       cache to monitor
    * @param timeToWait  timeout
    * @param unit        timeout unit
    * @param fqnsToEvict fqns to watch for
    * @return true if evicted, false otherwise
    */
   public boolean waitForEviction(Cache cache, long timeToWait, TimeUnit unit, Fqn... fqnsToEvict) throws InterruptedException
   {
      return new EvictionWatcher(cache, fqnsToEvict).waitForEviction(timeToWait, unit);
   }
}