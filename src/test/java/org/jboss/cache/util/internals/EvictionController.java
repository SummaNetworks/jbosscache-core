package org.jboss.cache.util.internals;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionRegistry;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.EvictionTimerTask;
import org.jboss.cache.eviction.EvictionTimerTask.Task;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.util.TestingUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * when used on a cache will disable defaul eviction behavior and it will supply means of kicking off evction
 * programmatically. It is intended for replcaing Thread.sleep(xyz) - like statements in which the executing tests wait
 * untill eviction finishes.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class EvictionController
{
   CacheSPI cache;
   RegionManager regionManager;
   EvictionTimerTask timerTask;
   long originalWakeupInterval;
   RegionRegistry rr;

   public EvictionController(Cache cache)
   {
      this.cache = (CacheSPI) cache;
      regionManager = this.cache.getRegionManager();
      if (regionManager == null)
      {
         throw new IllegalStateException("Null region manager; is the cache started?");
      }
      timerTask = (EvictionTimerTask) TestingUtil.extractField(regionManager, "evictionTimerTask");
      if (timerTask == null)
      {
         throw new IllegalStateException("No timer task!!!");
      }
      rr = this.cache.getComponentRegistry().getComponent(RegionRegistry.class);
      stopEvictionThread();
      originalWakeupInterval = cache.getConfiguration().getEvictionConfig().getWakeupInterval();
   }

   public void startEviction()
   {
      startEviction(false);
   }

   /**
    * Kick starts the eviction process
    *
    * @param restartEvictionTimerTask if true, restarts the eviction timer scheduled executor after manually kicking off an eviction.
    */
   public void startEviction(boolean restartEvictionTimerTask)
   {
      try
      {
         Method method = EvictionTimerTask.class.getDeclaredMethod("processRegions", new Class[]{});
         method.setAccessible(true);
         method.invoke(timerTask);
      }
      catch (Exception e)
      {
         e.printStackTrace();
         throw new IllegalStateException(e);
      }

      if (restartEvictionTimerTask)
      {
         timerTask.init(originalWakeupInterval, null, rr);
      }
   }

   /**
    * Evicts the given region but only after ensuring that region's TTL passed.
    */
   public void evictRegionWithTimeToLive(String region) throws Exception
   {
      EvictionConfig evConfig = cache.getConfiguration().getEvictionConfig();
      EvictionRegionConfig erConfig = evConfig.getEvictionRegionConfig(region);
      if (erConfig == null)
      {
         throw new IllegalStateException("No such region!");
      }
      long ttl = 0;
      if (erConfig.getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig)
      {
         LRUAlgorithmConfig configuration = (LRUAlgorithmConfig) erConfig.getEvictionAlgorithmConfig();
         ttl = configuration.getTimeToLive();
      }
      else
      {
         throw new IllegalArgumentException("Only LRU being handled for now; please add other implementations here");
      }
      TestingUtil.sleepThread(ttl + 500);
      evictRegion(region);
   }

   /**
    * Only evicts the given region.
    */
   public void evictRegion(String regionStr) throws Exception
   {
      for (Region region : rr.values())
      {
         if (region.getEvictionRegionConfig() != null && region.getFqn().equals(Fqn.fromString(regionStr)))
         {
            Method method = EvictionTimerTask.class.getDeclaredMethod("handleRegion", Region.class);
            method.setAccessible(true);
            method.invoke(timerTask, region);
         }
      }
   }

   public Signaller getEvictionThreadSignaller()
   {
      final Signaller s = new Signaller();
      Task signallingTask = timerTask.new Task()
      {
         public void run()
         {
            s.getToken();
            try
            {
               super.run();
            }
            finally
            {
               s.releaseToken();
            }
         }
      };

      try
      {
         Class ettClass = EvictionTimerTask.class;
         Field f = ettClass.getDeclaredField("task");
         f.setAccessible(true);
         f.set(timerTask, signallingTask);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
      timerTask.init(originalWakeupInterval, null, rr);
      return s;
   }

   public void stopEvictionThread()
   {
      timerTask.stop();
   }

   public static class Signaller
   {
      Semaphore s = new Semaphore(1);

      public boolean waitForEvictionThreadCompletion(long time, TimeUnit unit) throws InterruptedException
      {
         try
         {
            return s.tryAcquire(time, unit);
         }
         finally
         {
            s.release();
         }
      }

      void getToken()
      {
         try
         {
            s.acquire();
         }
         catch (InterruptedException e)
         {
            Thread.currentThread().interrupt();
         }
      }

      void releaseToken()
      {
         s.release();
      }
   }
}
