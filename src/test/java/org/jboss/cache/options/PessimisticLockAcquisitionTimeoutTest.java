/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Option;
import org.jboss.cache.lock.TimeoutException;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import org.jboss.cache.util.TestingUtil;

/**
 * Test functionality of {@link Option#setLockAcquisitionTimeout(int)}.
 *
 * @author Brian Stansberry
 */
@Test(groups = {"functional"}, sequential = true, testName = "options.PessimisticLockAcquisitionTimeoutTest")
public class PessimisticLockAcquisitionTimeoutTest
{
   private static final Log log = LogFactory.getLog(PessimisticLockAcquisitionTimeoutTest.class);

   private static final Fqn FQNA = Fqn.fromString("/A");
   private static final Fqn FQNB = Fqn.fromString("/B");
   private static final String KEY = "key";
   private static final String VALUE1 = "value1";
   private static final String VALUE2 = "value2";

   private CacheSPI<Object, Object> cache;
   private Option option;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration c = new Configuration();
      c.setCacheMode("REPL_SYNC");
      c.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");

      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      cache.start();

      option = new Option();
      option.setLockAcquisitionTimeout(0);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   /**
    * Confirms that doing a put with a lockAcquisitionTime option set
    * does the put as expected. There is no other thread or tx contesting
    * the lock the put needs, so this is just a simple test that the option
    * doesn't somehow screw up the put.
    *
    * @throws Exception
    */
   public void testSimplePut() throws Exception
   {
      log.info("++++ testSimplePut() ++++");
      simplePutTest(false);
   }

   /**
    * Confirms that doing a put with a lockAcquisitionTime option set
    * does the put as expected when executed within a transaction. There is no
    * other thread or tx contesting the lock the put needs, so this is just a
    * simple test that the option doesn't somehow screw up the put.
    *
    * @throws Exception
    */
   public void testSimplePutWithTx() throws Exception
   {
      log.info("++++ testSimplePutWithTx() ++++");
      simplePutTest(true);
   }

   private void simplePutTest(boolean useTx) throws Exception
   {
      TransactionManager tm = useTx ? cache.getTransactionManager() : null;
      LowTimeoutSetter setter = new LowTimeoutSetter(tm);
      setter.start();

      setter.join(2000);
      if (!setter.finished)
      {
         setter.interrupt();
         fail("Puts failed to complete in a timely manner");
      }

      assertNull("LowTimeoutSetter saw no TimeoutException", setter.te);
      assertNull("LowTimeoutSetter saw no misc Exception", setter.failure);
      assertEquals("Cache correct for " + FQNA, VALUE2, cache.get(FQNA, KEY));
      assertEquals("Cache correct for " + FQNB, VALUE2, cache.get(FQNB, KEY));
   }

   /**
    * Confirms that a put with a lockAcquisitionTimeout option set to zero
    * fails promptly in the presence of a lock on the target node.
    *
    * @throws Exception
    */
   public void testContestedPut() throws Exception
   {
      log.info("++++ testContestedPut() ++++");
      contestedPutTest(false);
   }

   /**
    * Confirms that a put with a lockAcquisitionTimeout option set to zero
    * is ignored if executed within a transaction.
    *
    * @throws Exception
    */
   public void testContestedPutWithTx() throws Exception
   {
      log.info("++++ testContestedPutWithTx() ++++");
      contestedPutTest(true);
   }

   private void contestedPutTest(boolean tx) throws Exception
   {
      TransactionManager mgr = cache.getTransactionManager();
      mgr.begin();

      LowTimeoutSetter setter = null;
      try
      {
         // Put a WL on /A
         cache.put(FQNA, KEY, VALUE1);

         // Launch a thread that tries to write to /A
         setter = new LowTimeoutSetter(tx ? mgr : null);
         setter.start();

         setter.join(2000);
         if (!setter.finished)
         {
            setter.interrupt();
            fail("Puts failed to complete in a timely manner");
         }
      }
      finally
      {
         // always commit the tx
         mgr.commit();
      }

      assertNotNull("LowTimeoutSetter saw TimeoutException", setter.te);
      assertNull("LowTimeoutSetter saw no misc Exception", setter.failure);
      assertEquals("Cache correct for " + FQNA, VALUE1, cache.get(FQNA, KEY));
      assertEquals("Cache correct for " + FQNB, VALUE2, cache.get(FQNB, KEY));

   }

