package org.jboss.cache.passivation;

import org.testng.annotations.Test;
import static org.jboss.cache.factories.UnitTestConfigurationFactory.*;

import java.io.File;
import java.io.FileFilter;

import org.jboss.cache.util.TestingUtil;

/**
 * Runs the same tests as {@link PassivationToFileCacheLoaderTest}, but with
 * Berkeley DB instead of a file-based CacheLoader
 *
 * @author <a href="mailto:{hmesha@novell.com}">{Hany Mesha}</a>
 * @version $Id: PassivationToBdbjeCacheLoaderTest.java 7422 2009-01-09 09:21:18Z mircea.markus $
 */
@Test(groups = "functional", testName = "passivation.PassivationToBdbjeCacheLoaderTest")
public class PassivationToBdbjeCacheLoaderTest extends PassivationTestsBase
{
   private String tmp_location = TestingUtil.TEST_FILES;
   private File dir = new File(tmp_location);

   public PassivationToBdbjeCacheLoaderTest()
   {
      if (!dir.exists()) dir.mkdirs();
   }

   protected void configureCache() throws Exception
   {

      class MyFilter implements FileFilter
      {
         public boolean accept(File file)
         {
            return file.getName().endsWith(".jdb");
         }
      }

      File[] files = dir.listFiles(new MyFilter());
      if (files != null)
      {
         for (int i = 0; i < files.length; i += 1)
         {
            File file = files[i];
            if (file.isFile())
            {
               if (!file.delete())
               {
                  System.err.println("Unable to delete: " + file);
               }
            }
         }
      }
      
      String tmpDir = TestingUtil.TEST_FILES;
      String threadId = Thread.currentThread().getName();
      String tmpCLLoc = tmpDir + "/JBossCache-PassivationToBdbjeCacheLoaderTest-" + threadId;
      cache.getConfiguration().setCacheLoaderConfig(buildSingleCacheLoaderConfig(true, null, "org.jboss.cache.loader.bdbje.BdbjeCacheLoader", "location=" + tmpCLLoc, false, false, false, false, false));
   }
}
