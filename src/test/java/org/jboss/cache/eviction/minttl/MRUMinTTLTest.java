package org.jboss.cache.eviction.minttl;

import org.jboss.cache.CacheStatus;
import org.jboss.cache.Fqn;
import org.jboss.cache.eviction.EvictionAlgorithmConfigBase;
import org.jboss.cache.eviction.MRUAlgorithmConfig;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional"}, testName = "eviction.minttl.MRUMinTTLTest")
public class MRUMinTTLTest extends MinTTLTestBase
{
   private Fqn fqn2 = Fqn.fromRelativeElements(region, "b");

   @Override
   protected EvictionAlgorithmConfigBase getEvictionAlgorithmConfig()
   {
      MRUAlgorithmConfig cfg = new MRUAlgorithmConfig();
      cfg.setMaxNodes(1);
      startBusyThread();
      return cfg;
   }

   private void startBusyThread()
   {
      // start a thread to constantly put another node in the cache to make sure the maxNodes is exceeded.
      // this should only happen AFTER the main node is entered to guarantee FIFO.

      Thread busyThread = new Thread()
      {
         public void run()
         {
            while (true)
            {
               if (cache != null)
               {
                  if (cache.getCacheStatus() == CacheStatus.STARTED)
                  {
                     if (cache.getRoot().hasChild(fqn))
                     {
                        cache.put(fqn2, "k", "v");
                        break;
                     }
                  }
               }
               TestingUtil.sleepRandom(50);
            }
         }
      };

      busyThread.setDaemon(true);
      busyThread.start();
   }
}