   public void testSimpleRead() throws Exception
   {
      log.info("++++++ testSimpleRead() ++++++");
      simpleReadTest(false);
   }

   public void testSimpleReadWithTx() throws Exception
   {
      log.info("++++++ testSimpleReadWithTx() ++++++");
      simpleReadTest(true);
   }

   private void simpleReadTest(boolean useTx) throws Exception
   {
      TransactionManager tm = useTx ? cache.getTransactionManager() : null;
      LowTimeoutReader reader = new LowTimeoutReader(tm);

      cache.put(FQNA, KEY, VALUE1);

      reader.start();

      reader.join(2000);
      if (!reader.finished)
      {
         reader.interrupt();
         fail("Read failed to complete in a timely manner");
      }

      assertNull("LowTimeoutSetter saw no TimeoutException", reader.te);
      assertNull("LowTimeoutSetter saw no misc Exception", reader.failure);
      assertEquals("LowTimeoutSetter correct for " + FQNA, VALUE1, reader.value);

   }

   public void testContestedRead() throws Exception
   {
      log.info("++++++ testContestedRead() ++++++");
      contestedReadTest(false);
   }

   public void testContestedReadWithTx() throws Exception
   {
      log.info("++++++ testContestedReadWithTx() ++++++");
      contestedReadTest(true);
   }

   private void contestedReadTest(boolean tx) throws Exception
   {
      TransactionManager mgr = cache.getTransactionManager();
      mgr.begin();

      LowTimeoutReader reader = null;
      try
      {
         // Put a WL on /A
         cache.put(FQNA, KEY, VALUE1);

         // Launch a thread that tries to read from /A
         reader = new LowTimeoutReader(tx ? mgr : null);
         reader.start();

         reader.join(2000);
         if (!reader.finished)
         {
            reader.interrupt();
            fail("Read failed to complete in a timely manner");
         }
      }
      finally
      {
         // always commit the tx
         mgr.commit();
      }

      assertNotNull("LowTimeoutSetter saw TimeoutException", reader.te);
      assertNull("LowTimeoutSetter saw no misc Exception", reader.failure);
      assertNull("LowTimeoutSetter unable to read " + FQNA, reader.value);

   }

   class LowTimeoutSetter extends Thread
   {
      TransactionManager tm;
      TimeoutException te;
      Throwable failure;
      boolean finished;

      LowTimeoutSetter(TransactionManager tm)
      {
         this.tm = tm;
      }

      public void run()
      {
         try
         {
            try
            {
               if (tm != null)
               {
                  tm.begin();
               }

               cache.put(FQNB, KEY, VALUE2);

               cache.getInvocationContext().setOptionOverrides(option);
               cache.put(FQNA, KEY, VALUE2);
            }
            catch (TimeoutException te)
            {
               this.te = te;
            }
            catch (Exception e)
            {
               if (tm != null)
                  tm.setRollbackOnly();
               throw e;
            }
            finally
            {
               if (tm != null)
               {
                  tm.commit();
               }
               finished = true;
            }
         }
         catch (Throwable t)
         {
            failure = t;
         }
      }
   }

   class LowTimeoutReader extends Thread
   {
      TransactionManager tm;
      TimeoutException te;
      Throwable failure;
      Object value;
      boolean finished;

      LowTimeoutReader(TransactionManager tm)
      {
         this.tm = tm;
      }

      public void run()
      {
         try
         {
            try
            {
               if (tm != null)
               {
                  tm.begin();
               }

               cache.getInvocationContext().setOptionOverrides(option);
               value = cache.get(FQNA, KEY);
            }
            catch (TimeoutException te)
            {
               this.te = te;
            }
            catch (Exception e)
            {
               if (tm != null)
                  tm.setRollbackOnly();
               throw e;
            }
            finally
            {
               if (tm != null)
               {
                  tm.commit();
               }
               finished = true;
            }
         }
         catch (Throwable t)
         {
            failure = t;
         }
      }
   }
}
