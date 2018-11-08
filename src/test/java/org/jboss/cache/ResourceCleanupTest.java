package org.jboss.cache;

import org.testng.annotations.BeforeSuite;
import org.testng.annotations.AfterSuite;
import org.jboss.cache.util.TestingUtil;

import java.io.File;

/**
 * Make sure that all files are being deleted after each test run.
 *
 * @author Mircea.Markus@jboss.com
 */
public class ResourceCleanupTest
{
   @BeforeSuite
   public void removeTempDir()
   {
      TestingUtil.recursiveFileRemove(TestingUtil.TEST_FILES);
      System.out.println("Removing all the files from " + TestingUtil.TEST_FILES);
      File file = new File(TestingUtil.TEST_FILES);
      if (file.exists())
      {
         System.err.println("!!!!!!!!!!!!! Directory '" + TestingUtil.TEST_FILES + "' should have been deleted!!!");
      }
      else
      {
         System.out.println("Successfully removed folder: '" + TestingUtil.TEST_FILES + "'");
      }
   }

   @BeforeSuite
   @AfterSuite
   public void printEnvInformation()
   {
      System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~ ENVIRONMENT INFO ~~~~~~~~~~~~~~~~~~~~~~~~~~");
      String bindAddress = System.getProperty("bind.address");
      System.out.println("bind.address = " + bindAddress);
      //todo for some funny reasons MVN ignores bind.address passed in. This is a hack..
      if (bindAddress == null)
      {
         System.out.println("Setting bind.address to 127.0.0.1 as it is missing!!!");
         System.setProperty("bind.address","127.0.0.1");
      }
      System.out.println("java.runtime.version = " + System.getProperty("java.runtime.version"));
      System.out.println("java.runtime.name =" + System.getProperty("java.runtime.name"));
      System.out.println("java.vm.version = " + System.getProperty("java.vm.version"));
      System.out.println("java.vm.vendor = " + System.getProperty("java.vm.vendor"));
      System.out.println("os.name = " + System.getProperty("os.name"));
      System.out.println("os.version = " + System.getProperty("os.version"));
      System.out.println("sun.arch.data.model = " + System.getProperty("sun.arch.data.model"));
      System.out.println("sun.cpu.endian = " + System.getProperty("sun.cpu.endian"));
      System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~ ENVIRONMENT INFO ~~~~~~~~~~~~~~~~~~~~~~~~~~");
   }

   public static void main(String[] args)
   {
      System.out.println("System.getProperties() = " + System.getProperties());
   }
}
