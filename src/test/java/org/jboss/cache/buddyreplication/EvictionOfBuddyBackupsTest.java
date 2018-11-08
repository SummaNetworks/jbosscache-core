package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.eviction.NullEvictionAlgorithmConfig;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
/**
 * Tests the eviction of buddy backup regions
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.2.0
 */
@Test(groups = "functional", testName = "buddyreplication.EvictionOfBuddyBackupsTest")
public class EvictionOfBuddyBackupsTest extends BuddyReplicationTestsBase
{
   private CacheSPI cache1, cache2;
   private Fqn fqn = Fqn.fromString("/a/b/c");
  private EvictionController ec1;
  private EvictionController ec2;

  @BeforeMethod
   public void setUp() throws Exception
   {
      cache1 = createCache(1, null, true, false);
      cache1.getConfiguration().setEvictionConfig(getEvictionConfig());
      cache1.start();
      ec1 = new EvictionController(cache1);

      cache2 = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(cache1.getConfiguration().clone(), getClass());
      ec2 = new EvictionController(cache2);
      waitForSingleBuddy(cache1, cache2);

      TestingUtil.blockUntilViewsReceived(60000, cache1, cache2);
   }

   @AfterMethod
   @Override
   public void tearDown() throws Exception           
   {
      super.tearDown();
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   private EvictionConfig getEvictionConfig()
   {
      EvictionConfig c = new EvictionConfig();
      EvictionRegionConfig defaultRegion = new EvictionRegionConfig(Fqn.ROOT, new NullEvictionAlgorithmConfig());
      c.setDefaultEvictionRegionConfig(defaultRegion);
      c.setWakeupInterval(0);

      LRUAlgorithmConfig lru = new LRUAlgorithmConfig(1000, 1000);
      EvictionRegionConfig subregion = new EvictionRegionConfig(fqn, lru);
      c.addEvictionRegionConfig(subregion);
      return c;
   }


   public void testEvictionOfBackupRegions() throws Exception
   {
      ReplicationListener replicationListener2 = ReplicationListener.getReplicationListener(cache2);
      replicationListener2.expect(PutKeyValueCommand.class);
      cache1.put(fqn, "k", "v");
      replicationListener2.waitForReplicationToOccur();

      assert cache1.peek(fqn, false, false) != null : "Node should exist";
      assert cache2.peek(fqnTransformer.getBackupFqn(cache1.getLocalAddress(), fqn), false, false) != null : "Node should exist on backup";

      // now wait for eviction to kick in - for up to 2 secs
      TestingUtil.sleepThread(1100);
      ec1.startEviction();
      ec2.startEviction();

      assert cache1.peek(fqn, false, false) == null : "Node should have evicted";
      assert cache2.peek(fqnTransformer.getBackupFqn(cache1.getLocalAddress(), fqn), false, false) == null : "Node should have evicted on backup";
   }
}
