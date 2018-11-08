package org.jboss.cache.api.pfer;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.pfer.PFERMvccInvalidationSyncTest")
public class PFERMvccInvalidationSyncTest extends PFERMVCCTestBase
{
   public PFERMvccInvalidationSyncTest()
   {
      cacheMode = Configuration.CacheMode.INVALIDATION_SYNC;
   }
}
