package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

/**
 * Tests {@link org.jboss.cache.loader.jdbm.JdbmCacheLoader2}.
 *
 * @author Elias Ross
 * @version $Id: JdbmCacheLoaderTest.java 6905 2008-10-13 09:35:27Z dpospisi@redhat.com $
 */
@Test (groups = {"functional"}, testName = "loader.JdbmCacheLoader2Test")
public class JdbmCacheLoader2Test extends CacheLoaderTestsBase
{
   @Override
   protected void configureCache(CacheSPI cache) throws Exception
   {
      String tmpCLLoc = TestingUtil.TEST_FILES + "/JdbmCacheLoader2Test";
      cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.jdbm.JdbmCacheLoader2",
            "location=" + tmpCLLoc, false, true, false, false, false));
      TestingUtil.recursiveFileRemove(tmpCLLoc);
   }
   
   @Override
   public void testCacheLoaderThreadSafety() throws Exception
   {
   }

   @Override
   public void testCacheLoaderThreadSafetyMultipleFqns() throws Exception
   {
   }

   public void testIgnoreModifications() throws Exception
   {
   }
}
