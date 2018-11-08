package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;

/**
 * Runs the same tests as {@link FileCacheLoaderTest}, but with Berkeley DB instead of a file-based CacheLoader
 *
 * @author Bela Ban
 * @version $Id: BdbjeCacheLoaderTest.java 7422 2009-01-09 09:21:18Z mircea.markus $
 */
@Test(groups = "functional", testName = "loader.BdbjeCacheLoaderTest")
public class BdbjeCacheLoaderTest extends CacheLoaderTestsBase
{
   
   @Override
   protected void configureCache(CacheSPI cache) throws Exception
   {
      String tmpDir = TestingUtil.TEST_FILES;
      String tmpCLLoc = tmpDir + "/JBossCache-BdbjeCacheLoaderTest";

      cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.bdbje.BdbjeCacheLoader",
            "location=" + tmpCLLoc, false, true, false, false, false));
      TestingUtil.recursiveFileRemove(tmpCLLoc);
   }

   @BeforeMethod
   public void clearDatabase() throws Exception 
   {
      loader.remove(Fqn.ROOT);
   }


   public void testTransaction() throws Exception
   {

      // to help recreate the issue in
      // http://www.jboss.com/index.html?module=bb&op=viewtopic&p=4048003#4048003

      Fqn fqn = Fqn.fromString("/a/b/c");
      String key = "key", value = "value";

      cache.put(fqn, key, value);
      cache.getTransactionManager().begin();
      assertEquals(value, cache.get(fqn, key));
      cache.getTransactionManager().commit();

      // now repeat this.

      cache.getTransactionManager().begin();
      assertEquals(value, cache.get(fqn, key));
      cache.getTransactionManager().commit();
   }


}
