package org.jboss.cache.transaction.pessimistic;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.transaction.DummyBaseTransactionManager;
import org.jboss.cache.transaction.DummyTransaction;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.transaction.TransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.Status;
import javax.transaction.Synchronization;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.LinkedList;
import java.util.List;

/**
 * This test checks how the cache behaves when a JTA STATUS_UNKNOWN is passed in to the cache during afterCompletion().
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 */
@Test(groups = "functional", sequential = true, testName = "transaction.pessimistic.StatusUnknownTest")
public class StatusUnknownTest
{
   private Cache<String, String> cache;
   private TransactionManager tm;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = new UnitTestCacheFactory<String, String>().createCache(false, getClass());
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.getConfiguration().setTransactionManagerLookupClass(HeuristicFailingDummyTransactionManagerLookup.class.getName());
      cache.start();
      tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
      tm = null;
   }

   public void testStatusUnknown() throws Exception
   {
      tm.begin();
      Fqn fqn = Fqn.fromString("/a/b/c");

      cache.put(fqn, "k", "v");
      assertEquals(4, ((CacheSPI) cache).getNumberOfLocksHeld());
      assertTrue(cache.getRoot().hasChild(fqn));
      tm.commit();

      assertEquals(0, ((CacheSPI) cache).getNumberOfLocksHeld());
      assertFalse(cache.getRoot().hasChild(fqn));
   }

   public static class HeuristicFailingDummyTransactionManager extends DummyTransactionManager
   {
      private static final long serialVersionUID = 6325631394461739211L;
      
      // we can not share an instance with DummyTransactionManager
      private static DummyTransactionManager instance;
      
      @Override
      public void begin() throws SystemException, NotSupportedException
      {
         super.begin();

         Transaction tx = new HeuristicFailingDummyTransaction(this);
         setTransaction(tx);
      }

      public static DummyTransactionManager getInstance()
      {         
         if (instance == null)
         {
            instance = new HeuristicFailingDummyTransactionManager();
            /*
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
             */
         }
         return instance;
      }
   }

   public static class HeuristicFailingDummyTransaction extends DummyTransaction
   {
      public HeuristicFailingDummyTransaction(DummyBaseTransactionManager mgr)
      {
         super(mgr);
      }

      @Override
      public void commit() throws RollbackException
      {
         try
         {
            notifyBeforeCompletion();
            notifyAfterCompletion(Status.STATUS_UNKNOWN);
         }
         finally
         {
            // Disassociate tx from thread.
            tm_.setTransaction(null);
         }
      }

      @Override
      protected void notifyAfterCompletion(int status)
      {
         List<Synchronization> tmp;

         synchronized (participants)
         {
            tmp = new LinkedList<Synchronization>(participants);
         }

         for (Synchronization s : tmp)
         {
            try
            {
               s.afterCompletion(status);
            }
            catch (Throwable t)
            {
               throw (RuntimeException) t;
            }
         }

         synchronized (participants)
         {
            participants.clear();
         }
      }
   }

   public static class HeuristicFailingDummyTransactionManagerLookup implements TransactionManagerLookup
   {

      public TransactionManager getTransactionManager() throws Exception
      {
         return HeuristicFailingDummyTransactionManager.getInstance();
      }
   }
}


