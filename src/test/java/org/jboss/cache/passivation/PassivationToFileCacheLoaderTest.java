package org.jboss.cache.passivation;

import org.jboss.cache.util.TestingUtil;
import static org.jboss.cache.factories.UnitTestConfigurationFactory.buildSingleCacheLoaderConfig;
import org.testng.annotations.Test;

/**
 * tests passivation using file cache loader
 *
 * @author <a href="mailto:{hmesha@novell.com}">{Hany Mesha}</a>
 * @version $Id: PassivationToFileCacheLoaderTest.java 7422 2009-01-09 09:21:18Z mircea.markus $
 */
@Test(groups = "functional", testName = "passivation.PassivationToFileCacheLoaderTest")
public class PassivationToFileCacheLoaderTest extends PassivationTestsBase
{
   protected void configureCache() throws Exception
   {

      String tmpLocation = null;
      String OS = System.getProperty("os.name").toLowerCase();
//      if (OS.contains("win") || OS.contains("nt"))
//      {
//         tmpLocation = TestingUtil.TEST_FILES;
//      }
//      else
//      {
         tmpLocation = TestingUtil.TEST_FILES;
//      }

      String threadId = Thread.currentThread().getName();
      String tmpCLLoc = tmpLocation + "/JBossCache-PassivationToFileCacheLoaderTest-" + threadId;
      
      cache.getConfiguration().setCacheLoaderConfig(buildSingleCacheLoaderConfig(true, null, "org.jboss.cache.loader.FileCacheLoader", "location=" + tmpCLLoc, false, false, false, false, false));
   }
}
