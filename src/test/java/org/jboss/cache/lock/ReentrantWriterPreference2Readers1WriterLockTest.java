/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.lock;

import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Tests ReentrantWriterPreferenceReadWriteLock
 *
 * @author Bela Ban
 * @version $Id: ReentrantWriterPreference2Readers1WriterLockTest.java 7295 2008-12-12 08:41:33Z mircea.markus $
 */
@Test(groups = {"functional"}, enabled = false, testName = "lock.ReentrantWriterPreference2Readers1WriterLockTest")
// historical disabled - and wont fix.  See JBCACHE-461
public class ReentrantWriterPreference2Readers1WriterLockTest
{
   ReentrantReadWriteLock lock;
   ReentrantReadWriteLock.ReadLock rl;
   ReentrantReadWriteLock.WriteLock wl;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      lock = new ReentrantReadWriteLock();
      rl = lock.readLock();
      wl = lock.writeLock();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      lock = null;
   }


   public void testSimpleUpgradeFromReadLockToWriteLock()
   {
      int readers, writers;
      try
      {
         rl.lock();
         readers = lock.getReadLockCount();
         assertEquals(1, readers);
         boolean wl_acquired = wl.tryLock(500, TimeUnit.MILLISECONDS);
         if (!wl_acquired)
         {
            fail("write lock could not be acquired");
            return;
         }
         readers = lock.getReadLockCount();
         assertEquals(1, readers);
         writers = lock.getWriteHoldCount();
         assertEquals(1, writers);
      }
      catch (InterruptedException e)
      {

      }
      finally
      {
         rl.unlock();
         if (lock.getWriteHoldCount() > 0)
            wl.unlock();
      }
   }

   public void test2ReadersAnd1Writer() throws InterruptedException
   {
      int readers, writers;
      Upgrader upgrader = new Upgrader("Upgrader");
      Reader reader = new Reader("Reader");
      reader.start();
      sleepThread(500);

      readers = lock.getReadLockCount();
      assertEquals(1, readers);

      upgrader.start();
      sleepThread(500);

      readers = lock.getReadLockCount();
      assertEquals(2, readers);

      synchronized (upgrader)
      { // writer upgrades from RL to WL, this should fail
         upgrader.notify();
      }
      sleepThread(500);

      readers = lock.getReadLockCount();
      assertEquals(2, readers);
      writers = lock.getWriteHoldCount();
      assertEquals(0, writers);

      synchronized (reader)
      { // reader unlocks its RL, now writer should be able to upgrade to a WL
         reader.notify();
      }
      reader.join();

      readers = lock.getReadLockCount();
      assertEquals(1, readers);
      writers = lock.getWriteHoldCount();
      assertEquals(1, writers);

      synchronized (upgrader)
      { // writer releases WL
         upgrader.notify();
      }
      sleepThread(500);
      readers = lock.getReadLockCount();
      assertEquals(0, readers);
      writers = lock.getWriteHoldCount();
      assertEquals(0, writers);

      upgrader.join(3000);
      assertTrue("Known failure. See JBCACHE-461; This is due to a potential bug in ReentrantWriterPreferenceReadWriteLock !",
            upgrader.wasUpgradeSuccessful());
   }


   private class Reader extends Thread
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
         }
         catch (InterruptedException e)
         {
         }
         finally
         {
            rl.unlock();
         }
      }
   }


   private class Upgrader extends Thread
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
            wl.lock();
            upgradeSuccessful = true;


            synchronized (this)
            {
               this.wait();
            }
            rl.unlock();
         }
         catch (InterruptedException e)
         {
         }
         finally
         {
            wl.unlock();
            rl.unlock();
         }
      }
   }


   static void sleepThread(long timeout)
   {
      try
      {
         Thread.sleep(timeout);
      }
      catch (InterruptedException e)
      {
      }
   }

}
