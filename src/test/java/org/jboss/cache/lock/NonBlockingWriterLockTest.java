package org.jboss.cache.lock;

import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Vector;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * NonBlockingWriterLock is a read/write lock (with upgrade) that has
 * non-blocking write lock acquisition on existing read lock(s).
 * <p>Note that the write lock is exclusive among write locks, e.g.,
 * only one write lock can be granted at one time, but the write lock
 * is independent of the read locks. For example,
 * a read lock to be acquired will be blocked if there is existing write lock, but
 * will not be blocked if there are mutiple read locks already granted to other
 * owners. On the other hand, a write lock can be acquired as long as there
 * is no existing write lock, regardless how many read locks have been
 * granted.
 *
 * @author <a href="mailto:cavin_song@yahoo.com">Cavin Song</a> April 22, 2004
 * @version 1.0
 */
@Test(groups = {"functional"}, enabled = false, testName = "lock.NonBlockingWriterLockTest")
// TODO: 2.2.0: This is not a very good test.  There is a lot of timing related stuff with regards to the order of execution of reader and writer threads that is not taken into account, producing variable results.  Needs to be rewritten.
public class NonBlockingWriterLockTest
{
   static final NonBlockingWriterLock lock_ = new NonBlockingWriterLock();
   static long SLEEP_MSECS = 500;
   Vector<Object> lockResult = new Vector<Object>();
   int NO_MORE_OP = 0;
   int INVOKE_READ = 1;
   int INVOKE_WRITE = 2;
   int INVOKE_UPGRADE = 3;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      logX("\n");
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
   }

   // For debugging purpose
   private static void logX(String str)
   {
//       try {
//          logToFile(str);
//       } catch (IOException ioe) {
//       }
   }

