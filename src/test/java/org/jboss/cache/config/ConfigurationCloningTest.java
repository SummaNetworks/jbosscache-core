/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.config;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig.SingletonStoreConfig;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Properties;

/**
 * Tests the ability to clone Configuration elements and end up with
 * independently modifiable configurations.
 *
 * @author Brian Stansberry
 */
@Test(groups = {"functional"}, testName = "config.ConfigurationCloningTest")
public class ConfigurationCloningTest
{
   /**
    * A file that includes every configuration element I could think of
    */
   public static final String DEFAULT_CONFIGURATION_FILE = "configs/clonable-config.xml";

   private static final Log log = LogFactory.getLog(ConfigurationCloningTest.class);

   public void testClone() throws Exception
   {
      XmlConfigurationParser parser = new XmlConfigurationParser();
      Configuration c = parser.parseFile(DEFAULT_CONFIGURATION_FILE);

      try
      {
         Configuration clone = c.clone();

         // Test a few simple properties 
         assertEquals(NodeLockingScheme.OPTIMISTIC, clone.getNodeLockingScheme());
         assertEquals(CacheMode.REPL_SYNC, clone.getCacheMode());
         assertEquals("CloneCluster", clone.getClusterName());
         assertEquals(c.getClusterConfig(), clone.getClusterConfig());
         assertEquals(3, clone.getStateRetrievalTimeout());

         // Eviction
         EvictionConfig ec1 = c.getEvictionConfig();
         EvictionConfig ec2 = clone.getEvictionConfig();

         assertFalse(ec1 == ec2);

         assertEquals(4, ec2.getDefaultEvictionRegionConfig().getEventQueueSize());
         assertEquals(45000, ec2.getWakeupInterval());
         assert ec2.getDefaultEvictionRegionConfig().getEvictionAlgorithmConfig() instanceof LRUAlgorithmConfig;

         List<EvictionRegionConfig> ercs1 = ec1.getEvictionRegionConfigs();
         List<EvictionRegionConfig> ercs2 = ec2.getEvictionRegionConfigs();
         assertEquals(ercs1.size(), ercs2.size());
         for (int i = 0; i < ercs1.size(); i++)
         {
            compareEvictionRegionConfigs(ercs1.get(i), ercs2.get(i));
         }

         // Cache loading
         CacheLoaderConfig clc1 = c.getCacheLoaderConfig();
         CacheLoaderConfig clc2 = clone.getCacheLoaderConfig();

         assertFalse(clc1 == clc2);

         assertFalse(clc2.isPassivation());
         assertTrue(clc2.isShared());

         List<IndividualCacheLoaderConfig> clcs1 = clc1.getIndividualCacheLoaderConfigs();
         List<IndividualCacheLoaderConfig> clcs2 = clc2.getIndividualCacheLoaderConfigs();
         assertEquals(clcs1.size(), clcs2.size());
         for (int i = 0; i < clcs1.size(); i++)
         {
            compareCacheLoaderConfigs(clcs1.get(i), clcs2.get(i));
         }

         RuntimeConfig rc1 = c.getRuntimeConfig();
         RuntimeConfig rc2 = clone.getRuntimeConfig();
         assertFalse(rc1 == rc2);
         assertEquals(rc1, rc2);

      }
      catch (CloneNotSupportedException e)
      {
         log.error(e.getMessage(), e);
         fail("Cloning failed -- " + e.getMessage());
      }
   }

   private void compareEvictionRegionConfigs(EvictionRegionConfig erc1,
                                             EvictionRegionConfig erc2)
   {
      assertEquals(erc1.getRegionName(), erc2.getRegionName());
      assertEquals(erc1.getRegionFqn(), erc2.getRegionFqn());
      assertEquals(erc1.getEventQueueSize(), erc2.getEventQueueSize());

      EvictionAlgorithmConfig epc1 = erc1.getEvictionAlgorithmConfig();
      EvictionAlgorithmConfig epc2 = erc2.getEvictionAlgorithmConfig();

      assertFalse(epc1 == epc2);
      assertEquals(epc1, epc2);
   }

   private void compareCacheLoaderConfigs(IndividualCacheLoaderConfig clc1,
                                          IndividualCacheLoaderConfig clc2)
   {
      assertFalse(clc1 == clc2);
      assertEquals(clc1, clc2);

      Properties p1 = clc1.getProperties();
      Properties p2 = clc2.getProperties();
      assertFalse(p1 == p2);
      assertEquals(p1, p2);

      SingletonStoreConfig ssc1 = clc1.getSingletonStoreConfig();
      SingletonStoreConfig ssc2 = clc2.getSingletonStoreConfig();
      assertFalse(ssc1 == ssc2);
      assertEquals(ssc1, ssc2);
   }

}
