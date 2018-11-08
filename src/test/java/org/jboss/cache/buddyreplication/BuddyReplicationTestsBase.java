/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.jboss.cache.*;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.CacheLoaderManager;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.Address;
import static org.testng.AssertJUnit.*;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for BR tests
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
public abstract class BuddyReplicationTestsBase
{
   protected final ThreadLocal<List<CacheSPI<Object, Object>>> cachesTL = new ThreadLocal<List<CacheSPI<Object, Object>>>();
   protected final BuddyFqnTransformer fqnTransformer = new BuddyFqnTransformer();
   protected final Log log = LogFactory.getLog(getClass());  

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      System.setProperty("org.jboss.cache.shutdown.force", "true");
      List<CacheSPI<Object, Object>> caches = cachesTL.get();
      if (caches != null)
      {
         // an optimisation to aid the progress of unit tests, especially in the case of TCP connections.  Note that this
         // is NOT necessary in live systems since each cache would typically be in a separate JVM.
         for (CacheSPI c : caches)
         {
            if (c != null && c.getBuddyManager() != null) c.getBuddyManager().stop();
         }
         cleanupCaches(caches, true);
      }
      cachesTL.set(null);
      System.gc();
      new UnitTestCacheFactory().cleanUp();
   }

   protected void cleanupCaches(List<CacheSPI<Object, Object>> caches, boolean stop)
   {
      for (CacheSPI c : caches)
      {
         if (c != null)
         {
            TransactionManager tm = c.getTransactionManager();
            if (tm != null)
            {
               try
               {
                  if (tm.getTransaction() != null) tm.rollback();
               }
               catch (Exception e)
               {
                  // error rolling back gtx2EntryMap
                  e.printStackTrace();
               }
            }

            CacheLoaderManager clm = c.getCacheLoaderManager();
            if (clm != null)
            {
               CacheLoader cl = c.getCacheLoaderManager().getCacheLoader();
               try
               {
                  if (cl != null) cl.remove(Fqn.ROOT);
               }
               catch (Exception e)
               {
                  // unable to clean cache loader
                  e.printStackTrace();
               }
            }
            if (stop)
            {
               TestingUtil.killCaches(c);
            }
            else
            {
               if (c.getComponentRegistry().getState().allowInvocations()) c.removeNode(Fqn.ROOT);
            }
         }
      }
   }

   protected final static int VIEW_BLOCK_TIMEOUT = 5000;

   protected CacheSPI<Object, Object> createCache(int numBuddies, String buddyPoolName) throws Exception
   {
      return createCache(numBuddies, buddyPoolName, false, true);
   }

   protected CacheSPI<?, ?> createCache(int numBuddies, String buddyPoolName, boolean useDataGravitation) throws Exception
   {
      return createCache(numBuddies, buddyPoolName, useDataGravitation, true);
   }

   protected CacheSPI<Object, Object> createCache(int numBuddies, String buddyPoolName, boolean useDataGravitation, boolean start) throws Exception
   {
      return createCache(false, numBuddies, buddyPoolName, useDataGravitation, true, start);
   }

   protected CacheSPI<Object, Object> createCache(boolean optimisticLocks, int numBuddies, String buddyPoolName, boolean useDataGravitation, boolean start) throws Exception
   {
      return createCache(optimisticLocks, numBuddies, buddyPoolName, useDataGravitation, true, start);
   }

   protected CacheSPI<?, ?> createCache(int numBuddies, String buddyPoolName, boolean useDataGravitation, boolean removeOnFind, boolean start) throws Exception
   {
      return createCache(false, numBuddies, buddyPoolName, useDataGravitation, removeOnFind, start);
   }

   protected CacheSPI<Object, Object> createCache(boolean optimisticLocks, int numBuddies, String buddyPoolName, boolean useDataGravitation, boolean removeOnFind, boolean start) throws Exception
   {
      CacheSPI<Object, Object> c = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC, false, false, true), false, getClass());

      String threadId = Thread.currentThread().getName();
      //c.getConfiguration().setClusterName("BuddyReplicationTest-" + threadId);

      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      if (buddyPoolName != null) brc.setBuddyPoolName(buddyPoolName);
      brc.setEnabled(true);
      brc.setDataGravitationRemoveOnFind(removeOnFind);
      brc.setDataGravitationSearchBackupTrees(true);
      brc.setAutoDataGravitation(useDataGravitation);
      NextMemberBuddyLocatorConfig nextMemberBuddyLocatorConfig = new NextMemberBuddyLocatorConfig();
      nextMemberBuddyLocatorConfig.setNumBuddies(numBuddies);
      brc.setBuddyLocatorConfig(nextMemberBuddyLocatorConfig);
      c.getConfiguration().setBuddyReplicationConfig(brc);

      c.getConfiguration().setFetchInMemoryState(true);
      c.getConfiguration().setNodeLockingScheme(optimisticLocks ? NodeLockingScheme.OPTIMISTIC : getNonOptimisticLockingScheme());

      c.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      c.getConfiguration().setSyncCommitPhase(true);// helps track down breakages

      // Call the hook that allows mux integration if that's what the test wants
      configureMultiplexer(c);

      if (start)
      {
         c.start();
         validateMultiplexer(c);
      }
      return c;
   }

   protected NodeLockingScheme getNonOptimisticLockingScheme()
   {
      return NodeLockingScheme.PESSIMISTIC;
   }

   /**
    * Provides a hook for multiplexer integration. This default implementation
    * is a no-op; subclasses that test mux integration would override
    * to integrate the given cache with a multiplexer.
    * <p/>
    * param cache a cache that has been configured but not yet created.
    */
   protected void configureMultiplexer(Cache cache) throws Exception
   {
      // default does nothing
   }

   /**
    * Provides a hook to check that the cache's channel came from the
    * multiplexer, or not, as expected.  This default impl asserts that
    * the channel did not come from the multiplexer.
    *
    * @param cache a cache that has already been started
    */
   protected void validateMultiplexer(Cache cache)
   {
      assertFalse("Cache is not using multiplexer", cache.getConfiguration().isUsingMultiplexer());
   }

   protected List<CacheSPI<Object, Object>> createCaches(int numCaches, boolean useBuddyPool) throws Exception
   {
      List<CacheSPI<Object, Object>> spiList = createCaches(1, numCaches, useBuddyPool, false);
      waitForSingleBuddy(spiList);
      return spiList;
   }

   protected List<CacheSPI<Object, Object>> createCaches(int numCaches, boolean useBuddyPool, boolean useDataGravitation, boolean optimisticLocks) throws Exception
   {
      List<CacheSPI<Object, Object>> spiList = createCaches(1, numCaches, useBuddyPool, useDataGravitation, optimisticLocks);
      waitForSingleBuddy(spiList);
      return spiList;
   }

   protected List<CacheSPI<Object, Object>> createCaches(int numCaches, boolean useBuddyPool, boolean useDataGravitation) throws Exception
   {
      List<CacheSPI<Object, Object>> spiList = createCaches(1, numCaches, useBuddyPool, useDataGravitation);
      waitForSingleBuddy(spiList);
      return spiList;
   }

   protected List<CacheSPI<Object, Object>> createCachesWithCacheLoader(int numCaches, boolean useDataGravitation, boolean removeOnFind, boolean passivation) throws Exception
   {
      return this.createCachesWithCacheLoader(numCaches, useDataGravitation, removeOnFind, passivation, false);
   }

   protected List<CacheSPI<Object, Object>> createCachesWithCacheLoader(int numCaches, boolean useDataGravitation, boolean removeOnFind, boolean passivation, boolean fetchPersistent) throws Exception
   {
      List<CacheSPI<Object, Object>> caches = new ArrayList<CacheSPI<Object, Object>>();
      for (int i = 0; i < numCaches; i++)
      {
         caches.add(createCacheWithCacheLoader(useDataGravitation, removeOnFind, passivation, fetchPersistent, true));
      }

      // allow some time for the cachePool to start up and discover each other
      TestingUtil.blockUntilViewsReceived(caches.toArray(new Cache[0]), VIEW_BLOCK_TIMEOUT);
      waitForSingleBuddy(caches);
      return caches;
   }

   protected CacheSPI createCacheWithCacheLoader(boolean useDataGravitation, boolean removeOnFind, boolean passivation, boolean fetchPersistent, boolean start) throws Exception
   {
      CacheSPI cache = createCache(1, null, useDataGravitation, removeOnFind, false);

      CacheLoaderConfig config = new CacheLoaderConfig();
      IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
      iclc.setClassName(DummyInMemoryCacheLoader.class.getName());
      iclc.setFetchPersistentState(fetchPersistent);
      config.addIndividualCacheLoaderConfig(iclc);
      config.setShared(false);
      config.setPassivation(passivation);
      cache.getConfiguration().setCacheLoaderConfig(config);
      if (start)
      {
         cache.start();
      }

      return cache;
   }

   protected List<CacheSPI<Object, Object>> createCaches(int numBuddies, int numCaches, boolean useBuddyPool) throws Exception
   {
      return createCaches(numBuddies, numCaches, useBuddyPool, false);
   }

   protected List<CacheSPI<Object, Object>> createCaches(int numBuddies, int numCaches, boolean useBuddyPool, boolean useDataGravitation) throws Exception
   {
      return createCaches(numBuddies, numCaches, useBuddyPool, useDataGravitation, false);
   }

   protected List<CacheSPI<Object, Object>> createCaches(int numBuddies, int numCaches, boolean useBuddyPool, boolean useDataGravitation, boolean optimisticLocks) throws Exception
   {
      return createCaches(numBuddies, numCaches, useBuddyPool, useDataGravitation, optimisticLocks, true);
   }

   protected List<CacheSPI<Object, Object>> createCaches(int numBuddies, int numCaches, boolean useBuddyPool, boolean useDataGravitation, boolean optimisticLocks, boolean start) throws Exception
   {
      List<CacheSPI<Object, Object>> caches = new ArrayList<CacheSPI<Object, Object>>(numCaches);
      for (int i = 0; i < numCaches; i++)
         caches.add(createCache(optimisticLocks, numBuddies, useBuddyPool ? Character.toString((char) ('A' + i)) : null, useDataGravitation, start));

      if (start)
      {
         // allow some time for the cachePool to start up and discover each other
         TestingUtil.blockUntilViewsReceived(caches.toArray(new Cache[0]), VIEW_BLOCK_TIMEOUT);
         TestingUtil.sleepThread(getSleepTimeout());
      }

      return caches;
   }

   /**
    * This is to allow for any state transfers involved (when assigning a buddy) to complete
    */
   protected int getSleepTimeout()
   {
      return 1000;
   }

   protected static void assertIsBuddy(Cache dataOwner, Cache buddy, boolean onlyBuddy)
   {
      Address dataOwnerLocalAddress = dataOwner.getLocalAddress();
      Address buddyLocalAddress = buddy.getLocalAddress();


      BuddyManager dataOwnerBuddyManager = ((CacheSPI) dataOwner).getBuddyManager();
      BuddyManager buddyBuddyManager = ((CacheSPI) buddy).getBuddyManager();

      // lets test things on the data owner's side of things
      if (onlyBuddy) assertEquals("Should only have one buddy" + getViewsString(dataOwner, buddy), 1, dataOwnerBuddyManager.getBuddyAddresses().size());

      assertTrue(buddyLocalAddress + " should be a buddy to " + dataOwnerLocalAddress + getViewsString(dataOwner, buddy), dataOwnerBuddyManager.getBuddyAddresses().contains(buddyLocalAddress));

      // and now on the buddy end
      BuddyGroup group = buddyBuddyManager.buddyGroupsIParticipateIn.get(dataOwnerLocalAddress);

      assertTrue("buddy's list of groups it participates in should contain data owner's group name" + getViewsString(dataOwner, buddy), buddyBuddyManager.buddyGroupsIParticipateIn.containsKey(dataOwnerLocalAddress));
      if (onlyBuddy) assertEquals(1, group.getBuddies().size());
      assertTrue(buddyLocalAddress + " should be a buddy to " + group.getGroupName() + getViewsString(dataOwner, buddy), group.getBuddies().contains(buddyLocalAddress));
   }

   private static String getViewsString(Cache dataOwner, Cache buddy)
   {
      return "[dataOwnerView: {" + dataOwner.getMembers()+ "}, buddyViewIs: {" + buddy.getMembers() + "}]";
   }

   public static void waitForBuddy(Cache dataOwner, Cache buddy, boolean onlyBuddy) throws Exception
   {
      waitForBuddy(dataOwner, buddy, onlyBuddy, 60000);
   }



   public static void waitForSingleBuddy(List caches) throws Exception
   {
      Cache[] array = (Cache[]) caches.toArray(new Cache[0]);
      waitForSingleBuddy(array);
   }

   /**
    * Will wait for 60 secs + 10sec * caches.length for the given caches to become buddys.
    * The caches should be ordered as per underlying view.
    */
   public static void waitForSingleBuddy(Cache... caches) throws Exception
   {
      long timeout = 60000 + caches.length;
      for (int i = 0; i < caches.length - 1; i++)
      {
         waitForBuddy(caches[i], caches[i + 1], true, timeout);
      }
      waitForBuddy(caches[caches.length - 1], caches[0], true, timeout);
   }

   public static void waitForBuddy(Cache dataOwner, Cache buddy, boolean onlyBuddy, long timeout) throws Exception
   {
      long start = System.currentTimeMillis();
      while ((System.currentTimeMillis() - start) < timeout)
      {
         if (isBuddy(dataOwner, buddy, onlyBuddy)) return;
         Thread.sleep(50);
      }
      //give it a last chance, just to have a nice printed message
      assertIsBuddy(dataOwner, buddy, onlyBuddy);
   }


   private static boolean isBuddy(Cache dataOwner, Cache buddy, boolean onlyBuddy)
   {
      Address dataOwnerLocalAddress = dataOwner.getLocalAddress();
      Address buddyLocalAddress = buddy.getLocalAddress();
      BuddyManager dataOwnerBuddyManager = ((CacheSPI) dataOwner).getBuddyManager();
      BuddyManager buddyBuddyManager = ((CacheSPI) buddy).getBuddyManager();
      boolean result = true;
      // lets test things on the data owner's side of things
      if (onlyBuddy) result = result && (1 == dataOwnerBuddyManager.getBuddyAddresses().size());
      result = result && dataOwnerBuddyManager.getBuddyAddresses().contains(buddyLocalAddress);

      // and now on the buddy end
      BuddyGroup group = buddyBuddyManager.buddyGroupsIParticipateIn.get(dataOwnerLocalAddress);
      result = result & buddyBuddyManager.buddyGroupsIParticipateIn.containsKey(dataOwnerLocalAddress);
      if (onlyBuddy) result = result && group.getBuddies().size() == 1;
      result = result & group != null && group.getBuddies() != null && group.getBuddies().contains(buddyLocalAddress);
      return result;
   }

   protected void assertNoLocks(List<CacheSPI<Object, Object>> caches)
   {
      for (Cache cache : caches)
      {
         if (cache != null) assert ((CacheSPI) cache).getNumberOfLocksHeld() < 1 : cache.getLocalAddress() + " still holds locks";
      }
   }

   public void assertNoStaleLocks(List<CacheSPI<Object, Object>> caches)
   {
      for (CacheSPI<Object, Object> cache : caches) assertNoStaleLocks(cache);
   }

   private void assertNoStaleLocks(CacheSPI<Object, Object> cache)
   {
      assertEquals("Number of locks in cache instance " + cache + " should be 0", 0, cache.getNumberOfLocksHeld());
   }

   protected void checkConsistentPoolState(List<CacheSPI<Object, Object>> caches)
   {
      for (int i = 0; i < caches.size(); i++)
      {
         Map groupMap = caches.get(i).getBuddyManager().buddyPool;
         for (int j = 0; j < caches.size(); j++)
         {
            if (i != j)
            {
               Map groupMap2 = caches.get(j).getBuddyManager().buddyPool;
               for (CacheSPI cache : caches)
               {
                  assertEquals("Comparing contents of cache " + (i + 1) + " pool map with cache " + (j + 1), groupMap.get(cache), groupMap2.get(cache));
               }
            }
         }
      }
   }
}
