package org.jboss.cache.transaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.lock.TimeoutException;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.UserTransaction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Unit test for local CacheImpl with concurrent transactions.
 * Uses locking and multiple threads to test concurrent r/w access to the tree.
 *
 * @author <a href="mailto:spohl@users.sourceforge.net">Stefan Pohl</a>
 * @author Ben Wang
 * @version $Revision: 7422 $
 */
@Test(groups = {"functional", "transaction"}, enabled = true, testName = "transaction.ConcurrentBankTest")
public class ConcurrentBankTest
{
   private CacheSPI<Object, Integer> cache;
   private static Log log = LogFactory.getLog(ConcurrentBankTest.class);
   private final Fqn NODE = Fqn.fromString("/cachetest");
   private final int ROLLBACK_CHANCE = 100;

   private static String customer[] = {"cu1", "cu2", "cu3"};
   private static final int BOOKINGS = 1000;
   private static boolean testFailedinThread = false;

   private void failMain()
   {
      testFailedinThread = true;
   }

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true);
      UnitTestCacheFactory<Object, Integer> instance = new UnitTestCacheFactory<Object, Integer>();
      cache = (CacheSPI<Object, Integer>) instance.createCache(conf, false, getClass());
      cache.getConfiguration().setIsolationLevel(IsolationLevel.SERIALIZABLE);
      cache.getConfiguration().setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      cache.create();
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
      // We just can't kill DummyTransactionManager. We are sharing single instance in more tests. 
      TestingUtil.killTransaction(TransactionSetup.getManager());      
   }

   public void testConcurrentBooking() throws Exception
   {
      Teller one, two;
      if (cache.getRoot().get(NODE) == null)
      {
         cache.put(NODE, "cu1", 1000);
         cache.put(NODE, "cu2", 1000);
         cache.put(NODE, "cu3", 1000);
      }

      one = new Teller("one", cache);
      two = new Teller("two", cache);

      one.start();
      TestingUtil.sleepThread((long) 100);
      two.start();
      one.join();
      two.join();

      assert !testFailedinThread;
   }

   private class Teller extends Thread
   {
      CacheSPI<Object, Integer> cache;

      public Teller(String str, CacheSPI<Object, Integer> cache)
      {
         super(str);
         this.cache = cache;
      }

      public void run()
      {
         int count = customer.length;
         UserTransaction tx = null;
         try
         {
            tx = TransactionSetup.getUserTransaction();

            boolean again = false;
            int src = 0;
            int dst = 0;
            int amo = 0;
            int anz = 0;
            while (anz < BOOKINGS)
            {
               if (!again)
               {
                  src = (int) (Math.random() * count);
                  dst = (int) (Math.random() * (count - 1));
                  amo = 1 + (int) (Math.random() * 20);
                  if (dst >= src)
                     dst++;
               }

               tx.begin();
               HashMap<Object, Integer> accounts = getAccounts();// read lock on NODE
               tx.commit();// releases read lock

               int sum = sumAccounts(accounts);
               // the sum of all accounts always has to be 3000
               if (sum != 3000)
               {
                  failMain();
                  return;// terminate thread
               }
               assertEquals("the sum of all accounts always has to be 3000", 3000, sum);

               try
               {
                  tx.begin();
                  deposit(customer[src], customer[dst], amo, tx);// gets write lock
                  tx.commit();// releases write lock
                  again = false;
               }
               catch (TimeoutException timeout_ex)
               {
                  tx.rollback();
                  again = true;
               }
               catch (Throwable e)
               {
                  tx.rollback();
                  again = true;
               }
               anz++;
               yield();
            }
         }
         catch (Throwable t)
         {
            t.printStackTrace();
            fail(t.toString());
         }
      }

      /**
       * Posting
       */
      public void deposit(String from, String to, int amount, UserTransaction tx) throws Exception
      {
         int act;
         // debit
         act = cache.get(NODE, from);
         cache.put(NODE, from, act - amount);

         // eventually rollback the transaction
         if ((int) (Math.random() * ROLLBACK_CHANCE) == 0)
         {
            tx.setRollbackOnly();
            throw new Exception("Manually set rollback!");
         }

         // credit
         act = cache.get(NODE, to);
         cache.put(NODE, to, act + amount);

      }

      /**
       * retrieving amounts of accounts
       */
      public HashMap<Object, Integer> getAccounts() throws CacheException
      {
         HashMap<Object, Integer> result = new HashMap<Object, Integer>();
         try
         {
            Set set = cache.getRoot().getChild(NODE).getKeys();// gets read lock
            for (Object name : set)
            {
               result.put(name, cache.get(NODE, name));
            }
            return result;
         }
         catch (CacheException ce)
         {
            throw ce;
         }
      }

      protected int sumAccounts(HashMap<Object, Integer> map)
      {
         Iterator<Integer> iter = map.values().iterator();
         int result = 0;
         while (iter.hasNext())
         {
            result += iter.next();
         }
         return result;
      }
   }
}
