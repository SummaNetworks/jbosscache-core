package org.jboss.cache.util.concurrent.locks;

import org.jboss.cache.factories.context.MVCCContextFactory;
import org.jboss.cache.invocation.InvocationContextContainer;
import org.jboss.cache.transaction.GlobalTransaction;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Test(groups = {"unit", "mvcc"}, testName = "util.concurrent.locks.OwnableReentrantLockTest")
public class OwnableReentrantLockTest
{
   private InvocationContextContainer getInvocationContextContainer()
   {
      InvocationContextContainer icc = new InvocationContextContainer();
      icc.injectContextFactory(new MVCCContextFactory());
      return icc;
   }

   public void testReentrancyThread()
   {
      InvocationContextContainer icc = getInvocationContextContainer();
      OwnableReentrantLock lock = new OwnableReentrantLock(icc);

      lock.lock(); // locked by current thread
      assert lock.getOwner().equals(Thread.currentThread());
      assert lock.getHoldCount() == 1;

      assert lock.tryLock();
      assert lock.getOwner().equals(Thread.currentThread());
      assert lock.getHoldCount() == 2;

      lock.unlock();
      assert lock.getOwner().equals(Thread.currentThread());
      assert lock.getHoldCount() == 1;

      lock.unlock();
      assert lock.getOwner() == null;
      assert lock.getHoldCount() == 0;
   }

   public void testReentrancyGtx()
   {
      InvocationContextContainer icc = getInvocationContextContainer();
      OwnableReentrantLock lock = new OwnableReentrantLock(icc);

      // create and set a gtx
      GlobalTransaction gtx = new GlobalTransaction();
      gtx.setId(10);
      icc.get().setGlobalTransaction(gtx);

      lock.lock(); // locked by current thread
      assert lock.getOwner().equals(gtx);
      assert lock.getHoldCount() == 1;

      lock.lock();
      assert lock.getOwner().equals(gtx);
      assert lock.getHoldCount() == 2;

      lock.unlock();
      assert lock.getOwner().equals(gtx);
      assert lock.getHoldCount() == 1;

      lock.unlock();
      assert lock.getOwner() == null;
      assert lock.getHoldCount() == 0;
   }

   public void testReentrancyNotSameGtx()
   {
      InvocationContextContainer icc = getInvocationContextContainer();
      OwnableReentrantLock lock = new OwnableReentrantLock(icc);

      // create and set a gtx
      GlobalTransaction gtx = new GlobalTransaction();
      gtx.setId(10);
      icc.get().setGlobalTransaction(gtx);

      GlobalTransaction gtx2 = new GlobalTransaction();
      gtx2.setId(10);

      assert gtx != gtx2;

      lock.lock(); // locked by current thread
      assert lock.getOwner().equals(gtx);
      assert lock.getHoldCount() == 1;

      icc.get().setGlobalTransaction(gtx2);
      lock.lock();
      assert lock.getOwner().equals(gtx2);
      assert lock.getHoldCount() == 2;

      lock.unlock();
      assert lock.getOwner().equals(gtx2);
      assert lock.getHoldCount() == 1;

      icc.get().setGlobalTransaction(gtx);
      lock.unlock();
      assert lock.getOwner() == null;
      assert lock.getHoldCount() == 0;
   }

   public void testThreadLockedByThread() throws InterruptedException
   {
      InvocationContextContainer icc = getInvocationContextContainer();
      final OwnableReentrantLock lock = new OwnableReentrantLock(icc);
      final AtomicBoolean acquired = new AtomicBoolean(false);
      final AtomicBoolean threwExceptionOnRelease = new AtomicBoolean(false);


      lock.lock();
      assert lock.getOwner().equals(Thread.currentThread());
      assert lock.getHoldCount() == 1;

      Thread t = new Thread()
      {
         public void run()
         {
            try
            {
               acquired.set(lock.tryLock(10, TimeUnit.MILLISECONDS));
            }
            catch (InterruptedException e)
            {
               // do nothing
            }

            try
            {
               lock.unlock();
            }
            catch (IllegalMonitorStateException e)
            {
               // expected
               threwExceptionOnRelease.set(true);
            }
         }
      };

      t.start();
      t.join();

      assert !acquired.get() : "Second thread should not have acquired lock";
      assert threwExceptionOnRelease.get() : "Second thread should have thrown an exception trying to release lock";

      lock.unlock();
      assert !lock.isLocked();
   }

   public void testThreadLockedByGtx() throws InterruptedException
   {
      InvocationContextContainer icc = getInvocationContextContainer();
      final OwnableReentrantLock lock = new OwnableReentrantLock(icc);
      GlobalTransaction gtx = new GlobalTransaction();
      gtx.setId(10);
      icc.get().setGlobalTransaction(gtx);
      final AtomicBoolean acquired = new AtomicBoolean(false);
      final AtomicBoolean threwExceptionOnRelease = new AtomicBoolean(false);

      lock.lock();
      assert lock.getOwner().equals(gtx);
      assert lock.getHoldCount() == 1;

      Thread t = new Thread()
      {
         public void run()
         {
            try
            {
               acquired.set(lock.tryLock(10, TimeUnit.MILLISECONDS));
            }
            catch (InterruptedException e)
            {
               // do nothing
            }

            try
            {
               lock.unlock();
            }
            catch (IllegalMonitorStateException e)
            {
               // expected
               threwExceptionOnRelease.set(true);
            }
         }
      };

      t.start();
      t.join();

      assert !acquired.get() : "Second thread should not have acquired lock";
      assert threwExceptionOnRelease.get() : "Second thread should have thrown an exception trying to release lock";

      lock.unlock();
      assert !lock.isLocked();
   }

