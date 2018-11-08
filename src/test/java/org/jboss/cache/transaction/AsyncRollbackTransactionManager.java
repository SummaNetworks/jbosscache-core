package org.jboss.cache.transaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.InvalidTransactionException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class AsyncRollbackTransactionManager extends DummyTransactionManager
{
   private static final long serialVersionUID = 5793952292960075970L;
   static AsyncRollbackTransactionManager instance = null;
   private static Log log = LogFactory.getLog(AsyncRollbackTransactionManager.class);

   public static DummyTransactionManager getInstance()
   {
      if (instance == null)
      {
         instance = new AsyncRollbackTransactionManager();
         try
         {
            Properties p = new Properties();
            p.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
            Context ctx = new InitialContext(p);
            ctx.bind("java:/TransactionManager", instance);
            ctx.bind("UserTransaction", new DummyUserTransaction(instance));
         }
         catch (NamingException e)
         {
            log.error("binding of DummyTransactionManager failed", e);
         }
      }
      return instance;
   }

   private Thread timedOutTransactionsChecker = null;
   private int timeout = 30;
   private Map<Long, AsyncRollbackTransaction> txMap = new HashMap<Long, AsyncRollbackTransaction>();

   public void setTransactionTimeout(int seconds) throws SystemException
   {
      this.timeout = seconds;
   }

   public AsyncRollbackTransactionManager()
   {
      timedOutTransactionsChecker = new TimedOutTransactionsChecker();
      timedOutTransactionsChecker.start();
   }

   private class TimedOutTransactionsChecker extends Thread
   {

      public TimedOutTransactionsChecker()
      {
      }

      public void run()
      {
         while (true)
         {
            try
            {
               Thread.sleep(50);
               synchronized (this)
               {
                  Iterator<AsyncRollbackTransaction> iterator = txMap.values().iterator();
                  do
                  {
                     if (!iterator.hasNext())
                     {
                        break;
                     }
                     AsyncRollbackTransaction t = iterator.next();
                     try
                     {
                        t.wakeUp();
                     }
                     catch (SystemException e)
                     {
                        e.printStackTrace();
                     }

                  }
                  while (true);
               }
            }
            catch (InterruptedException e)
            {
            }
         }
      }
   }

   public void begin() throws NotSupportedException, SystemException
   {
      Transaction currentTx;
      if ((currentTx = getTransaction()) != null)
      {
         throw new NotSupportedException(Thread.currentThread() +
               " is already associated with a transaction (" + currentTx + ")");
      }
      AsyncRollbackTransaction tx = new AsyncRollbackTransaction(this, timeout);
      setTransaction(tx);
      txMap.put(tx.generateTransactionId(), tx);
   }


   public void rollback() throws IllegalStateException, SecurityException, SystemException
   {
      removeTxFromMap((AsyncRollbackTransaction) getTransaction());
      super.rollback();
   }

   public void removeTxFromMap(AsyncRollbackTransaction tx) throws SystemException
   {
      if (tx != null)
      {
         txMap.remove(tx.getTransactionId());
      }
   }


   public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException
   {
      AsyncRollbackTransaction tx = (AsyncRollbackTransaction) getTransaction();
      if (tx != null)
      {
         txMap.remove(tx.getTransactionId());
      }
      super.commit();
   }

   public void resume(Transaction tx) throws InvalidTransactionException, IllegalStateException, SystemException
   {
      super.resume(tx);
   }

   public Transaction suspend() throws SystemException
   {
      return super.suspend();
   }

   static class AsyncRollbackTransaction extends DummyTransaction
   {
      private static long transactionNums = 0;

      private long transactionId;

      private long beginTimeMillis;

      private int timeoutSec;

      public AsyncRollbackTransaction(DummyBaseTransactionManager tm, int timeout)
      {
         super(tm);
         this.timeoutSec = timeout;
         this.beginTimeMillis = System.currentTimeMillis();
      }

      /**
       * @return the transactionId
       */
      public long getTransactionId()
      {
         return transactionId;
      }

      public long generateTransactionId()
      {
         long result = 0;
         synchronized (AsyncRollbackTransaction.class)
         {
            transactionNums++;
            result = transactionNums;
         }
         this.transactionId = result;
         return result;
      }

      final int getTimeoutSeconds()
      {
         return timeoutSec;
      }

      protected final void asyncRollback() throws SystemException
      {
         Thread asyncRollbackThread = new Thread()
         {
            public void run()
            {
               try
               {
                  rollback();
               }
               catch (Exception exception)
               {
               }
            }
         };
         ((AsyncRollbackTransactionManager) tm_).removeTxFromMap(this);
         asyncRollbackThread.start();
      }

      public void wakeUp() throws SystemException
      {
         if (isTransactionTimedOut())
         {
            asyncRollback();
         }
      }

      private boolean isTransactionTimedOut()
      {
         return (System.currentTimeMillis() - beginTimeMillis) > (timeoutSec * 1000);
      }
   }


}