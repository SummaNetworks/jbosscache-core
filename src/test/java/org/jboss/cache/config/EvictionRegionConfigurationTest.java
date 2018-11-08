/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.config;


import org.jboss.cache.Fqn;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.eviction.EvictionPolicy;
import org.jboss.cache.eviction.LRUPolicy;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="mailto:brian.stansberry@jboss.org">Brian Stansberry</a>
 */
@Test(groups = {"functional", "jgroups", "transaction"}, testName = "config.EvictionRegionConfigurationTest")
public class EvictionRegionConfigurationTest
{
   /**
    * This test duplicates the way the JBoss Microcontainer goes about
    * building up an eviction config, and checks that at the
    * end of the process there is only one _default_ region configured.
    *
    * @throws Exception
    */
   public void testDuplicateDefaultRegion() throws Exception
   {
      EvictionConfig ec = new EvictionConfig();
      ec.setDefaultEvictionPolicyClass(LRUPolicy.class.getName());

      List<EvictionRegionConfig> ercs = ec.getEvictionRegionConfigs();

      EvictionRegionConfig erc = new EvictionRegionConfig();
      erc.setRegionFqn(RegionManagerImpl.DEFAULT_REGION);
      EvictionPolicy policy = LRUPolicy.class.newInstance();
      erc.setEvictionPolicyConfig(policy.getEvictionConfigurationClass().newInstance());

      ercs.add(erc);

      ec.setEvictionRegionConfigs(ercs);

      ercs = ec.getEvictionRegionConfigs();

      Set<Fqn> fqns = new HashSet<Fqn>();
      for (EvictionRegionConfig cfg : ercs)
      {
         if (fqns.contains(cfg.getRegionFqn()))
            fail("duplicate region fqn " + cfg.getRegionFqn());
         fqns.add(cfg.getRegionFqn());
      }

   }
}
