package org.jboss.cache.eviction.minttl;

import org.jboss.cache.Fqn;
import org.jboss.cache.eviction.EvictionAlgorithmConfigBase;
import org.jboss.cache.eviction.FIFOAlgorithmConfig;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional"}, testName = "eviction.minttl.FIFOMinTTLTest")
public class FIFOMinTTLTest extends MinTTLTestBase
{
   private Fqn fqn2 = Fqn.fromRelativeElements(region, "b");
   private Thread busyThread;
   private volatile boolean busyThreadRunning = true;

   @Override
   protected EvictionAlgorithmConfigBase getEvictionAlgorithmConfig()
   {
      startBusyThread();
      FIFOAlgorithmConfig cfg = new FIFOAlgorithmConfig();
      cfg.setMaxNodes(1);
      return cfg;
   }

   @AfterMethod
   public void stopBusyThread()
   {
      busyThreadRunning = false;
      try
      {
         busyThread.interrupt();
         busyThread.join();
      }
      catch (InterruptedException e)
      {
      }
   }

   private void startBusyThread()
   {
      // start a thread to constantly put another node in the cache to make sure the maxNodes is exceeded.
      // this should only happen AFTER the main node is entered to guarantee FIFO.
      busyThreadRunning = true;
      busyThread = new Thread("BusyThread")
      {
         public void run()
         {
            try
            {
               cacheInitialisedLatch.await();
            }
            catch (InterruptedException e)
            {
               // do nothing
            }

            while (busyThreadRunning)
            {
               cache.put(fqn2, "k", "v");
               TestingUtil.sleepRandom(150);
            }
         }
      };

      busyThread.setDaemon(true);
      busyThread.start();
   }
}
