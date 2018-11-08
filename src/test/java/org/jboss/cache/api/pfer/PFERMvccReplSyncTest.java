package org.jboss.cache.api.pfer;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.pfer.PFERMvccReplSyncTest")
public class PFERMvccReplSyncTest extends PFERMVCCTestBase
{
   public PFERMvccReplSyncTest()
   {
      cacheMode = Configuration.CacheMode.REPL_SYNC;
   }
}
