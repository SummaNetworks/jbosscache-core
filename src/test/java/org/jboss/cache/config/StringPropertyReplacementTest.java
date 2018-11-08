/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.config;


import org.jboss.cache.config.BuddyReplicationConfig.BuddyLocatorConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests that string property replacement works properly when parsing
 * a config file.  JBCACHE-1218
 *
 * @author Brian Stansberry
 */
@Test(groups = {"functional"}, testName = "config.StringPropertyReplacementTest")
public class StringPropertyReplacementTest
{
   public static final String STRING_REPLACED_FILE = "configs/string-property-replaced.xml";

   private static final String PROP_BASE = "test.property.";
   private static final String SYNC_COMMIT_PROP = PROP_BASE + "SyncCommitPhase";
   private static final String NUM_BUDDIES_PROP = PROP_BASE + "BuddyReplicationConfig.numBuddies";
   private static final String MAX_NODES_PROP = PROP_BASE + "EvictionPolicyConfig.maxNodes";
   private static final String BUDDY_POOL_PROP = PROP_BASE + "BuddyReplicationConfig.buddyPoolName";

   private String numBuddies;
   private String syncCommitPhase;
   private String maxNodes;
   private String buddyPoolName;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      numBuddies = System.getProperty(NUM_BUDDIES_PROP);
      syncCommitPhase = System.getProperty(SYNC_COMMIT_PROP);
      maxNodes = System.getProperty(MAX_NODES_PROP);
      buddyPoolName = System.getProperty(BUDDY_POOL_PROP);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (numBuddies == null)
         System.clearProperty(NUM_BUDDIES_PROP);
      else
         System.setProperty(NUM_BUDDIES_PROP, numBuddies);

      if (syncCommitPhase == null)
         System.clearProperty(SYNC_COMMIT_PROP);
      else
         System.setProperty(SYNC_COMMIT_PROP, syncCommitPhase);

      if (maxNodes == null)
         System.clearProperty(MAX_NODES_PROP);
      else
         System.setProperty(MAX_NODES_PROP, maxNodes);

      if (buddyPoolName == null)
         System.clearProperty(BUDDY_POOL_PROP);
      else
         System.setProperty(BUDDY_POOL_PROP, buddyPoolName);
   }

   public void testStringPropertyReplacement() throws Exception
   {
      System.setProperty(NUM_BUDDIES_PROP, "3");
      System.setProperty(SYNC_COMMIT_PROP, "false");
      System.setProperty(MAX_NODES_PROP, "1000");
      System.setProperty(BUDDY_POOL_PROP, "replaced");

      Configuration cfg = new XmlConfigurationParser().parseFile(STRING_REPLACED_FILE);

      assertEquals(NodeLockingScheme.MVCC, cfg.getNodeLockingScheme());
      assertFalse(cfg.isSyncCommitPhase());
      assertTrue(cfg.isSyncRollbackPhase());
      assertEquals(15000, cfg.getLockAcquisitionTimeout());
      String clusterCfg = cfg.getClusterConfig();
      assertTrue(clusterCfg == null || clusterCfg.length() == 0);

      EvictionConfig ec = cfg.getEvictionConfig();
      assert ec.getDefaultEvictionRegionConfig().getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig;
      EvictionRegionConfig erc = ec.getDefaultEvictionRegionConfig();
      LRUAlgorithmConfig epc = (LRUAlgorithmConfig) erc.getEvictionAlgorithmConfig();
      assertEquals(1000, epc.getMaxNodes());

      CacheLoaderConfig clc = cfg.getCacheLoaderConfig();
      IndividualCacheLoaderConfig iclc = clc.getFirstCacheLoaderConfig();
      assertEquals(System.getProperty("java.io.tmpdir"), iclc.getProperties().get("location"));

      BuddyReplicationConfig brc = cfg.getBuddyReplicationConfig();
      assertTrue(brc.isEnabled());
      assertEquals("replaced", brc.getBuddyPoolName());
      BuddyLocatorConfig blc = brc.getBuddyLocatorConfig();
      assertEquals("3", blc.getBuddyLocatorProperties().get("numBuddies"));
   }

}
