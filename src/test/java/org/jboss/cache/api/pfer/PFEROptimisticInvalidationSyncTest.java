/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.api.pfer;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

/**
 * Test putForExternalRead with optimistic locking and INVALIDATION_SYNC.
 *
 * @author Brian Stansberry
 * @version $Revision: 7168 $
 */
@Test(groups = {"functional", "optimistic"}, testName = "api.pfer.PFEROptimisticInvalidationSyncTest")
public class PFEROptimisticInvalidationSyncTest extends PFEROptimisticTestBase
{
   public PFEROptimisticInvalidationSyncTest()
   {
      cacheMode = Configuration.CacheMode.INVALIDATION_SYNC;
   }
}
