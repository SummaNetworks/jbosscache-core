/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.hsqldb.jdbc.jdbcDataSource;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.util.UnitTestDatabaseManager;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import java.util.Properties;

/**
 * This test runs a JDBC cache loader, obtaining connections from a JDBC DataSource.
 * <p/>
 * This test uses HSQL and an in-memory DB.
 */
@Test(groups = {"functional"}, testName = "loader.JDBCCacheLoaderHsqldbDSTest")
public class JDBCCacheLoaderHsqldbDSTest extends CacheLoaderTestsBase
{
   private final String FACTORY = "org.jboss.cache.transaction.DummyContextFactory";
   private final String JNDI_NAME = "java:/TestDS";
   private Properties prop;
   private jdbcDataSource ds;

   protected void configureCache(CacheSPI cache) throws Exception
   {
      if (ds == null)
      {
         System.setProperty(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
         DummyTransactionManager.getInstance();

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

         prop = UnitTestDatabaseManager.getTestDbProperties();

         ds = new jdbcDataSource();
         ds.setDatabase(prop.getProperty("cache.jdbc.url"));
         ds.setUser("sa");

         context.bind(JNDI_NAME, ds);
         assertNotNull(JNDI_NAME + " bound", context.lookup(JNDI_NAME));
      }
      Properties p = new Properties();
      p.setProperty("cache.jdbc.datasource", JNDI_NAME);
      p.setProperty("cache.jdbc.node.type", prop.getProperty("cache.jdbc.node.type"));
      p.setProperty("cache.jdbc.table.name", prop.getProperty("cache.jdbc.table.name"));
      p.setProperty("cache.jdbc.table.primarykey", prop.getProperty("cache.jdbc.table.primarykey"));
      JDBCCacheLoaderConfig jdbcConfig = new JDBCCacheLoaderConfig();
      jdbcConfig.setFetchPersistentState(true);
      jdbcConfig.setProperties(p);
      CacheLoaderConfig config = new CacheLoaderConfig();
      config.addIndividualCacheLoaderConfig(jdbcConfig);
      cache.getConfiguration().setCacheLoaderConfig(config);
      cache.create();
   }

   @AfterTest
   protected void destroyDbAfterTest() throws Exception
   {
      Properties icProps = new Properties();
      icProps.setProperty(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
      Context ctx = new InitialContext(icProps);
      ctx.unbind(JNDI_NAME);
      UnitTestDatabaseManager.shutdownInMemoryDatabase(prop);
   }

   public void testLargeObject() throws Exception
   {
      String key = "LargeObj";
      // create an object with size bigger than 4k (k=1024 bytes)
      StringBuilder text = new StringBuilder("LargeObject");
      while (text.toString().getBytes().length < (1024 * 100))
      {
         text.append(text);
      }
      String initialValue = text.toString();
      // insert it into the cache loader
      loader.remove(Fqn.fromString("/"));

      Object retVal = loader.put(FQN, key, initialValue);
      assertNull(retVal);
      addDelay();
      // load the object from the cache loader and validate it
      assertEquals(initialValue, (String) loader.get(FQN).get(key));
      // update the object and validate it
      String updatedValue = initialValue.concat(("UpdatedValue"));
      retVal = loader.put(FQN, key, updatedValue);
      assertEquals(initialValue, (String) retVal);
      assertEquals(updatedValue, (String) loader.get(FQN).get(key));
   }

   @Override
   public void testTransactionRollback() throws Exception
   {
      // no-op
   }

   @Override
   public void testIntegratedTransactionRollback() throws Exception
   {
      // no-op
   }
}
