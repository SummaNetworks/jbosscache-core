/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;

/**
 * Tests handling of the buddy backup region during region
 * activation and inactivation
 *
 * @author Brian Stansberry
 */
@Test(groups = "functional", testName = "buddyreplication.Buddy2NodesBackupActivationInactivationTest")
public class Buddy2NodesBackupActivationInactivationTest extends AbstractNodeBasedBuddyTest
{
   public static final Fqn A = Fqn.fromString("/a");
   public static final Fqn A_B = Fqn.fromString("/a/b");
   public static final String JOE = "JOE";
   

   @BeforeClass
   public void createCaches() throws Exception
   {
      super.caches = new ArrayList<CacheSPI<Object, Object>>(2);
      caches.add(createCache());
      caches.add(createCache());
   }

   public void testBuddyBackupActivation() throws Exception
   {
      CacheSPI cache1 = caches.get(0);
      CacheSPI cache2 = caches.get(1);
      Fqn A = Fqn.fromString("/a");
      TestingUtil.blockUntilViewsReceived(VIEW_BLOCK_TIMEOUT, cache1, cache2);

      // create the regions on the two cachePool first
      Region c1 = cache1.getRegionManager().getRegion(A, Region.Type.MARSHALLING, true);
      Region c2 = cache2.getRegionManager().getRegion(A, Region.Type.MARSHALLING, true);

      assertFalse(c1.isActive());
      assertFalse(c2.isActive());

      c1.activate();
      cache1.put(A_B, "name", JOE);

      waitForBuddy(cache2, cache1, true);
      waitForBuddy(cache1, cache2, true);

//      TestingUtil.sleepThread(getSleepTimeout());

      c2.activate();

      Fqn fqn = fqnTransformer.getBackupFqn(cache1.getLocalAddress(), A_B);

      assertEquals("State transferred with activation", JOE, cache2.get(fqn, "name"));
   }

   public void testReplToInactiveRegion() throws Exception
   {
      CacheSPI cache1 = caches.get(0);
      CacheSPI cache2 = caches.get(1);

      TestingUtil.blockUntilViewsReceived(VIEW_BLOCK_TIMEOUT, cache1, cache2);
      Fqn backupFqn = fqnTransformer.getBackupFqn(cache1.getLocalAddress(), A_B);
      Fqn A = Fqn.fromString("/a");

      Region regionA = cache1.getRegion(A, true);
      regionA.registerContextClassLoader(getClass().getClassLoader());
      regionA.activate();

      // Activate the buddy backup subtree in the recipient so any
      // repl message doesn't get rejected due to that tree being inactive
      cache2.getRegionManager().activate(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN);
      cache2.getRegionManager().deactivate(A);

      cache1.put(A_B, "name", JOE);

      assertNull("Should be no replication to inactive region", cache2.get(A_B, "name"));

      assertNull("Should be no replication to inactive backup region", cache2.get(backupFqn, "name"));
   }

   public void testBuddyBackupInactivation() throws Exception
   {
      CacheSPI cache1 = caches.get(0);
      Fqn A = Fqn.fromString("/a");
      Region regionA = cache1.getRegion(A, true);
      regionA.registerContextClassLoader(getClass().getClassLoader());
      regionA.activate();

      Fqn fqn = Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, "test");
      fqn = Fqn.fromRelativeFqn(fqn, A_B);
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put(fqn, "name", JOE);

      assertEquals("Put should have been OK", JOE, cache1.get(fqn, "name"));

      regionA.deactivate();

      assertNull("Inactivation should have cleared region", cache1.get(fqn, "name"));
   }

   protected CacheSPI<Object, Object> createCache() throws Exception
   {
      CacheMode mode = CacheMode.REPL_SYNC;
      Configuration c = UnitTestConfigurationFactory.createConfiguration(mode);
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      cache.getConfiguration().setUseRegionBasedMarshalling(true);
      cache.getConfiguration().setInactiveOnStartup(true);
      cache.getConfiguration().setBuddyReplicationConfig(getBuddyConfig());
      cache.create();
      cache.start();
      return cache;
   }

   private BuddyReplicationConfig getBuddyConfig() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      brc.setAutoDataGravitation(false);
      return brc;
   }
}
