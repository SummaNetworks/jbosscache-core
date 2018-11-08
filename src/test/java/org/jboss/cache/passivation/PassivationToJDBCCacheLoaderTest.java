/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.passivation;

import org.jboss.cache.Fqn;
import org.jboss.cache.config.CacheLoaderConfig;
import static org.jboss.cache.factories.UnitTestConfigurationFactory.buildSingleCacheLoaderConfig;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.UnitTestDatabaseManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Properties;

/**
 * Tests passivation using JDBC Cache Loader.
 * This test has MySQL hard-coded. To run it, run MySQL first: mysqld -u=root
 *
 * @author <a href="mailto:{hmesha@novell.com}">{Hany Mesha}</a>
 * @version $Id: PassivationToJDBCCacheLoaderTest.java 7422 2009-01-09 09:21:18Z mircea.markus $
 */
@Test(groups = "functional", testName = "passivation.PassivationToJDBCCacheLoaderTest")
public class PassivationToJDBCCacheLoaderTest extends PassivationTestsBase
{

   private Properties props;
   private long durration;

   @BeforeTest
   public void createDatabase()
   {
      durration = System.currentTimeMillis();
      props = UnitTestDatabaseManager.getTestDbProperties();
   }

   @AfterTest
   public void shutDownDatabase()
   {
      UnitTestDatabaseManager.shutdownInMemoryDatabase(props);
   }

   private Properties getJDBCProps()
   {
      return props;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      log.info("**** TEARING DOWN ****");
      if (loader != null) loader.remove(Fqn.ROOT);
      TestingUtil.killCaches(cache);
      UnitTestDatabaseManager.shutdownInMemoryDatabase(props);
   }


   protected void configureCache() throws Exception
   {
      CacheLoaderConfig loaderConfig = buildSingleCacheLoaderConfig(true, null, "org.jboss.cache.loader.JDBCCacheLoader",
            getJDBCProps(), false, false, false, false, false);
      cache.getConfiguration().setCacheLoaderConfig(loaderConfig);
   }
}
