package org.jboss.cache.api.pfer;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.pfer.PFERMvccReplAsyncTest")
public class PFERMvccReplAsyncTest extends PFERMVCCTestBase
{
   public PFERMvccReplAsyncTest()
   {
      cacheMode = Configuration.CacheMode.REPL_ASYNC;
   }
}