   public void testGtxLockedByThread() throws InterruptedException
   {
      final InvocationContextContainer icc = getInvocationContextContainer();
      final OwnableReentrantLock lock = new OwnableReentrantLock(icc);
      final AtomicBoolean acquired = new AtomicBoolean(false);
      final AtomicBoolean threwExceptionOnRelease = new AtomicBoolean(false);

      lock.lock();
      assert lock.getOwner().equals(Thread.currentThread());
      assert lock.getHoldCount() == 1;

      Thread t = new Thread()
      {
         public void run()
         {
            try
            {
               GlobalTransaction gtx = new GlobalTransaction();
               gtx.setId(10);
               icc.get().setGlobalTransaction(gtx);
               acquired.set(lock.tryLock(10, TimeUnit.MILLISECONDS));
            }
            catch (InterruptedException e)
            {
               // do nothing
            }

            try
            {
               lock.unlock();
            }
            catch (IllegalMonitorStateException e)
            {
               // expected
               threwExceptionOnRelease.set(true);
            }
         }
      };

      t.start();
      t.join();

      assert !acquired.get() : "Second thread should not have acquired lock";
      assert threwExceptionOnRelease.get() : "Second thread should have thrown an exception trying to release lock";

      lock.unlock();
      assert !lock.isLocked();
   }

   public void testGtxLockedByGtxFail() throws InterruptedException
   {
      final InvocationContextContainer icc = getInvocationContextContainer();
      final OwnableReentrantLock lock = new OwnableReentrantLock(icc);
      final AtomicBoolean acquired = new AtomicBoolean(false);
      final AtomicBoolean threwExceptionOnRelease = new AtomicBoolean(false);
      GlobalTransaction gtx = new GlobalTransaction();
      gtx.setId(10);
      icc.get().setGlobalTransaction(gtx);

      lock.lock();
      assert lock.getOwner().equals(gtx);
      assert lock.getHoldCount() == 1;

      Thread t = new Thread()
      {
         public void run()
         {
            try
            {
               GlobalTransaction gtx = new GlobalTransaction();
               gtx.setId(20);
               icc.get().setGlobalTransaction(gtx);
               acquired.set(lock.tryLock(10, TimeUnit.MILLISECONDS));
            }
            catch (InterruptedException e)
            {
               // do nothing
            }

            try
            {
               lock.unlock();
            }
            catch (IllegalMonitorStateException e)
            {
               // expected
               threwExceptionOnRelease.set(true);
            }
         }
      };

      t.start();
      t.join();

      assert !acquired.get() : "Second thread should not have acquired lock";
      assert threwExceptionOnRelease.get() : "Second thread should have thrown an exception trying to release lock";

      lock.unlock();
      assert !lock.isLocked();
   }

   public void testGtxLockedByGtxSuccess() throws InterruptedException
   {
      final InvocationContextContainer icc = getInvocationContextContainer();
      final OwnableReentrantLock lock = new OwnableReentrantLock(icc);
      final AtomicBoolean acquired = new AtomicBoolean(false);
      GlobalTransaction gtx = new GlobalTransaction();
      gtx.setId(10);
      icc.get().setGlobalTransaction(gtx);

      lock.lock();
      assert lock.getOwner().equals(gtx);
      assert lock.getHoldCount() == 1;

      Thread t = new Thread()
      {
         public void run()
         {
            try
            {
               GlobalTransaction gtx = new GlobalTransaction();
               gtx.setId(10);
               icc.get().setGlobalTransaction(gtx);
               acquired.set(lock.tryLock(10, TimeUnit.MILLISECONDS));
            }
            catch (InterruptedException e)
            {
               // do nothing
            }
         }
      };

      t.start();
      t.join();

      assert acquired.get() : "Second thread should have acquired lock";
      assert lock.getHoldCount() == 2;
      lock.unlock();
      lock.unlock();
      assert !lock.isLocked();
   }

   public void satisfyCodeCoverage() throws InterruptedException
   {
      final InvocationContextContainer icc = getInvocationContextContainer();
      final OwnableReentrantLock lock = new OwnableReentrantLock(icc);
      lock.lockInterruptibly();

      try
      {
         lock.newCondition();
         assert false : "Should throw exception";
      }
      catch (UnsupportedOperationException uoe)
      {
         // good
      }
      assert lock.isHeldExclusively();
      lock.unlock();
      assert !lock.isHeldExclusively();
   }

   public void testSerialization() throws IOException, ClassNotFoundException
   {
      final InvocationContextContainer icc = getInvocationContextContainer();
      final OwnableReentrantLock lock = new OwnableReentrantLock(icc);
      lock.lock();
      assert lock.isLocked();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(lock);
      oos.close();
      baos.close();
      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
      OwnableReentrantLock l2 = (OwnableReentrantLock) ois.readObject();

      assert !l2.isLocked();
      assert l2.getOwner() == null;
   }
}