//   // Debugging intrnal function
//   private static void logToFile(String str) throws IOException
//   {
//      Writer out = new FileWriter("./ReadCommittedLog.txt", true/*append*/);
//      out.write(str);
//      out.close();
//   }

   /***************************************************************/
   /*     Utility functions to creat threads for RL, WL and UL    */
   /***************************************************************/
   /**
    * Creates a new thread and acquires a read lock with a timeout
    * value specified by the caller. Optionally, the caller can request
    * a second read or write lock after the first read lock request.
    * The locking result is stored in a vector with the following
    * format:
    * <p><DL>
    * <DD>'case number'-'thread name'-[RL|WL|UL]-[0|1]
    * </DL>
    * <p>where:
    * <DL>
    * <DD> 'case number' is the passed in test case # by the caller.
    * <DD> 'thread name' is the passed in thread name by the caller.
    * <DD> RL - indicating was doing read lock request.
    * <DD> WL - indicating was doing write lock request.
    * <DD> UL - indicating was doing upgrade lock request.
    * <DD> 0  - indicating the locking request failed.
    * <DD> 1  - indicating the locking request succeeded.
    * </DL>
    * <p/>
    * After all threads in each test case terminate, the test case
    * should make the following call to verify the test result:
    * <DL>
    * <DD> asssertTrue(checkLockingResult(expected-result-string);
    * </DL>
    * <p/>
    * 'expected-result-string' is the locking result string
    * described above. For example, "8-t1-RL-0" means that thread
    * t1 in test case #8 doing a Read Lock request expects the
    * operation to fail. If the expected result string can't be
    * found then the test case is considered FAILED (ie, either
    * the read lock request was successful or did not complete).
    * <p/>
    * Each test case should also call cleanLockingResult() to reset
    * result vector for the next test cases.
    *
    * @param caseNum  Arbitrary string for the test case number.
    * @param name     Arbitrary string for the calling thread name.
    * @param msecs    Milliseconds that the thread should sleep after
    *                 acquiring the read lock.
    * @param errMsg   Error msg to log in case of error.
    * @param secondOP Set to NO_MORE_OP if a 2nd lock request is not required.
    *                 Set to INVOKE_READ, INVOKE_READ or INVOKE_UPGRADE
    *                 respectively if the 2nd lock is a read, write or
    *                 upgrade request respectively.
    */

   protected Thread readThread(final String caseNum, final String name,
                               final long msecs, final long sleepSecs,
                               final String errMsg, final int secondOP)
   {
      return new Thread(name)
      {
         public void run()
         {
            Lock rlock = lock_.readLock();
            try
            {
               if (!rlock.tryLock(msecs, TimeUnit.MILLISECONDS))
               {
                  logX(caseNum + "-" + name + " requesting read lock failed!\n");
                  String str = caseNum + "-" + name + "-RL-0";
                  postLockingResult(str);
                  return;
               }
               // OK, read lock obtained, sleep and release it.
               logX(caseNum + "-" + name + " requesting read lock succeeded!\n");
               String str = caseNum + "-" + name + "-RL-1";
               postLockingResult(str);
               TestingUtil.sleepThread(sleepSecs);

               if (secondOP == INVOKE_READ)
                  acquireReadLock(caseNum, name, msecs, errMsg);
               else if (secondOP == INVOKE_WRITE)
                  acquireWriteLock(caseNum, name, msecs, errMsg);
               else if (secondOP == INVOKE_UPGRADE)
                  acquireUpgradeLock(caseNum, name, msecs, errMsg);

               rlock.unlock();
               logX(caseNum + "-" + name + " releasing read lock.\n");
            }
            catch (Exception ex)
            {
            }
         }
      };
   }

   /**
    * Creates a new thread and acquires a write lock with a timeout
    * value specified by the caller. Similar to {@link #readThread readThread()}
    * except it's used for write locks.
    *
    * @see #readThread readThread()
    */
   protected Thread writeThread(final String caseNum, final String name,
                                final long msecs, final long sleepSecs,
                                final String errMsg, final int secondOP)
   {
      return new Thread(name)
      {
         public void run()
         {
            try
            {
               Lock wlock = lock_.writeLock();
               if (!wlock.tryLock(msecs, TimeUnit.MILLISECONDS))
               {
                  logX(caseNum + "-" + name + " requesting write lock failed!\n");
                  String str = caseNum + "-" + name + "-WL-0";
                  postLockingResult(str);
                  return;
               }
               // OK, write lock obtained, sleep and release it.
               logX(caseNum + "-" + name + " requesting write lock succeeded!\n");
               String str = caseNum + "-" + name + "-WL-1";
               postLockingResult(str);
               TestingUtil.sleepThread(sleepSecs);

               if (secondOP == INVOKE_READ)
                  acquireReadLock(caseNum, name, msecs, errMsg);
               else if (secondOP == INVOKE_WRITE)
                  acquireWriteLock(caseNum, name, msecs, errMsg);
               else if (secondOP == INVOKE_UPGRADE)
                  acquireUpgradeLock(caseNum, name, msecs, errMsg);

               wlock.unlock();
               logX(caseNum + "-" + name + " releasing write lock.\n");
            }
            catch (Exception ex)
            {
            }
         }
      };
   }

   /**
    * Creates a new thread, acquires a read lock, sleeps for a while
    * and then tries to upgrade the read lock to a write one. Similar
    * to {@link #readThread readThread()} except it's used for upgrading
    * locks.
    *
    * @see #readThread readThread()
    */
   protected Thread upgradeThread(final String caseNum, final String name,
                                  final long msecs, final String errMsg)
   {
      return new Thread(name)
      {
         public void run()
         {
            try
            {
               Lock rlock = lock_.readLock();
               Lock wlock = null;
               if (!rlock.tryLock(msecs, TimeUnit.MILLISECONDS))
               {
                  logX(caseNum + "-" + name + " requesting read lock failed!\n");
                  String str = caseNum + "-" + name + "-RL-0";
                  postLockingResult(str);
                  return;
               }
               // OK, read lock obtained, sleep and upgrade it later.
               logX(caseNum + "-" + name + " requesting read lock succeeded (upgrade later)!\n");
               TestingUtil.sleepThread(SLEEP_MSECS / 2);
               String str = caseNum + "-" + name + "-UL-";
               if ((wlock = lock_.upgradeLockAttempt(msecs)) == null)
               {
                  logX(caseNum + "-" + name + " requesting upgrade lock failed!\n");
                  str += "0";
               }
               else
               {
                  logX(caseNum + "-" + name + " requesting upgrade lock succeeded!\n");
                  str += "1";
               }
               postLockingResult(str);
               // Sleep again and then release the lock.
               TestingUtil.sleepThread(SLEEP_MSECS);
               if (wlock != null)
               {
                  wlock.unlock();
                  logX(caseNum + "-" + name + " releasing upgrade lock.\n");
               }
               rlock.unlock();
            }
            catch (Exception ex)
            {
            }
         }
      };
   }

   /***************************************************************/
   /*     Utility functions to acquire RL and WL (no thread)      */
   /***************************************************************/
   /**
    * This routine tries to acquire a read lock with a timeout value
    * passed in by the caller. Like {@link #readThread readThread()}
    * it then stores the locking result in the result vector depending
    * on the outcome of the request.
    */

   protected void acquireReadLock(final String caseNum, final String name,
                                  final long msecs, final String errMsg)
   {
      try
      {
         Lock rlock = lock_.readLock();
         if (!rlock.tryLock(msecs, TimeUnit.MILLISECONDS))
         {
            logX(caseNum + "-" + name + " requesting read lock failed!\n");
            String str = caseNum + "-" + name + "-RL-0";
            postLockingResult(str);
            return;
         }
         // OK, read lock obtained, sleep and release it.
         logX(caseNum + "-" + name + " requesting read lock succeeded!\n");
         String str = caseNum + "-" + name + "-RL-1";
         postLockingResult(str);
         TestingUtil.sleepThread(SLEEP_MSECS);
         rlock.unlock();
         logX(caseNum + "-" + name + " releasing read lock.\n");
      }
      catch (Exception ex)
      {
      }
   }

   /**
    * Same as {@link #acquireReadLock acquireReadLock()} except
    * it's for write lock request.
    */
   protected void acquireWriteLock(final String caseNum, final String name,
                                   final long msecs, final String errMsg)
   {
      try
      {
         Lock wlock = lock_.writeLock();
         if (!wlock.tryLock(msecs, TimeUnit.MILLISECONDS))
         {
            logX(caseNum + "-" + name + " requesting write lock failed!\n");
            String str = caseNum + "-" + name + "-WL-0";
            postLockingResult(str);
            return;
         }
         // OK, write lock obtained, sleep and release it.
         logX(caseNum + "-" + name + " requesting write lock succeeded!\n");
         String str = caseNum + "-" + name + "-WL-1";
         postLockingResult(str);
         TestingUtil.sleepThread(SLEEP_MSECS);
         wlock.unlock();
         logX(caseNum + "-" + name + " releasing write lock.\n");
      }
      catch (Exception ex)
      {
      }
   }

   /**
    * Same as {@link #acquireReadLock acquireReadLock()} except
    * it's for upgrade lock request.
    */
   protected void acquireUpgradeLock(final String caseNum, final String name,
                                     final long msecs, final String errMsg)
   {
      try
      {
         Lock ulock = null;
         if ((ulock = lock_.upgradeLockAttempt(msecs)) == null)
         {
            logX(caseNum + "-" + name + " requesting upgrade lock failed!\n");
            String str = caseNum + "-" + name + "-UL-0";
            postLockingResult(str);
            return;
         }
         // OK, write lock obtained, sleep and release it.
         logX(caseNum + "-" + name + " requesting upgrade lock succeeded!\n");
         String str = caseNum + "-" + name + "-UL-1";
         postLockingResult(str);
         TestingUtil.sleepThread(SLEEP_MSECS);
         ulock.unlock();
         logX(caseNum + "-" + name + " releasing upgrade lock.\n");
      }
      catch (Exception ex)
      {
      }
   }

   /***************************************************************/
   /*     Synchronized methods handling locking result vector     */
   /***************************************************************/
   /**
    * Clean/remove all locking results in the vector.
    */
   protected synchronized void cleanLockingResult()
   {
      lockResult.removeAllElements();
   }

   /**
    * Post a locking result to the vector for later verification.
    */
   protected synchronized void postLockingResult(Object obj)
   {
      logX("  Added *" + obj + "* to the result vector\n");
      // Make sure we only have one in the vector
      //if (!checkLockingResult((String)obj))
      lockResult.addElement(obj);
   }

   /**
    * Check if a given expected locking result is in the vector.
    */
   protected synchronized boolean checkLockingResult(String expected)
   {
      boolean rc = false;
      for (int i = 0; i < lockResult.size(); i++)
      {
         Object ele = lockResult.elementAt(i);
         String str = (String) ele;
         if (expected.equals(str))
         {
            rc = true;
            break;
         }
      }
      if (rc)
         logX("  Searching for *" + expected + "* SUCCEEDED.\n");
      else
         logX("  Searching for *" + expected + "* FAILED.\n");
      return rc;
   }

   /***************************************************************/
   /*                   T e s t  C a s e s                        */
   /***************************************************************/
   /**
    * Case #10 - T1 acquires RL, T2 acquires RL followed by WL.
    */
   public void testWriteWithMultipleReaders() throws Exception
   {
      String caseNum = "10";
      Thread t1 = readThread(caseNum, "t1", 0, SLEEP_MSECS * 2,
            "1st read lock attempt failed", NO_MORE_OP);
      Thread t2 = readThread(caseNum, "t2", 0, SLEEP_MSECS,
            "2nd read lock attempt failed", INVOKE_WRITE);

      t1.start();
      t2.start();
      t1.join(3000);
      t2.join(3000);
      assertTrue(checkLockingResult(caseNum + "-t1-RL-1") &&
            checkLockingResult(caseNum + "-t2-RL-1") &&
            checkLockingResult(caseNum + "-t2-WL-1"));
      cleanLockingResult();
      // possilbe deadlock check
      if (t1.isAlive() || t2.isAlive())
         fail("Possible deadlock resulted in testRead.");
   }

   /**
    * Case #11 - T1 acquires RL followed by WL, T2 acquires RL.
    */
   public void testUpgradeWithMultipleReadersOn1() throws Exception
   {
      String caseNum = "11";
      Thread t1 = readThread(caseNum, "t1", 0, SLEEP_MSECS,
            "1st read lock attempt failed", INVOKE_WRITE);
      Thread t2 = readThread(caseNum, "t2", 0, SLEEP_MSECS * 2,
            "2nd read lock attempt failed", NO_MORE_OP);

      t1.start();
      t2.start();
      t1.join(3000);
      t2.join(3000);
      assertTrue(checkLockingResult(caseNum + "-t1-RL-1") &&
            checkLockingResult(caseNum + "-t2-RL-1") &&
            checkLockingResult(caseNum + "-t1-WL-1"));
      cleanLockingResult();
      // possilbe deadlock check
      if (t1.isAlive() || t2.isAlive())
         fail("Possible deadlock resulted in testRead.");
   }

   /**
    * Case #2 - T1 acquires RL followed by UL.
    */
   public void testUpgradeReadLock() throws Exception
   {
      String caseNum = "2";
      Thread t1 = readThread(caseNum, "t1", 0, SLEEP_MSECS,
            "1st read lock attempt failed", INVOKE_UPGRADE);

      t1.start();
      t1.join(3000);
      assertTrue(checkLockingResult(caseNum + "-t1-RL-1") &&
            checkLockingResult(caseNum + "-t1-UL-1"));
      cleanLockingResult();
   }

   /**
    * Case #3 - T1 acquires RL followed by WL.
    */

   public void testReadThenWrite() throws Exception
   {
      String caseNum = "3";
      acquireReadLock(caseNum, "t1", 0, "1st read lock attempt failed");
      acquireWriteLock(caseNum, "t1.1", 0, "2nd write lock attempt failed");
      assertTrue(checkLockingResult(caseNum + "-t1-RL-1") &&
            checkLockingResult(caseNum + "-t1.1-WL-1"));
      cleanLockingResult();
   }


   /**
    * Case #5 - T1 acquires WL followed by RL.
    */

   public void testWriteThenRead() throws Exception
   {
      String caseNum = "5";
      acquireWriteLock(caseNum, "t1", 0, "1st write lock attempt failed");
      acquireReadLock(caseNum, "t1.1", 0, "2nd read lock attempt failed");
      assertTrue(checkLockingResult(caseNum + "-t1-WL-1") &&
            checkLockingResult(caseNum + "-t1.1-RL-1"));
      cleanLockingResult();
   }


   /**
    * Case #6 - T1 acquires RL, T2 acquires RL.
    */
   public void testMultipleReadlock() throws Exception
   {
      String caseNum = "6";
      Thread t1 = readThread(caseNum, "t1", 0, SLEEP_MSECS,
            "1st read lock attempt failed", NO_MORE_OP);
      Thread t2 = readThread(caseNum, "t2", 0, SLEEP_MSECS,
            "2nd read lock attempt failed", NO_MORE_OP);

      t1.start();
      t2.start();
      t1.join(3000);
      t2.join(3000);
      assertTrue(checkLockingResult(caseNum + "-t1-RL-1") &&
            checkLockingResult(caseNum + "-t2-RL-1"));
      cleanLockingResult();
      // possilbe deadlock check
      if (t1.isAlive() || t2.isAlive())
         fail("Possible deadlock resulted in testRead.");
   }

   /**
    * Case #8 - T1 acquires RL, T2 acquires WL.
    */
   public void testWriteWithExistingReader() throws Exception
   {
      String caseNum = "8";
      Thread t1 = readThread(caseNum, "t1", 0, SLEEP_MSECS,
            "1st write lock attempt failed", NO_MORE_OP);
      Thread t2 = writeThread(caseNum, "t2", 0, SLEEP_MSECS,
            "2nd read lock attempt failed", NO_MORE_OP);

      t1.start();
      t2.start();
      t1.join(3000);
      t2.join(3000);
      assertTrue(checkLockingResult(caseNum + "-t1-RL-1") &&
            checkLockingResult(caseNum + "-t2-WL-1"));
      cleanLockingResult();
      // possilbe deadlock check
      if (t1.isAlive() || t2.isAlive())
         fail("Possible deadlock resulted in testRead.");
   }

   /**
    * Case #13 - T1 acquires RL, T2 acquires WL.
    */
   public void testReadWithExistingWriter() throws Exception
   {
      String caseNum = "13";
      Thread t1 = writeThread(caseNum, "t1", 0, SLEEP_MSECS,
            "1st write lock attempt failed", NO_MORE_OP);
      Thread t2 = readThread(caseNum, "t2", 0, SLEEP_MSECS,
            "2nd read lock attempt failed", NO_MORE_OP);

      t1.start();
      t2.start();
      t1.join(3000);
      t2.join(3000);
      assert checkLockingResult(caseNum + "-t1-WL-1");
      assert checkLockingResult(caseNum + "-t2-RL-0");
//      assertTrue(checkLockingResult(caseNum + "-t1-WL-1") &&
//                 checkLockingResult(caseNum + "-t2-RL-0"));
      cleanLockingResult();
      // possilbe deadlock check
      if (t1.isAlive() || t2.isAlive())
         fail("Possible deadlock resulted in testRead.");
   }

   /**
    * Case #14 - T1 acquires WL, T2 acquires WL.
    */
   public void testMultipleWritelocks() throws Exception
   {
      String caseNum = "14";
      Thread t1 = writeThread(caseNum, "t1", 0, SLEEP_MSECS,
            "1st write lock attempt failed", NO_MORE_OP);
      Thread t2 = writeThread(caseNum, "t2", 0, SLEEP_MSECS,
            "2nd write lock attempt failed", NO_MORE_OP);

      t1.start();
      t2.start();
      t1.join(3000);
      t2.join(3000);
//      assertTrue(checkLockingResult(caseNum + "-t1-WL-1") &&
//                 checkLockingResult(caseNum + "-t2-WL-0"));
      assert checkLockingResult(caseNum + "-t1-WL-1");
      assert checkLockingResult(caseNum + "-t2-WL-0");

      cleanLockingResult();
      // possilbe deadlock check
      if (t1.isAlive() || t2.isAlive())
         fail("Possible deadlock resulted in testRead.");
   }

   /**
    * Case #7 - T1 acquires RL, T2 acquires UL.
    */
   public void testUpgradeWithExistingReader() throws Exception
   {
      String caseNum = "7";
      Thread t1 = readThread(caseNum, "t1", 0, SLEEP_MSECS,
            "1st read lock attempt failed", NO_MORE_OP);
      Thread t2 = upgradeThread(caseNum, "t2", 0,
            "2nd upgrade lock attempt failed");

      t1.start();
      t2.start();
      t1.join(3000);
      t2.join(3000);
      assertTrue(checkLockingResult(caseNum + "-t1-RL-1") &&
            checkLockingResult(caseNum + "-t2-UL-1"));
      cleanLockingResult();
      // possilbe deadlock check
      if (t1.isAlive() || t2.isAlive())
         fail("Possible deadlock resulted in testRead.");
   }

   /**
    * Case #9 - T1 acquires RL, T2 acquires RL followed by UL.
    */
   public void testUpgradeWithMultipleReaders() throws Exception
   {
      String caseNum = "9";
      Thread t1 = readThread(caseNum, "t1", 0, SLEEP_MSECS * 2,
            "1st read lock attempt failed", NO_MORE_OP);
      Thread t2 = readThread(caseNum, "t2", 0, SLEEP_MSECS,
            "2nd read lock attempt failed", INVOKE_UPGRADE);

      t1.start();
      t2.start();
      t1.join(3000);
      t2.join(3000);
      assertTrue(checkLockingResult(caseNum + "-t1-RL-1") &&
            checkLockingResult(caseNum + "-t2-RL-1") &&
            checkLockingResult(caseNum + "-t2-UL-1"));
      cleanLockingResult();
      // possilbe deadlock check
      if (t1.isAlive() || t2.isAlive())
         fail("Possible deadlock resulted in testRead.");
   }
}

