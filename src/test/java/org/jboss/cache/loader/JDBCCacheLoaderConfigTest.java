/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Properties;

/**
 * Unit test for JDBCCacheLoaderConfigTest
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
@Test(groups = {"unit"}, testName = "loader.JDBCCacheLoaderConfigTest")
public class JDBCCacheLoaderConfigTest
{
   private AdjListJDBCCacheLoaderConfig cacheLoaderConfig;

   @BeforeMethod(alwaysRun = true)
   protected void setUp() throws Exception
   {
      cacheLoaderConfig = new AdjListJDBCCacheLoaderConfig();
   }

   public void testSetGetConnectionFactory()
   {
      cacheLoaderConfig.setConnectionFactoryClass("com.acme.Paradise");
      assertEquals("com.acme.Paradise", cacheLoaderConfig.getConnectionFactoryClass());
   }

   public void testEqualsHashCode()
   {
      cacheLoaderConfig.setConnectionFactoryClass("com.acme.Paradise");
      AdjListJDBCCacheLoaderConfig other = new AdjListJDBCCacheLoaderConfig();
      other.setConnectionFactoryClass("com.acme.Paradise");
      assertTrue(cacheLoaderConfig.equals(other));
      assertEquals(cacheLoaderConfig.hashCode(), other.hashCode());

      other.setConnectionFactoryClass("com.ibm.flaming.Gala");
      assertFalse(cacheLoaderConfig.equals(other));
   }

   public void testSetGetBatchInfo()
   {
      Properties props = new Properties();
      props.put("cache.jdbc.table.name", "jbosscache");
      props.put("cache.jdbc.table.create", "true");
      props.put("cache.jdbc.table.drop", "true");
      props.put("cache.jdbc.table.primarykey", "jbosscache_pk");
      props.put("cache.jdbc.fqn.column", "fqn");
      props.put("cache.jdbc.fqn.type", "varchar(255)");
      props.put("cache.jdbc.node.column", "node");
      props.put("cache.jdbc.node.type", "blob");
      props.put("cache.jdbc.parent.column", "parent");
      props.put("cache.jdbc.driver", "org.hsqldb.jdbcDriver");
      props.put("cache.jdbc.url", "jdbc:hsqldb:mem:jbosscache");
      props.put("cache.jdbc.user", "sa");
      props.put("cache.jdbc.password", "");
      props.put("cache.jdbc.batch.enable", "false");
      props.put("cache.jdbc.batch.size", "1001");
      JDBCCacheLoaderConfig config = new JDBCCacheLoaderConfig();
      config.setProperties(props);
      assert !config.isBatchEnabled();
      assert config.getBatchSize() == 1001;
   }
}
