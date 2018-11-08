package org.jboss.cache.api.pfer;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.pfer.PFERMvccInvalidationAsyncTest")
public class PFERMvccInvalidationAsyncTest extends PFERMVCCTestBase
{
   public PFERMvccInvalidationAsyncTest()
   {
      cacheMode = Configuration.CacheMode.INVALIDATION_ASYNC;
   }
}
