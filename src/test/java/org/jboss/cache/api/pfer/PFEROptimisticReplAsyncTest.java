package org.jboss.cache.api.pfer;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test(groups = {"functional", "optimistic"}, testName = "api.pfer.PFEROptimisticReplAsyncTest")
public class PFEROptimisticReplAsyncTest extends PFEROptimisticTestBase
{
   public PFEROptimisticReplAsyncTest()
   {
      cacheMode = Configuration.CacheMode.REPL_ASYNC;
   }
}
