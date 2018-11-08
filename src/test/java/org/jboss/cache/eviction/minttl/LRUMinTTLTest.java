package org.jboss.cache.eviction.minttl;

import org.jboss.cache.eviction.EvictionAlgorithmConfigBase;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional"}, testName = "eviction.minttl.LRUMinTTLTest")
public class LRUMinTTLTest extends MinTTLTestBase
{
   @Override
   protected EvictionAlgorithmConfigBase getEvictionAlgorithmConfig()
   {
      LRUAlgorithmConfig cfg = new LRUAlgorithmConfig();
      cfg.setTimeToLive(200);
      return cfg;
   }
}
