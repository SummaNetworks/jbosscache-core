/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;

/**
 * Unit test that runs the the tests defined JDBCCacheLoaderTest using a standalone
 * connection pool factory based on c3p0 library.
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
@Test(groups = "functional", testName = "loader.C3p0JDBCCacheLoaderTest")
public class C3p0JDBCCacheLoaderTest extends JDBCCacheLoaderTest
{
   private static final String CF_CLASS = "org.jboss.cache.loader.C3p0ConnectionFactory";

   @BeforeTest
   public void createDatabase()
   {
      super.createDatabase();
      props.put("cache.jdbc.connection.factory", CF_CLASS);
   }

   protected void configureCache(CacheSPI cache) throws Exception
   {
      cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.JDBCCacheLoader", props, false, true, false, false, false));
   }
}
