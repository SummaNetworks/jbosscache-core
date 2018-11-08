/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jboss.cache.util;

import java.io.FileOutputStream;
import java.io.PrintStream;
import org.jboss.cache.UnitTestCacheFactory;
import org.testng.IClass;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author dpospisi
 */
public class UnitTestTestNGListener implements ITestListener {
      
   /**
    * Holds test classes actually running in all threads.
    */
   private ThreadLocal<IClass> threadTestClass = new ThreadLocal<IClass>();
   Log log = LogFactory.getLog(UnitTestTestNGListener.class);
   
   private int failed = 0;
   private int succeded = 0;
   private int skipped = 0;
   
   public void onTestStart(ITestResult res) {
      log.info("Starting test " + getTestDesc(res));
      threadTestClass.set(res.getTestClass());
   }

   synchronized public void onTestSuccess(ITestResult arg0) {
      System.out.println(getThreadId() + " Test " + getTestDesc(arg0) + " succeded.");
      log.info("Test succeded " + getTestDesc(arg0) + ".");
      succeded++;
      printStatus();
   }

   synchronized public void onTestFailure(ITestResult arg0) {
      System.out.println(getThreadId() + " Test " + getTestDesc(arg0) + " failed.");
      if (arg0.getThrowable() != null) log.error("Test failed " + getTestDesc(arg0), arg0.getThrowable());
      failed++;
      printStatus();      
   }

   synchronized public void onTestSkipped(ITestResult arg0) {
      System.out.println(getThreadId() + " Test " + getTestDesc(arg0) + " skipped.");
      log.info(" Test " + getTestDesc(arg0) + " skipped.");
      if (arg0.getThrowable() != null) log.error("Test skipped : " + arg0.getThrowable(), arg0.getThrowable());
      skipped++;
      printStatus();
   }

   public void onTestFailedButWithinSuccessPercentage(ITestResult arg0) {
   }

   public void onStart(ITestContext arg0) {
   }

   public void onFinish(ITestContext arg0) {
   }
   
   private String getThreadId() {
       return  "["+ Thread.currentThread().getName() + "]";
   }
   
   private String getTestDesc(ITestResult res) {
      return res.getMethod().getMethodName() + "(" + res.getTestClass().getName() + ")";
   }
   
   private void printStatus() {
      System.out.println("Testsuite execution progress: tests succeded " + succeded + ", failed " + failed + ", skipped " + skipped + ".");
   }

}
