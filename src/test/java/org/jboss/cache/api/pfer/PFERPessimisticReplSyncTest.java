package org.jboss.cache.api.pfer;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test(groups = {"functional", "pessimistic"}, testName = "api.pfer.PFERPessimisticReplSyncTest")
public class PFERPessimisticReplSyncTest extends PFERPessimisticTestBase
{
   public PFERPessimisticReplSyncTest()
   {
      cacheMode = Configuration.CacheMode.REPL_SYNC;
   }
}
