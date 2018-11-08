/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.commands.write.PutDataMapCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.loader.CacheLoader;
import static org.jboss.cache.util.SingleBuddyGravitationHelper.expectGravitation;
import static org.jboss.cache.util.SingleBuddyGravitationHelper.*;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.SingleBuddyGravitationHelper;
import static org.jboss.cache.util.TestingUtil.dumpCacheContents;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests use of the data gravitator alongside other cache loaders as well as data gravitator options such as removeOnFind.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = "functional", testName = "buddyreplication.BuddyReplicationWithCacheLoaderTest")
public class BuddyReplicationWithCacheLoaderTest extends BuddyReplicationTestsBase
{
   protected Fqn fqn = Fqn.fromString("/test/br/four/level");
   protected String key = "key";
   protected String value = "value";
   protected boolean passivation = false;


   private CacheLoader[] getLoaders(List<CacheSPI<Object, Object>> caches)
   {
      CacheLoader[] retVal = new CacheLoader[caches.size()];

      for (int i = 0; i < retVal.length; i++)
      {
         retVal[i] = caches.get(i).getCacheLoaderManager().getCacheLoader();
      }

      return retVal;
   }

   public void testWithDataGravitationDefault() throws Exception
   {
      dataGravitationDefaultTest(true);
   }

   public void testWithDataGravitationDefaultNoAuto() throws Exception
   {
      dataGravitationDefaultTest(false);
   }

