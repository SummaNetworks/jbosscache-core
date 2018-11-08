package org.jboss.cache.api.pfer;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.optimistic.TransactionWorkspace;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.transaction.TransactionContext;
import org.jboss.cache.transaction.TransactionTable;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertNotNull;

import java.util.Map;

public abstract class PFEROptimisticTestBase extends PutForExternalReadTestBase
{
   protected PFEROptimisticTestBase()
   {
      nodeLockingScheme = NodeLockingScheme.OPTIMISTIC;
   }

   @Override
   @SuppressWarnings("unchecked")
   protected void assertLocked(Fqn fqn, CacheSPI cache, boolean writeLocked)
   {
      TransactionTable tt = cache.getTransactionTable();
      GlobalTransaction gtx = tt.getCurrentTransaction();
      OptimisticTransactionContext otc = (OptimisticTransactionContext) cache.getTransactionTable().get(gtx);

      if (otc == null && gtx == null)
      {
         // perhaps the tx has been suspended?
         Map<GlobalTransaction, TransactionContext> gtx2ContextMap = (Map<GlobalTransaction, TransactionContext>) TestingUtil.extractField(tt, "gtx2ContextMap");
         assert gtx2ContextMap.size() == 1 : "Can only attempt to access a suspended tx if there is only one such suspended tx!";
         gtx = gtx2ContextMap.keySet().iterator().next();
         otc = (OptimisticTransactionContext) gtx2ContextMap.get(gtx);
      }

      if (otc == null) throw new NullPointerException("No transaction context available!");

      TransactionWorkspace workspace = otc.getTransactionWorkSpace();
      // scan workspaces for this node
      assertNotNull("node " + fqn + " should be in transaction workspace", workspace.getNode(fqn));
   }
}
