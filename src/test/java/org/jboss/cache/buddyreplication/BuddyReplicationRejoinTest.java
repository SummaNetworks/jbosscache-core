package org.jboss.cache.buddyreplication;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheStatus;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.CachePrinter;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests fail over scenario when using buddy replication.
 * <p/>
 * What we are looking for is the last log output 'RECOVERED DATA', if all counter values
 * are zero then we have failed to recover the work done on the buddy node.
 * <p/>
 * Change the LATE_START_BUDDY_CACHE flag to trigger different startup behavior at the initial
 * object creation. It does not seem to have an impact on the outcome though.
 *                                                           
 * @author Fredrik Johansson, Cubeia Ltd
 */
@Test(groups = "functional", testName = "buddyreplication.BuddyReplicationRejoinTest")
public class BuddyReplicationRejoinTest extends BuddyReplicationTestsBase
{
   private static Log log = LogFactory.getLog(BuddyReplicationRejoinTest.class);

   /**
    * How many object we should insert to the cache.
    */
   private final static int OBJECT_COUNT = 10;

   private Cache<String, Integer> cache1;
   private Cache<String, Integer> cache2;

   @BeforeMethod
   public void setUp() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      brc.setBuddyCommunicationTimeout(1000);
      brc.setAutoDataGravitation(true);
      brc.setDataGravitationRemoveOnFind(true);
      brc.setDataGravitationSearchBackupTrees(true);
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setBuddyReplicationConfig(brc);

      // Cache1 will be used only for recovery.
      // Cache2 will perform some updates on the objects and then fail.

      cache1 = new UnitTestCacheFactory<String, Integer>().createCache(c, false, getClass());
      cache2 = new UnitTestCacheFactory<String, Integer>().createCache(c.clone(), false, getClass());
   }

   @AfterMethod
   public void tearDown() throws Exception
   {
      super.tearDown();
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   /**
    * Executes the test scenario which goes like this:
    * <p/>
    * 1. Start primary cache (cache1)
    * <p/>
    * 2. Add initial data to primary cache1. All counters are now 0.
    * <p/>
    * 3. Start secondary cache (cache2)
    * <p/>
    * 4. Let the secondary change data in half of the objects. The cache
    * will simply update a counter value. The values should now be 1
    * for those objects.
    * <p/>
    * This will trigger a data-gravitation to the secondary cache and
    * the objects should be removed from the primary cache's real data
    * <p/>
    * 5. Fail the secondary cache (cache.stop()). The primary cache should hold
    * valid values for the objects updated by the secondary cache (values
    * should be 2).
    * <p/>
    * 6. Print out all data in the primary cache. This will cause all objects
    * gravitate to the primary cache. We should have the the same state as
    * after #1 in list except that object 0-4 should have counter = 1.
    * <p/>
    * 7. Startup a secondary cache again.
    * <p/>
    * 8. Run the same update procedure. The secondary cache should gravitate objects
    * 0-4 and update the counters with +1, making the counters = 2.
    * <p/>
    * 9. Fail the secondary cache.
    * <p/>
    * 10. Print out all data in the primary cache. This will cause all objects
    * gravitate to the primary cache. We should have the the same state as
    * after #1 in list except that object 0-4 should have counter = 2 this time
    * since the second secondary cache should have update the values again.
    * <p/>
    * <p/>
    * FAIL.
    * <p/>
    * The recovered values in the end are 1, not 2.
    * It seems like the first buddy cache works fine, but he second time around it
    * fails to remove the local objects on then primary cache on the remote gravitation.
    * <p/>
    * The log printout in the end *should* look like:
    * <p/>
    * *********** RECOVERED DATA ***********
    * /0 counter = 2
    * /1 counter = 2
    * /2 counter = 2
    * /3 counter = 2
    * /4 counter = 2
    * /5 counter = 0
    * /6 counter = 0
    * /7 counter = 0
    * /8 counter = 0
    * /9 counter = 0
    * *********************************
    * <p/>
    * But we get 1's instead of 2's, which means that the recovery failed.
    */
   public void testGravitationAndFailover() throws CloneNotSupportedException
   {
      Configuration cfg = cache2.getConfiguration().clone();
      cache1.start();
      TestingUtil.sleepThread(100);

      addInitial(cache1);
      printCacheDetails("INITIAL STATES");

      cache2.start();
      printCacheDetails("CACHE2 STARTED");

      runBuddyUpdatesAndFail();

      checkRecoveredData(cache1, 1);
      printCacheDetails("DATA GRAVITATED BACK TO CACHE1");

      cache2 = new UnitTestCacheFactory<String, Integer>().createCache(cfg, getClass());
      printCacheDetails("BUDDY BACK");

      runBuddyUpdatesAndFail();

      checkRecoveredData(cache1, 2);
      printCacheDetails("DATA GRAVITATED BACK TO CACHE1 (AGAIN)");
   }


   private void runBuddyUpdatesAndFail()
   {
      executeBuddy(cache2);
      printCacheDetails("BUDDY UPDATED");

      cache2.stop();
      printCacheDetails("BUDDY FAILED");
   }


   /**
    * Change some data for a select number of objects on the given cache.
    *
    * @param cache
    */
   private void executeBuddy(Cache<String, Integer> cache)
   {
      for (int i = 0; i < OBJECT_COUNT / 2; i++)
      {
         Integer integer = cache.get(Fqn.fromString(String.valueOf(i)), "counter");
         cache.put(Fqn.fromString(String.valueOf(i)), "counter", integer + 1);
      }
   }


   /**
    * Add inital state to a cache
    *
    * @param cache
    */
   private void addInitial(Cache<String, Integer> cache)
   {
      for (int i = 0; i < OBJECT_COUNT; i++)
      {
         cache.put(Fqn.fromElements(String.valueOf(i)), "counter", 0);
      }
   }


   /**
    * Print all data to the log
    *
    * @param cache
    */
   private void checkRecoveredData(Cache<String, Integer> cache, int expectedValue)
   {
      log.info("*********** RECOVERED DATA ***********");
      for (int i = 0; i < OBJECT_COUNT; i++)
      {
         Integer counter = cache.get(Fqn.fromString(String.valueOf(i)), "counter");
         log.info("/" + i + " counter = " + counter);
         assert i < 5 ? counter == expectedValue : counter == 0;
      }
      log.info("*********************************");
   }


   private void printCacheDetails(String state)
   {
      log.info("*********** " + state + " ***********");
      if (cache1.getCacheStatus() == CacheStatus.STARTED)
      {
         log.info("--------- CACHE 1 (" + cache1.getLocalAddress() + ") ---------");
         log.info(CachePrinter.printCacheDetails(cache1));
      }
      else
      {
         log.info("--------- CACHE 1 STOPPED");
      }

      if (cache2.getCacheStatus() == CacheStatus.STARTED)
      {
         log.info("--------- CACHE 2 (" + cache2.getLocalAddress() + ") ---------");
         log.info(CachePrinter.printCacheDetails(cache2) + "\n\n");
      }
      else
      {
         log.info("--------- CACHE 2 STOPPED");
      }
   }
}
