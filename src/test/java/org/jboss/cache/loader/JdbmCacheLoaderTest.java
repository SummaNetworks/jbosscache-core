package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

/**
 * Tests {@link org.jboss.cache.loader.jdbm.JdbmCacheLoader}.
 *
 * @author Elias Ross
 * @version $Id: JdbmCacheLoaderTest.java 7422 2009-01-09 09:21:18Z mircea.markus $
 */
@Test (groups = {"functional"}, testName = "loader.JdbmCacheLoaderTest")
public class JdbmCacheLoaderTest extends CacheLoaderTestsBase
{
   protected void configureCache(CacheSPI cache) throws Exception
   {
      String tmpCLLoc = TestingUtil.TEST_FILES + "/JdbmCacheLoaderTest";
      cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.jdbm.JdbmCacheLoader",
            "location=" + tmpCLLoc, false, true, false, false, false));
      TestingUtil.recursiveFileRemove(tmpCLLoc);
   }
   
   public void testCacheLoaderThreadSafety() throws Exception
   {
   }

   public void testCacheLoaderThreadSafetyMultipleFqns() throws Exception
   {
   }

   public void testIgnoreModifications() throws Exception
   {
   }
}
