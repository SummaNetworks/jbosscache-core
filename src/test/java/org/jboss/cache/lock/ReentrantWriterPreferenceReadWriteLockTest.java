package org.jboss.cache.lock;

import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Tests ReentrantWriterPreferenceReadWriteLock
 *
 * @author Bela Ban
 * @version $Id: ReentrantWriterPreferenceReadWriteLockTest.java 7295 2008-12-12 08:41:33Z mircea.markus $
 */
@Test(groups = {"functional"}, sequential = true, testName = "lock.ReentrantWriterPreferenceReadWriteLockTest")
public class ReentrantWriterPreferenceReadWriteLockTest
{
   ReentrantReadWriteLock lock;
   Lock rl, wl;
   Exception thread_ex = null;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      // lock=new ReentrantWriterPreferenceReadWriteLock();
      lock = new ReentrantReadWriteLock();
      rl = lock.readLock();
      wl = lock.writeLock();
      thread_ex = null;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      lock = null;
      if (thread_ex != null)
         throw thread_ex;
   }

   public void testMultipleReadLockAcquisitions() throws InterruptedException
   {
      rl.lock();
      rl.lock();
   }

   public void testInterruptedLockAcquisition()
   {
      Thread.currentThread().interrupt();
      try
      {
         rl.lockInterruptibly();
         fail("thread should be in interrupted status");
      }
      catch (InterruptedException e)
      {
      }
      finally
      {
         try
         {
            rl.unlock();
            fail("unlock() should throw an IllegalStateException");
         }
         catch (IllegalMonitorStateException illegalStateEx)
         {
            assertTrue(true);
         }
      }
   }

   public void testMultipleWriteLockAcquisitions() throws InterruptedException
   {
      wl.lock();
      wl.lock();
   }

   public void testMultipleReadLockReleases() throws InterruptedException
   {
      rl.lock();
      rl.unlock();
      try
      {
         rl.unlock();
         fail("we should not get here, cannot lock RL once but unlock twice");
      }
      catch (IllegalMonitorStateException illegalState)
      {
         // this is as expected
      }
   }

   public void testMultipleWriteLockReleases() throws InterruptedException
   {
      wl.lock();
      wl.unlock();
      try
      {
         wl.unlock();
         fail("expected");
      }
      catch (IllegalMonitorStateException e)
      {
      }
   }

   public void testAcquireWriteLockAfterReadLock() throws InterruptedException
   {
      rl.lock();
      rl.unlock();
      wl.lock();
   }


   public void testAcquiringReadLockedLockWithRead() throws InterruptedException
   {
      new Thread()
      {
         public void run()
         {
            try
            {
               rl.lockInterruptibly();
            }
            catch (InterruptedException e)
            {
            }
         }
      }.start();

      TestingUtil.sleepThread(500);

      // now we have a RL by another thread

      boolean flag = rl.tryLock(3000, TimeUnit.MILLISECONDS);
      assertTrue(flag);
      flag = wl.tryLock(3000, TimeUnit.MILLISECONDS);
      assertFalse(flag);
   }

   public void testAcquiringReadLockedLock() throws InterruptedException
   {
      new Thread()
      {
         public void run()
         {
            try
            {
               rl.lockInterruptibly();
            }
            catch (InterruptedException e)
            {
            }
         }
      }.start();

      TestingUtil.sleepThread(500);

      // now we have a RL by another thread
      boolean flag = wl.tryLock(3000, TimeUnit.MILLISECONDS);
      assertFalse(flag);
   }

   public void testWriteThenReadByDifferentTx() throws InterruptedException
   {
      Writer writer = new Writer("Writer");
      Reader reader = new Reader("Reader");
      writer.start();
      TestingUtil.sleepThread(500);
      reader.start();
      TestingUtil.sleepThread(1000);

      synchronized (writer)
      {
         writer.notify();
      }
      TestingUtil.sleepThread(500);
      synchronized (reader)
      {
         reader.notify();
      }
      writer.join();
      reader.join();
   }

   public void testReadThenWriteByDifferentTx() throws InterruptedException
   {
      Writer writer = new Writer("Writer");
      Reader reader = new Reader("Reader");

      reader.start();
      TestingUtil.sleepThread(500);
      writer.start();
      TestingUtil.sleepThread(1000);

      synchronized (reader)
      {
         reader.notify();
      }

      TestingUtil.sleepThread(500);
      synchronized (writer)
      {
         writer.notify();
      }
      writer.join();
      reader.join();
   }


   class Reader extends Thread
   {

      public Reader(String name)
      {
         super(name);
      }

      public void run()
      {
         try
         {
            rl.lock();
            synchronized (this)
            {
               this.wait();
            }
            rl.unlock();
         }
         catch (InterruptedException e)
         {
         }
      }
   }


   class Writer extends Thread
   {

      public Writer(String name)
      {
         super(name);
      }

      public void run()
      {
         try
         {
            wl.lock();
            synchronized (this)
            {
               this.wait();
            }
            wl.unlock();
         }
         catch (InterruptedException e)
         {
         }
      }
   }


   class Upgrader extends Thread
   {
      boolean upgradeSuccessful = false;

      public Upgrader(String name)
      {
         super(name);
      }

      public boolean wasUpgradeSuccessful()
      {
         return upgradeSuccessful;
      }


      public void run()
      {
         try
         {
            rl.lock();
            synchronized (this)
            {
               this.wait();
            }
            // rl.unlock();
            wl.lock();
            upgradeSuccessful = true;
            wl.unlock();
         }
         catch (InterruptedException e)
         {
         }
      }
   }

}
