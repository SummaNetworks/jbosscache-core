/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.statetransfer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

@Test(groups="functional", testName = "statetransfer.NonBlockingStateTransferTest")
public class NonBlockingStateTransferTest
{
   public static final Fqn A = Fqn.fromString("/a");
   public static final Fqn B = Fqn.fromString("/b");
   public static final Fqn C = Fqn.fromString("/c");
   protected static final String ADDRESS_CLASSNAME = "org.jboss.cache.marshall.data.Address";
   protected static final String PERSON_CLASSNAME = "org.jboss.cache.marshall.data.Person";
   public static final Fqn A_B = Fqn.fromString("/a/b");
   public static final Fqn A_C = Fqn.fromString("/a/c");
   public static final Fqn A_D = Fqn.fromString("/a/d");
   public static final String JOE = "JOE";
   public static final String BOB = "BOB";
   public static final String JANE = "JANE";
   public static final Integer TWENTY = 20;
   public static final Integer FORTY = 40;

   private volatile int testCount = 0;

   private static final Log log = LogFactory.getLog(NonBlockingStateTransferTest.class);

   public static class DelayTransfer implements Serializable
   {
      private transient int count;

      private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException
      {
         in.defaultReadObject();
      }

      private void writeObject(ObjectOutputStream out) throws IOException
      {
         out.defaultWriteObject();

         // RPC is first serialization, ST is second
         if (count++ == 0)
            return;

         try
         {
            // This sleep is not required for the test to function,
            // however it improves the possibility of finding errors
            // (since it keeps the tx log going)
            Thread.sleep(2000);
         }
         catch (InterruptedException e)
         {
         }
      }

   }
   private static class WritingRunner implements Runnable
   {
      private final Cache<Object,Object> cache;
      private final boolean tx;
      private volatile boolean stop;
      private volatile int result;

      WritingRunner(Cache<Object, Object> cache, boolean tx)
      {
         this.cache = cache;
         this.tx = tx;         
      }

      public int result()
      {
         return result;
      }

      public void run()
      {
         int c = 0;
         while (!stop)
         {
            try
            {
               if (c == 1000)
               {
                  startTxIfNeeded();
                  for (int i=0; i<1000; i++) cache.removeNode("/test" + i);
                  commitTxIfNeeded();
                  c = 0;
               } 
               else
               {
                  startTxIfNeeded();
                  cache.put("/test" + c, "test", c++);
                  commitTxIfNeeded();
               }
            }
            catch (InterruptedException ie)
            {
               stop = true;
            }
            catch (Exception e)
            {
               e.printStackTrace();
               log.error(e);
               stop = true;
            }
         }
         result = c;
      }

      private void startTxIfNeeded() throws Exception
      {
         if (tx) cache.getConfiguration().getRuntimeConfig().getTransactionManager().begin();
      }

      private void commitTxIfNeeded() throws Exception
      {
         if (tx) cache.getConfiguration().getRuntimeConfig().getTransactionManager().commit();
      }

      public void stop()
      {
         stop = true;
      }
   }

   private CacheSPI<Object, Object> createCache(String name) throws IOException
   {
      return createCache(name, true);

   }

   protected CacheSPI<Object, Object> createCache(String name, boolean start) throws IOException
   {
      Configuration config = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);
      config.setSyncCommitPhase(true);
      config.setClusterName(name + "-" + Thread.currentThread().getName());
      config.setNonBlockingStateTransfer(true);
      config.setSyncReplTimeout(30000);
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(config, false, getClass());

