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
 * Test putForExternalRead with pessimistic locking and INVALIDATION_ASYNC.
 *
 * @author Brian Stansberry
 * @version $Revision: 7168 $
 */
@Test(groups = {"functional", "pessimistic"}, testName = "api.pfer.PFERPessimisticInvalidationSyncTest")
public class PFERPessimisticInvalidationSyncTest extends PFERPessimisticTestBase
{
   public PFERPessimisticInvalidationSyncTest()
   {
      cacheMode = Configuration.CacheMode.INVALIDATION_SYNC;
   }
}
