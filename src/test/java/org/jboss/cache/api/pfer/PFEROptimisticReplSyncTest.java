package org.jboss.cache.api.pfer;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test(groups = {"functional", "optimistic"}, testName = "api.pfer.PFEROptimisticReplSyncTest")
public class PFEROptimisticReplSyncTest extends PFEROptimisticTestBase
{
   public PFEROptimisticReplSyncTest()
   {
      cacheMode = Configuration.CacheMode.REPL_SYNC;
   }
}
