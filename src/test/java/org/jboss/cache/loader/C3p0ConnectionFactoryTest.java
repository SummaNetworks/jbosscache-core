/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import com.mchange.v2.c3p0.PooledDataSource;
import org.jboss.cache.util.UnitTestDatabaseManager;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.util.Properties;

/**
 * Unit test for C3p0ConnectionFactory
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
@Test(groups = {"functional"}, testName = "loader.C3p0ConnectionFactoryTest")
public class C3p0ConnectionFactoryTest
{
   private C3p0ConnectionFactory cf;
   private AdjListJDBCCacheLoaderConfig config;

   private Properties props;

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

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      config = new AdjListJDBCCacheLoaderConfig();
      config.setProperties(props);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      cf.stop();
   }

   public void testSetConfig() throws Exception
   {
      config.getProperties().setProperty("c3p0.checkoutTimeout", "10000");

      /* We set the maxPoolSize in two different ways. First, via a System property and secondly, emulating XML
      configuration, as it maxPoolSize had been set in the cache loader properties. The system property should
      be the one used. Further explanation in C3p0ConnectionFactory */

      System.setProperty("c3p0.maxPoolSize", "5");
      config.getProperties().setProperty("c3p0.maxPoolSize", "3");

      cf = new C3p0ConnectionFactory();
      cf.setConfig(config);
      cf.start();

      Connection c1 = cf.getConnection();
      Connection c2 = cf.getConnection();
      Connection c3 = cf.getConnection();
      Connection c4 = cf.getConnection();
      Connection c5 = cf.getConnection();
      Connection c6 = null;

      try
      {
         c6 = cf.getConnection();
         fail("Should have produced an SQLException indicating that it timed out checking out a Connection");
      }
      catch (Exception good)
      {
      }
      finally
      {
         cf.close(c1);
         cf.close(c2);
         cf.close(c3);
         cf.close(c4);
         cf.close(c5);
         cf.close(c6);
      }
   }

   public void testGetConnection() throws Exception
   {
      cf = new C3p0ConnectionFactory();
      cf.setConfig(config);
      cf.start();
      PooledDataSource internalDs = (PooledDataSource) cf.getDataSource();

      Connection c1 = cf.getConnection();
      Connection c2 = cf.getConnection();
      assertEquals("There should be two connections checked out", 2, internalDs.getNumBusyConnectionsDefaultUser());

      cf.close(c1);
      Thread.sleep(100);
      assertEquals("There should be one connection checked out", 1, internalDs.getNumBusyConnectionsDefaultUser());

      cf.close(c2);
      Thread.sleep(100);
      assertEquals("There should be no connections checked out", 0, internalDs.getNumBusyConnectionsDefaultUser());
   }
}
