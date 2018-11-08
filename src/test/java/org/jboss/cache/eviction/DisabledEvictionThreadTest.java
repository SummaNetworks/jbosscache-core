package org.jboss.cache.eviction;

import org.jboss.cache.Cache;
import org.jboss.cache.RegionManager;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "eviction.DisabledEvictionThreadTest")
public class DisabledEvictionThreadTest
{
   public void testDisabledEvictionTimer()
   {
      Cache<String, String> c = null;
      try
      {
         Configuration cfg = UnitTestConfigurationFactory.createConfiguration(CacheMode.LOCAL, true);
         cfg.getEvictionConfig().setWakeupInterval(0);
         c = new UnitTestCacheFactory<String, String>().createCache(cfg, getClass());
         ComponentRegistry cr = TestingUtil.extractComponentRegistry(c);
         RegionManager rm = cr.getComponent(RegionManager.class);
         EvictionTimerTask ett = rm.getEvictionTimerTask();
         assert ett.scheduledExecutor == null;
      }
      finally
      {
         TestingUtil.killCaches(c);
      }
   }

   public void testControl()
   {
      Cache<String, String> c = null;
      try
      {
         Configuration cfg = UnitTestConfigurationFactory.createConfiguration(CacheMode.LOCAL, true);
         cfg.getEvictionConfig().setWakeupInterval(10);
         c = new UnitTestCacheFactory<String, String>().createCache(cfg, getClass());
         ComponentRegistry cr = TestingUtil.extractComponentRegistry(c);
         RegionManager rm = cr.getComponent(RegionManager.class);
         EvictionTimerTask ett = rm.getEvictionTimerTask();
         assert ett.scheduledExecutor != null;
      }
      finally
      {
         TestingUtil.killCaches(c);
      }
   }
}
