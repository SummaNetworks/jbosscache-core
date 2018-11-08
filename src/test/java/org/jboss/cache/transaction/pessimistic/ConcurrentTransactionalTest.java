/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.transaction.pessimistic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.lock.IsolationLevel;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.UserTransaction;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Unit test for local CacheSPI. Use locking and multiple threads to test
 * concurrent access to the tree.
 *
 * @version $Id: ConcurrentTransactionalTest.java 7571 2009-01-22 19:48:46Z mircea.markus $
 */
@Test(groups = {"functional", "transaction"},  testName = "transaction.pessimistic.ConcurrentTransactionalTest")
public class ConcurrentTransactionalTest
{
   private volatile CacheSPI<Integer, String> cache;
   private Log logger_ = LogFactory.getLog(ConcurrentTransactionalTest.class);
   private volatile Throwable thread_ex = null;
   private static final int NUM = 1000;
   Log log = LogFactory.getLog(ConcurrentTransactionalTest.class);

   private void createCache(IsolationLevel level)
   {
      Configuration conf = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, false);
      conf.setCacheMode(Configuration.CacheMode.LOCAL);
      conf.setIsolationLevel(level);
      conf.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      conf.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(conf, getClass());
      cache.put("/a/b/c", null);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
      thread_ex = null;
      // We just can't kill DummyTransactionManager. We are sharing single instance in more tests.
      TestingUtil.killTransaction(TransactionSetup.getManager());
   }

   public void testConcurrentAccessWithRWLock() throws Throwable
   {
      createCache(IsolationLevel.REPEATABLE_READ);
      work_();
   }

   public void testConcurrentAccessWithExclusiveLock() throws Throwable
   {
      createCache(IsolationLevel.SERIALIZABLE);
      work_();
   }

   private void work_() throws Throwable
   {
      Updater one, two;
      try
      {
         one = new Updater("Thread one");
         two = new Updater("Thread two");
         long current = System.currentTimeMillis();
         one.start();
         two.start();
         one.join(30000);
         two.join(30000);
         if (thread_ex != null)
         {
            throw thread_ex;
         }

         long now = System.currentTimeMillis();
         log("*** Time elapsed: " + (now - current));

         Set<Integer> keys = cache.getNode(Fqn.fromString("/a/b/c")).getKeys();

         if (keys.size() != NUM)
         {
            scanForNullValues(keys);

            try
            {
               List<Integer> l = new LinkedList<Integer>(keys);
               Collections.sort(l);
               LinkedList<Integer> duplicates = new LinkedList<Integer>();
               for (Integer integer : l)
               {
                  if (!duplicates.contains(integer))
                  {
                     duplicates.add(integer);
                  }
               }
            }
            catch (Exception e1)
            {
               e1.printStackTrace();
            }
         }

         assertEquals(NUM, keys.size());
      }
      catch (Exception e)
      {
         log.error("Exception here: " + e, e);
         e.printStackTrace();
         fail(e.toString());
      }
   }

   private void scanForNullValues(Set<Integer> keys)
   {
      for (Object o : keys)
      {
         if (o == null)
         {
            System.err.println("found a null value in keys");
         }
      }
   }

   private void log(String msg)
   {
      logger_.debug(" [" + Thread.currentThread() + "]: " + msg);
   }

   private class Updater extends Thread
   {
      private String val = null;
      private UserTransaction tx;

      public Updater(String name)
      {
         this.val = name;
      }

      public void run()
      {
         try
         {
            log("adding data");
            tx = TransactionSetup.getUserTransaction();
            for (int i = 0; i < NUM; i++)
            {
               log(cache + " adding data i=" + i);
               tx.begin();
               cache.put("/a/b/c", i, val);
               tx.commit();
               yield();
            }
         }
         catch (Throwable t)
         {
            log.error("cache = " + cache + ", tx = " + tx+ t);
            thread_ex = t;
         }
      }
   }

}