   private void dataGravitationDefaultTest(boolean autoGravitate) throws Exception
   {
      // create 3 cachePool
      List<CacheSPI<Object, Object>> caches = createCachesWithCacheLoader(3, autoGravitate, true, passivation);
      cachesTL.set(caches);
      List<ReplicationListener> replicationListeners = new ArrayList<ReplicationListener>();
      replicationListeners.add(ReplicationListener.getReplicationListener(caches.get(0)));
      replicationListeners.add(ReplicationListener.getReplicationListener(caches.get(1)));
      replicationListeners.add(ReplicationListener.getReplicationListener(caches.get(2)));

      CacheLoader[] loaders = getLoaders(caches);

      // cleanup
      for (int i = 0; i < 3; i++) loaders[i].remove(Fqn.ROOT);

      // put stuff in cache0
      replicationListeners.get(1).expect(PutKeyValueCommand.class);
      caches.get(0).put(fqn, key, value);
      replicationListeners.get(1).waitForReplicationToOccur();

      // make sure there are no locks.
      assertNoLocks(caches);
      dumpCacheContents(caches);

      // request data from cache2
      if (!autoGravitate)
         caches.get(2).getInvocationContext().getOptionOverrides().setForceDataGravitation(true);

      inReplicationListeners(replicationListeners).dataWillGravitateFrom(0).to(2);
      // should cause a gravitation event
      assertEquals(value, caches.get(2).get(fqn, key));
      expectGravitation();

      assertNoLocks(caches);

      dumpCacheContents(caches);

      // test that data does not exist in cache0
      assertTrue("should not exist in cache0", !caches.get(0).exists(fqn));

      // test that data does not exist in cache1
      assertTrue("should not exist in cache1", !caches.get(1).exists(fqn));

      // test that data does exist in cache2
      assertTrue("should exist in cache2", caches.get(2).exists(fqn));

      // test that data does not exist in loader0
      assertTrue("should not exist in loader0", !loaders[0].exists(fqn));

      // test that data does not exist in loader1
      assertTrue("should not exist in loader1", !loaders[1].exists(fqn));

      // test that data does exist in loader2
      assertTrue("should exist in loader2", passivation ? !loaders[2].exists(fqn) : loaders[2].exists(fqn));

      Fqn b1 = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), fqn);
      Fqn b2 = fqnTransformer.getBackupFqn(caches.get(2).getLocalAddress(), fqn);

      // test that bkup does exist in cache0
      assertTrue("should not exist in cache0", !caches.get(0).exists(b1));
      assertTrue("should exist in cache0", caches.get(0).exists(b2));

      // test that bkup does not exist in cache1
      assertTrue("should not exist in cache1", !caches.get(1).exists(b1));
      assertTrue("should not exist in cache1", !caches.get(1).exists(b2));

      // test that bkup does not exist in cache2
      assertTrue("should not exist in cache2", !caches.get(2).exists(b1));
      assertTrue("should not exist in cache2", !caches.get(2).exists(b2));

      // test that bkup does exist in loader0
      assertTrue("should not exist in loader0", !loaders[0].exists(b1));
      assertTrue("should exist in loader0", passivation ? !loaders[0].exists(b2) : loaders[0].exists(b2));

      // test that bkup does not exist in loader1
      assertTrue("should not exist in loaders1", !loaders[1].exists(b1));
      assertTrue("should not exist in loaders1", !loaders[1].exists(b2));

      // test that bkup does not exist in loader2
      assertTrue("should not exist in loaders2", !loaders[2].exists(b1));
      assertTrue("should not exist in loaders2", !loaders[2].exists(b2));
   }

   /**
    * Tests data gravitation when "removeOnFind=false"; i.e. nodes
    * from which data is gravitated evict it instead of removing it.
    *
    * @throws Exception
    */
   public void testWithDataGravitationEvictOnFind() throws Exception
   {
      dataGravitationEvictionTest(true);
   }

   /**
    * Tests data gravitation when auto-gravitation is disabled and
    * "removeOnFind=false"; i.e. nodes from which data is gravitated
    * evict it instead of removing it.
    */
   public void testWithDataGravitationEvictOnFindNoAuto() throws Exception
   {
      dataGravitationEvictionTest(false);
   }

   private void dataGravitationEvictionTest(boolean autoGravitate) throws Exception
   {
      // create 3 cachePool
      List<CacheSPI<Object, Object>> caches = createCachesWithCacheLoader(3, autoGravitate, false, passivation);
      ReplicationListener replListener0 = ReplicationListener.getReplicationListener(caches.get(0));
      ReplicationListener replListener1 = ReplicationListener.getReplicationListener(caches.get(1));
      ReplicationListener replListener2 = ReplicationListener.getReplicationListener(caches.get(2));

      cachesTL.set(caches);
      CacheLoader[] loaders = getLoaders(caches);
      Fqn b1 = fqnTransformer.getBackupFqn(caches.get(0).getLocalAddress(), fqn);
      Fqn b2 = fqnTransformer.getBackupFqn(caches.get(2).getLocalAddress(), fqn);

      // put stuff in cache0
      replListener1.expect(PutKeyValueCommand.class);
      caches.get(0).put(fqn, key, value);
      replListener1.waitForReplicationToOccur();

      // request data from cache2
      if (!autoGravitate)
         caches.get(2).getInvocationContext().getOptionOverrides().setForceDataGravitation(true);

      // should cause a gravitation event
      SingleBuddyGravitationHelper.inReplicationListeners(replListener0, replListener1, replListener2).dataWillGravitateFrom(0).to(2);
      assertEquals(value, caches.get(2).get(fqn, key));
      expectGravitation();

//      USE REPLICATION LISTENERS!!!!

      // test that data does not exist in cache0
      assertTrue("should not exist in cache0", !caches.get(0).exists(fqn));

      // test that data does not exist in cache1
      assertTrue("should not exist in cache1", !caches.get(1).exists(fqn));

      // test that data does exist in cache2
      assertTrue("should exist in cache2", caches.get(2).exists(fqn));

      // test that data does exist in loader0
      assertTrue("should exist in loader0", loaders[0].exists(fqn));

      // test that data does not exist in loader1
      assertTrue("should not exist in loader1", !loaders[1].exists(fqn));

      // test that data does exist in loader2
      assertTrue("should exist in loader2", passivation ? !loaders[2].exists(fqn) : loaders[2].exists(fqn));

      // test that bkup does exist in cache0
      assertTrue("should not exist in cache0", !caches.get(0).exists(b1));
      assertTrue("should exist in cache0", caches.get(0).exists(b2));

      // test that bkup does not exist in cache1
      assertTrue("should not exist in cache1", !caches.get(1).exists(b1));
      assertTrue("should not exist in cache1", !caches.get(1).exists(b2));

      // test that bkup does not exist in cache2
      assertTrue("should not exist in cache2", !caches.get(2).exists(b1));
      assertTrue("should not exist in cache2", !caches.get(2).exists(b2));

      // test that bkup does exist in loader0
      assertTrue("should not exist in loader0", !loaders[0].exists(b1));
      assertTrue("should exist in loader0", passivation ? !loaders[0].exists(b2) : loaders[0].exists(b2));

      // test that bkup does not exist in loader1
      assertTrue("should exist in loaders1", loaders[1].exists(b1));
      assertTrue("should not exist in loaders1", !loaders[1].exists(b2));

      // test that bkup does not exist in loader2
      assertTrue("should not exist in loaders2", !loaders[2].exists(b1));
      assertTrue("should not exist in loaders2", !loaders[2].exists(b2));
   }

   /**
    * Tests whether nodes that have been evicted can successfully be
    * gravitated.
    *
    * @throws Exception
    */
   public void testLocalGravitationOfEvictedNodes() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCacheWithCacheLoader(true, true, passivation, true, false);
      Configuration cfg1 = cache1.getConfiguration();
      Configuration cfg0 = cfg1.clone();
      CacheSPI<Object, Object> cache0 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(cfg0, false, getClass());

      // Store them for the teardown method
      List<CacheSPI<Object, Object>> caches = new ArrayList<CacheSPI<Object, Object>>();
      cachesTL.set(caches);
      caches.add(cache0);
      caches.add(cache1);

      cache0.start();
      cache1.start();

      TestingUtil.blockUntilViewsReceived(caches.toArray(new Cache[caches.size()]), VIEW_BLOCK_TIMEOUT * caches.size());
      TestingUtil.sleepThread(getSleepTimeout());


      Fqn foo = Fqn.fromString("/foo");
      Fqn backupFoo = fqnTransformer.getBackupFqn(cache0.getLocalAddress(), foo);
      cache0.put(foo, "key", "value");

      assert cache0.exists(foo) : "Data should exist in data owner";
      assert cache1.exists(backupFoo) : "Buddy should have data";

      // Sleep long enough for eviction to run twice plus a bit
      cache0.evict(foo);
      cache1.evict(backupFoo);

      // test that the data we're looking for has been evicted in both the data owner and the buddy.
      assert !cache0.exists(foo) : "Data should have evicted in data owner";
      assert !cache1.exists(backupFoo) : "Buddy should have data evicted";

      // now test that this exists in both loaders.
      assert cache0.getCacheLoaderManager().getCacheLoader().get(foo) != null : "Should exist in data owner's cache loader";
      assert cache1.getCacheLoaderManager().getCacheLoader().get(backupFoo) != null : "Should exist in buddy's loader";

      // a local gravitation should occur since cache1 has foo in its backup tree.
      assertEquals("Passivated value available from buddy", "value", cache1.get(foo, "key"));
   }

   /**
    * Tests whether nodes that have been evicted can successfully be
    * gravitated.
    *
    * @throws Exception
    */
   public void testRemoteGravitationOfEvictedNodes() throws Exception
   {
      CacheSPI<Object, Object> cache0 = createCacheWithCacheLoader(true, true, passivation, true, false);
      Configuration cfg0 = cache0.getConfiguration();
      Configuration cfg1 = cfg0.clone();
      CacheSPI<Object, Object> cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(cfg1, false, getClass());
      Configuration cfg2 = cfg0.clone();
      CacheSPI<Object, Object> cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(cfg2, false, getClass());

      // Store them for the teardown method
      List<CacheSPI<Object, Object>> caches = new ArrayList<CacheSPI<Object, Object>>();
      cachesTL.set(caches);
      caches.add(cache0);
      caches.add(cache1);
      caches.add(cache2);

      cache0.start();
      cache1.start();
      cache2.start();

      TestingUtil.blockUntilViewsReceived(caches.toArray(new Cache[caches.size()]), 60000);
      TestingUtil.sleepThread(getSleepTimeout());


      assert (cache0.getBuddyManager().getBuddyAddresses().contains(cache1.getLocalAddress())) : "Cache1 should be cache0's buddy!";

      Fqn foo = Fqn.fromString("/foo");
      Fqn backupFoo = fqnTransformer.getBackupFqn(cache0.getLocalAddress(), foo);
      cache0.put(foo, "key", "value");

      // test that the data exists in both the data owner and the buddy
      assert cache0.exists(foo) : "Data should exist in data owner";
      assert cache1.exists(backupFoo) : "Buddy should have data";

      cache0.evict(foo);
      cache1.evict(backupFoo);

      // test that the data we're looking for has been evicted in both the data owner and the buddy.
      assert !cache0.exists(foo) : "Data should have evicted in data owner";
      assert !cache1.exists(backupFoo) : "Buddy should have data evicted";

      // now test that this exists in both loaders.
      assert cache0.getCacheLoaderManager().getCacheLoader().get(foo) != null : "Should exist in data owner's loader";
      assert cache1.getCacheLoaderManager().getCacheLoader().get(backupFoo) != null : "Should exist in buddy's loader";

      // doing a get on cache2 will guarantee a remote data gravitation.
      assertEquals("Passivated value available from buddy", "value", cache2.get(foo, "key"));
   }
}
