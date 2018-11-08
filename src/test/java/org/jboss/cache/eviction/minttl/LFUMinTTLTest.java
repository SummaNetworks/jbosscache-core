package org.jboss.cache.eviction.minttl;

import org.jboss.cache.eviction.EvictionAlgorithmConfigBase;
import org.jboss.cache.eviction.LFUAlgorithmConfig;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional"}, testName = "eviction.minttl.LFUMinTTLTest")
public class LFUMinTTLTest extends MinTTLTestBase
{
   @Override
   protected EvictionAlgorithmConfigBase getEvictionAlgorithmConfig()
   {
      return new LFUAlgorithmConfig();
   }
}
