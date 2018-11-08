package org.jboss.cache.loader;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.UnitTestDatabaseManager;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.util.Properties;

/**
 * To test the closing of JDBC connections
 */
@Test(groups = "functional", sequential = true, testName = "loader.JDBCCacheLoaderConnectionTest")
public class JDBCCacheLoaderConnectionTest
{
   private Cache cache;
   private Properties props;

   @BeforeMethod
   public void setUp() throws Exception
   {
      props = UnitTestDatabaseManager.getTestDbProperties();
      cache = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", JDBCCacheLoader.class.getName(), props, false, false, true, false, false));
      cache.start();
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      UnitTestDatabaseManager.shutdownInMemoryDatabase(props);
      cache = null;
   }

   public void testConnectionRelease() throws Exception
   {
      cache.removeNode(Fqn.fromString("C"));
      for (int i = 0; i < 100; i++)
      {
         cache.put(Fqn.fromElements("C", Integer.toString(i)), "Blah", Integer.toString(i));
      }

      assertConnectionsClosed();
   }

   private void assertConnectionsClosed() throws Exception
   {
      JDBCCacheLoader loader = (JDBCCacheLoader) ((CacheSPI) cache).getCacheLoaderManager().getCacheLoader();
      NonManagedConnectionFactory cf = (NonManagedConnectionFactory) loader.cf;
      Connection conn = cf.connection.get();
      if (conn != null)
      {
         // make sure it is closed/released!
         assert conn.isClosed();
      }
   }
}
