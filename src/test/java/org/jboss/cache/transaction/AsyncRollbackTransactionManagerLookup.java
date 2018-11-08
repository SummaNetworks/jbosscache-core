package org.jboss.cache.transaction;


import javax.transaction.TransactionManager;

public class AsyncRollbackTransactionManagerLookup implements TransactionManagerLookup
{
   public TransactionManager getTransactionManager() throws Exception
   {
      return AsyncRollbackTransactionManager.getInstance();
   }

}