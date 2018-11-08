/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.UnitTestDatabaseManager;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

@Test(groups = "functional", sequential = true, testName = "loader.DataSourceIntegrationTest")
public class DataSourceIntegrationTest
{
   //private String old_factory = null;
   private final String FACTORY = "org.jboss.cache.transaction.DummyContextFactory";
   private final String JNDI_NAME = "java:/MockDS";
   private CacheSPI cache;
   private Properties props;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      //old_factory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
      DummyTransactionManager.getInstance();
   }

   protected CacheLoaderConfig getCacheLoaderConfig(Properties jndi) throws Exception
   {
      Properties props = new Properties(jndi);
      props.put("cache.jdbc.datasource", JNDI_NAME);
      props.put("cache.jdbc.table.create", true);
      props.put("cache.jdbc.table.drop", true);
      return UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.JDBCCacheLoader", props, false, false, false, false, false);
   }

   /**
    * Tests fix for JBCACHE-303, ensuring that JDBCCacheLoader works if
    * its DataSource is not in JNDI until start is called.
    *
    * @throws Exception
    */
   public void testDataSourceIntegration() throws Exception
   {
      props = null;
      Context context = new InitialContext();
      try
      {
         Object obj = context.lookup(JNDI_NAME);
         assertNull(JNDI_NAME + " not bound", obj);
      }
      catch (NameNotFoundException n)
      {
         // expected
      }
      props = UnitTestDatabaseManager.getTestDbProperties();
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setCacheMode("local");
      cache.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(props));
      cache.create();


      MockDataSource ds = new MockDataSource(props);
      context.bind(JNDI_NAME, ds);
      assertNotNull(JNDI_NAME + " bound", context.lookup(JNDI_NAME));
      cache.start();

      assertNotNull("Cache has a cache loader", cache.getCacheLoaderManager().getCacheLoader());
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      Context ctx = new InitialContext();
      ctx.unbind(JNDI_NAME);
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         UnitTestDatabaseManager.shutdownInMemoryDatabase(props);
         cache = null;
      }
   }

   private static class MockDataSource implements DataSource
   {
      private String userName;
      private String jdbcUrl;
      private String jdbcPassword;

      public MockDataSource(Properties properties)
      {
         try
         {
            Class.forName(properties.getProperty("cache.jdbc.driver"));
         }
         catch (ClassNotFoundException e)
         {
            throw new RuntimeException(e);
         }
         userName = properties.getProperty("cache.jdbc.user");
         jdbcUrl = properties.getProperty("cache.jdbc.url");
         jdbcPassword = properties.getProperty("cache.jdbc.password");
      }

      public Connection getConnection() throws SQLException
      {
         return DriverManager.getConnection(jdbcUrl, userName, jdbcPassword);
      }

      public Connection getConnection(String user, String password) throws SQLException
      {
         return DriverManager.getConnection(jdbcUrl, userName, jdbcPassword);
      }

      public int getLoginTimeout() throws SQLException
      {
         return 0;
      }

       /**
        * Return the parent Logger of all the Loggers used by this data source. This
        * should be the Logger farthest from the root Logger that is
        * still an ancestor of all of the Loggers used by this data source. Configuring
        * this Logger will affect all of the log messages generated by the data source.
        * In the worst case, this may be the root Logger.
        *
        * @return the parent Logger for this data source
        * @throws SQLFeatureNotSupportedException if the data source does not use <code>java.util.logging<code>.
        * @since 1.7
        */
       @Override
       public Logger getParentLogger() throws SQLFeatureNotSupportedException {
           return null;
       }

       public PrintWriter getLogWriter() throws SQLException
      {
         return null;
      }

      public void setLoginTimeout(int seconds) throws SQLException
      {
      }

      public void setLogWriter(PrintWriter printWriter) throws SQLException
      {
      }

      // preliminary JDK6 support - just so it compiles!!!
      public boolean isWrapperFor(Class<?> ifc)
      {
         return false;
      }

      public <T> T unwrap(Class<T> iface)
      {
         return null;
      }
   }
}
