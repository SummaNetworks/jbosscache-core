/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.UnitTestDatabaseManager;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.Properties;

/**
 * This test runs cache loader tests using Database as the cache loader store.
 * The default test is configured using Derby embedded framework.
 * The server and database configuration is read from a properties file located at
 * /etc/cache-jdbc.properties.
 * <p/>
 * To run this test with any other RDBMS, The appropriate JDBC driver
 * (i.e mysql-connector-java-3.0.10-stable-bin.jar)
 * must be in the lib directory.
 *
 * @author <a href="hmesha@novell.com">Hany Mesha</a>
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 * @version <tt>$Revision: 7422 $</tt>
 */
@Test(groups = {"functional"}, testName = "loader.JDBCCacheLoaderTest")
public class JDBCCacheLoaderTest extends CacheLoaderTestsBase
{
   protected Properties props;

   @BeforeTest
   public void createDatabase()
   {
      props = UnitTestDatabaseManager.getTestDbProperties();
   }

   @AfterTest
   public void shutDownDatabase()
   {
      UnitTestDatabaseManager.shutdownInMemoryDatabase(props);
   }

   @BeforeMethod
   public void clearDatabase() throws Exception
   {
      loader.remove(Fqn.ROOT);
   }

   protected void configureCache(CacheSPI cache) throws Exception
   {
      String props = props2String(this.props);
      cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", JDBCCacheLoader.class.getName(),
            props, false, true, false, false, false));
   }

   private String props2String(Properties prop)
   {
      StringBuilder p = new StringBuilder();
      append("cache.jdbc.driver", prop, p);
      append("cache.jdbc.url", prop, p);
      append("cache.jdbc.user", prop, p);
      append("cache.jdbc.password", prop, p);
      append("cache.jdbc.node.type", prop, p);
      append("cache.jdbc.table.name", prop, p);
      append("cache.jdbc.table.primarykey", prop, p);
      return p.toString();
   }

   private void append(String propertyName, Properties prop, StringBuilder builder)
   {
      if (prop.containsKey(propertyName))
      {
         builder.append(propertyName).append("=").append(prop.getProperty(propertyName)).append("\n");
      }
   }

   public void testLargeObject()
   {
      try
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
      catch (Exception e)
      {
         fail(e.toString());
      }
   }

   public void testRootIsCreated() throws Exception
   {
      loader.put(Fqn.fromString("/a/b/c"), "a", "b");
      assertTrue(loader.exists(Fqn.ROOT));
   }
}