      cache.create();
      if (start)
         cache.start();
      return cache;
   }

   public void testInitialStateTransfer() throws Exception
   {
      testCount++;
      log.info("testInitialStateTransfer start - " + testCount);
      CacheSPI<Object, Object> cache1 = null, cache2 = null;
      try
      {
         cache1 = createCache("nbst");
         writeInitialData(cache1);

         cache2 = createCache("nbst");

         // Pause to give caches time to see each other
         TestingUtil.blockUntilViewsReceived(new CacheSPI[] { cache1, cache2 }, 60000);

         verifyInitialData(cache2);
      }
      finally
      {
         TestingUtil.killCaches(cache1, cache2);
      }
      log.info("testInitialStateTransfer end - " + testCount);
   }

   public void testActivateRegionTransfer() throws Exception
   {
      testCount++;
      log.info("testActivateRegionTransfer start - " + testCount);
      CacheSPI<Object, Object> cache1 = null, cache2 = null;
      try
      {
         cache1 = createCache("nbst", false);
         cache1.getConfiguration().setUseRegionBasedMarshalling(true);
         Region region = cache1.getRegion(Fqn.fromString("/region1"), true);
         region.registerContextClassLoader(getClass().getClassLoader());
         cache1.start();

         writeInitialData(cache1);

         cache2 = createCache("nbst", false);
         cache2.getConfiguration().setUseRegionBasedMarshalling(true);
         region = cache2.getRegion(Fqn.fromString("/region1"), true);
         region.registerContextClassLoader(getClass().getClassLoader());
         cache2.start();
         region.deactivate();

         // Pause to give caches time to see each other
         TestingUtil.blockUntilViewsReceived(new CacheSPI[] { cache1, cache2 }, 60000);

         cache1.put("/region1/blah", "blah", "blah");
         cache1.put("/region1/blah2", "blah", "blah");

         assertEquals(null, cache2.get("/region1/blah", "blah"));
         assertEquals(null, cache2.get("/region1/blah2", "blah"));

         region.activate();

         assertEquals("blah", cache2.get("/region1/blah", "blah"));
         assertEquals("blah", cache2.get("/region1/blah2", "blah"));

         verifyInitialData(cache2);
      }
      finally
      {
         TestingUtil.killCaches(cache1, cache2);
      }
      log.info("testActivateRegionTransfer end - " + testCount);
   }

   public void testConcurrentStateTransfer() throws Exception
   {
      testCount++;
      log.info("testConcurrentStateTransfer start - " + testCount);
      CacheSPI<Object, Object> cache1 = null, cache2 = null, cache3 = null, cache4 = null;
      try
      {
         cache1 = createCache("nbst");
         writeInitialData(cache1);

         cache2 = createCache("nbst");

         cache1.put("/delay", "delay", new DelayTransfer());

         // Pause to give caches time to see each other
         TestingUtil.blockUntilViewsReceived(new CacheSPI[] { cache1, cache2 }, 60000);
         verifyInitialData(cache2);

         final CacheSPI<Object, Object >c3 = cache3 = createCache("nbst", false);
         final CacheSPI<Object, Object >c4 = cache4 = createCache("nbst", false);

         Thread t1 = new Thread(new Runnable()
         {
            public void run()
            {
               c3.start();
            }
         });
         t1.start();

         Thread t2 = new Thread(new Runnable()
         {
            public void run()
            {
               c4.start();
            }
         });
         t2.start();

         t1.join();
         t2.join();

         TestingUtil.blockUntilViewsReceived(new CacheSPI[] { cache1, cache2, cache3, cache4 }, 60000);
         verifyInitialData(cache3);
         verifyInitialData(cache4);
      }
      finally
      {
         TestingUtil.killCaches(cache1, cache2, cache3, cache4);
      }
      log.info("testConcurrentStateTransfer end - " + testCount);
   }

   public void testSTWithThirdWritingNonTxCache() throws Exception
   {
      testCount++;
      log.info("testSTWithThirdWritingNonTxCache start - " + testCount);
      thirdWritingCacheTest(false, "nbst1");
      log.info("testSTWithThirdWritingNonTxCache end - " + testCount);
   }

   public void testSTWithThirdWritingTxCache() throws Exception
   {
      testCount++;
      log.info("testSTWithThirdWritingTxCache start - " + testCount);
      thirdWritingCacheTest(true, "nbst2");
      log.info("testSTWithThirdWritingTxCache end - " + testCount);
   }

   public void testSTWithWritingNonTxThread() throws Exception
   {
      testCount++;
      log.info("testSTWithWritingNonTxThread start - " + testCount);
      writingThreadTest(false, "nbst3");
      log.info("testSTWithWritingNonTxThread end - " + testCount);
   }

   public void testSTWithWritingTxThread() throws Exception
   {
      testCount++;
      log.info("testSTWithWritingTxThread start - " + testCount);
      writingThreadTest(true, "nbst4");
      log.info("testSTWithWritingTxThread end - " + testCount);
   }

   private void thirdWritingCacheTest(boolean tx, String name) throws InterruptedException, IOException
   {
      CacheSPI<Object, Object> cache1 = null, cache2 = null, cache3 = null;
      try
      {
         cache1 = createCache(name);
         cache3 = createCache(name);

         writeInitialData(cache1);

         // Delay the transient copy, so that we get a more thorough log test
         cache1.put("/delay", "delay", new DelayTransfer());

         WritingRunner writer = new WritingRunner(cache3, tx);
         Thread writerThread = new Thread(writer);
         writerThread.setDaemon(true);
         writerThread.start();

         cache2 = createCache(name);

         // Pause to give caches time to see each other
         TestingUtil.blockUntilViewsReceived(new CacheSPI[] { cache1, cache2, cache3 }, 120000); // this could take a while since WritingRunner is creating a lot of stuff

         writer.stop();
         writerThread.interrupt();
         writerThread.join();

         verifyInitialData(cache2);

         int count = writer.result();

         for (int c = 0; c < count; c++)
            assertEquals(c, cache2.get("/test" + c, "test"));
      }
      finally
      {
         TestingUtil.killCaches(cache1, cache2, cache3);
      }
   }

   protected void verifyInitialData(CacheSPI<Object, Object> cache2)
   {
      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache2.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache2.get(A_C, "age"));
   }

   protected void writeInitialData(final CacheSPI<Object, Object> cache1)
   {
      cache1.put(A_B, "name", JOE);
      cache1.put(A_B, "age", TWENTY);
      cache1.put(A_C, "name", BOB);
      cache1.put(A_C, "age", FORTY);
   }

   private void writingThreadTest(boolean tx, String name) throws InterruptedException, IOException
   {
      CacheSPI<Object, Object> cache1 = null, cache2 = null;
      try
      {
         cache1 = createCache(name);

         writeInitialData(cache1);

         // Delay the transient copy, so that we get a more thorough log test
         cache1.put("/delay", "delay", new DelayTransfer());

         WritingRunner writer = new WritingRunner(cache1, tx);
         Thread writerThread = new Thread(writer);
         writerThread.setDaemon(true);
         writerThread.start();

         cache2 = createCache(name);

         // Pause to give caches time to see each other
         TestingUtil.blockUntilViewsReceived(new CacheSPI[] { cache1, cache2 }, 60000);

         writer.stop();
         writerThread.interrupt();
         writerThread.join();

         verifyInitialData(cache2);

         int count = writer.result();

         for (int c = 0; c < count; c++)
            assertEquals(c, cache2.get("/test" + c, "test"));
      }
      finally
      {
         TestingUtil.killCaches(cache1, cache2);         
      }
   }
}
