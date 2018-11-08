package org.jboss.cache.api.optimistic;

import org.jboss.cache.api.CacheAPITest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;


/**
 * Optimistically locked version of {@link org.jboss.cache.api.CacheAPITest}
 */
@Test(groups = {"functional", "optimistic"}, testName = "api.optimistic.CacheAPIOptimisticTest")
public class CacheAPIOptimisticTest extends CacheAPITest
{
   @Override
   protected NodeLockingScheme getNodeLockingScheme()
   {
      return NodeLockingScheme.OPTIMISTIC;
   }

}
