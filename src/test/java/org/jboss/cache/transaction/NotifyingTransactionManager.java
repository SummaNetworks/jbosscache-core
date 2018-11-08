/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.transaction;


import org.jboss.cache.CacheSPI;

import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * A dummy transaction manager that notifies registered listeners of the various phases of a 2PC so exceptions, etc. can be injected.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
public class NotifyingTransactionManager extends DummyTransactionManager implements TransactionManagerLookup
{

   private static final long serialVersionUID = -2994163352889758708L;

   private Notification notification;
   private CacheSPI cache;

   @Override
   public void commit() throws HeuristicMixedException, SystemException, HeuristicRollbackException, RollbackException
   {
      notifyListeners();
      super.commit();
   }

   @Override
   public void rollback() throws SystemException
   {
      notifyListeners();
      super.rollback();
   }

   private void notifyListeners()
   {
      try
      {
         log.debug("Calling notification.notify()");
         TransactionTable txTable = cache.getTransactionTable();
         Transaction tx = getTransaction();
         GlobalTransaction gtx = txTable.get(tx);
         notification.notify(tx, txTable.get(gtx));
      }
      catch (Exception e)
      {
         log.debug(e);
      }
   }

   public TransactionManager getTransactionManager() throws Exception
   {
      return this;
   }

   public interface Notification
   {
      public void notify(Transaction tx, TransactionContext transactionContext) throws SystemException, RollbackException;
   }

   public CacheSPI getCache()
   {
      return cache;
   }

   public void setCache(CacheSPI cache)
   {
      this.cache = cache;
   }

   public Notification getNotification()
   {
      return notification;
   }

   public void setNotification(Notification notification)
   {
      this.notification = notification;
   }

}



