package org.jboss.cache.api.pfer;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test(groups = {"functional", "pessimistic"}, testName = "api.pfer.PFERPessimisticReplAsyncTest")
public class PFERPessimisticReplAsyncTest extends PFERPessimisticTestBase
{
   public PFERPessimisticReplAsyncTest()
   {
      cacheMode = Configuration.CacheMode.REPL_ASYNC;
   }
}
