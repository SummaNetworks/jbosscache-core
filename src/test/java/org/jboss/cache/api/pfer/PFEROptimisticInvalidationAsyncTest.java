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
 * Test putForExternalRead with optimistic locking and INVALIDATION_ASYNC.
 *
 * @author Brian Stansberry
 * @version $Revision: 7168 $
 */
@Test(groups = {"functional", "optimistic"}, testName = "api.pfer.PFEROptimisticInvalidationAsyncTest")
public class PFEROptimisticInvalidationAsyncTest extends PFEROptimisticTestBase
{
   public PFEROptimisticInvalidationAsyncTest()
   {
      cacheMode = Configuration.CacheMode.INVALIDATION_ASYNC;
   }
}
