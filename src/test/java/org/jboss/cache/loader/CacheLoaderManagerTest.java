/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.element.LoadersElementParser;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Properties;

/**
 * Tests the construction of a cache laoder based on an XML element passed in.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
@Test(groups = "functional", sequential = true, testName = "loader.CacheLoaderManagerTest")
public class CacheLoaderManagerTest
{
   private LoadersElementParser loadersElementParser = new LoadersElementParser();

   private CacheLoaderConfig createCacheLoaderCfg(boolean passivation)
   {
      CacheLoaderConfig cfg = new CacheLoaderConfig();
      cfg.setPassivation(passivation);
      return cfg;
   }

   private CacheLoaderConfig.IndividualCacheLoaderConfig createIndividualCacheLoaderConfig(CacheLoaderConfig parent, boolean async, String classname)
   {
      CacheLoaderConfig.IndividualCacheLoaderConfig cfg = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      cfg.setAsync(async);
      cfg.setClassName(classname);
      cfg.setFetchPersistentState(false);
      Properties p = new Properties();
      p.setProperty("location", getTempDir());
      p.setProperty("cache.jdbc.driver", "com.mysql.jdbc.Driver");
      p.setProperty("cache.jdbc.url", "jdbc:mysql://localhost/test");
      p.setProperty("cache.jdbc.user", "user");
      p.setProperty("cache.jdbc.password", "pwd");
      cfg.setProperties(p);
      return cfg;
   }

   private String getTempDir()
   {
      return System.getProperty("java.io.tempdir", "/tmp");
   }

   private static Element strToElement(String s) throws Exception
   {
      return XmlConfigHelper.stringToElementInCoreNS(s);
   }

   public void testSingleCacheLoader() throws Exception
   {
      // without async
      CacheLoaderManager mgr = new CacheLoaderManager();
      CacheLoaderConfig cfg = createCacheLoaderCfg(false);
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, false, "org.jboss.cache.loader.FileCacheLoader"));

      mgr.setConfig(cfg, null, null);
      CacheLoader cl = mgr.getCacheLoader();

      assertEquals(FileCacheLoader.class, cl.getClass());

      // with async
      cfg = createCacheLoaderCfg(false);
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.FileCacheLoader"));

      mgr.setConfig(cfg, null, null);
      cl = mgr.getCacheLoader();

      assertEquals(AsyncCacheLoader.class, cl.getClass());
   }

   public void testSingleCacheLoaderPassivation() throws Exception
   {
      // without async
      CacheLoaderConfig cfg = createCacheLoaderCfg(true);
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, false, "org.jboss.cache.loader.FileCacheLoader"));
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, false, "org.jboss.cache.loader.bdbje.BdbjeCacheLoader"));
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, false, "org.jboss.cache.loader.JDBCCacheLoader"));

      CacheLoaderManager mgr = new CacheLoaderManager();
      mgr.setConfig(cfg, null, null);
      CacheLoader cl = mgr.getCacheLoader();

      assertEquals(FileCacheLoader.class, cl.getClass());

      // with async
      cfg = createCacheLoaderCfg(true);
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.FileCacheLoader"));
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.bdbje.BdbjeCacheLoader"));
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.JDBCCacheLoader"));

      mgr.setConfig(cfg, null, null);
      cl = mgr.getCacheLoader();

      assertEquals(AsyncCacheLoader.class, cl.getClass());
   }

   public void testSingleCacheLoaderFromXml() throws Exception
   {
      // without async
      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.FileCacheLoader",
            "location=" + getTempDir(), false, false, false, false, false);

      CacheLoaderManager mgr = new CacheLoaderManager();
      mgr.setConfig(clc, null, null);
      CacheLoader cl = mgr.getCacheLoader();

      assertEquals(FileCacheLoader.class, cl.getClass());

      // with async
      clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.FileCacheLoader",
            "location=" + getTempDir(), true, false, false, false, false);

      mgr.setConfig(clc, null, null);
      cl = mgr.getCacheLoader();
      assertEquals(AsyncCacheLoader.class, cl.getClass());
   }

   public void testSingleCacheLoaderPassivationFromXml() throws Exception
   {
      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(true, "", "org.jboss.cache.loader.FileCacheLoader", "location=" + getTempDir(), false, false, false, false, false);
      CacheLoaderConfig bdbjeCl = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(true, "", "org.jboss.cache.loader.bdbje.BdbjeCacheLoader", "location=" + getTempDir(), false, false, false, false, false);
      CacheLoaderConfig jdbCl = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(true, "", "org.jboss.cache.loader.JDBCCacheLoader", "location=" + getTempDir(), false, false, false, false, false);
      clc.getIndividualCacheLoaderConfigs().add(bdbjeCl.getFirstCacheLoaderConfig());
      clc.getIndividualCacheLoaderConfigs().add(jdbCl.getFirstCacheLoaderConfig());
      CacheLoaderManager mgr = new CacheLoaderManager();
      mgr.setConfig(clc, null, null);
      CacheLoader cl = mgr.getCacheLoader();
      assertEquals(FileCacheLoader.class, cl.getClass());

      clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(true, "", "org.jboss.cache.loader.FileCacheLoader", "location=" + getTempDir(), true, false, false, false, false);
      bdbjeCl = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(true, "", "org.jboss.cache.loader.bdbje.BdbjeCacheLoader", "location=" + getTempDir(), true, false, false, false, false);
      jdbCl = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(true, "", "org.jboss.cache.loader.JDBCCacheLoader", "location=" + getTempDir(), true, false, false, false, false);
      clc.getIndividualCacheLoaderConfigs().add(bdbjeCl.getFirstCacheLoaderConfig());
      clc.getIndividualCacheLoaderConfigs().add(jdbCl.getFirstCacheLoaderConfig());

      mgr.setConfig(clc, null, null);
      cl = mgr.getCacheLoader();

      assertEquals(AsyncCacheLoader.class, cl.getClass());
   }

   public void testChainingCacheLoader() throws Exception
   {
      // async = false
      CacheLoaderConfig cfg = createCacheLoaderCfg(false);
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, false, "org.jboss.cache.loader.FileCacheLoader"));
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, false, "org.jboss.cache.loader.JDBCCacheLoader"));

      CacheLoaderManager mgr = new CacheLoaderManager();
      mgr.setConfig(cfg, null, null);
      CacheLoader cl = mgr.getCacheLoader();

      assertEquals(ChainingCacheLoader.class, cl.getClass());
      assertEquals(2, ((ChainingCacheLoader) cl).getSize());
      List loaders = ((ChainingCacheLoader) cl).getCacheLoaders();
      assertEquals(FileCacheLoader.class, loaders.get(0).getClass());
      assertEquals(JDBCCacheLoader.class, loaders.get(1).getClass());

      // async = true
      cfg = createCacheLoaderCfg(false);
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, false, "org.jboss.cache.loader.FileCacheLoader"));
      cfg.addIndividualCacheLoaderConfig(createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.JDBCCacheLoader"));

      mgr.setConfig(cfg, null, null);
      cl = mgr.getCacheLoader();

      assertEquals(ChainingCacheLoader.class, cl.getClass());
      assertEquals(2, ((ChainingCacheLoader) cl).getSize());
      loaders = ((ChainingCacheLoader) cl).getCacheLoaders();
      assertEquals(FileCacheLoader.class, loaders.get(0).getClass());
      assertEquals(AsyncCacheLoader.class, loaders.get(1).getClass());
   }

   public void testChainingCacheLoaderFromXml() throws Exception
   {
      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "",
            "org.jboss.cache.loader.FileCacheLoader", "a=b", false, false, false, false, false);
      clc.addIndividualCacheLoaderConfig(UnitTestConfigurationFactory.buildIndividualCacheLoaderConfig("",
            "org.jboss.cache.loader.JDBCCacheLoader", "cache.jdbc.driver=com.mysql.jdbc.Driver\ncache.jdbc.url=jdbc:mysql://localhost/test\ncache.jdbc.user=user\ncache.jdbc.password=pwd", false, false, false, false));
      CacheLoaderManager mgr = new CacheLoaderManager();
      mgr.setConfig(clc, null, null);
      CacheLoader cl = mgr.getCacheLoader();


      assertEquals(ChainingCacheLoader.class, cl.getClass());
      assertEquals(2, ((ChainingCacheLoader) cl).getSize());
      List loaders = ((ChainingCacheLoader) cl).getCacheLoaders();
      assertEquals(FileCacheLoader.class, loaders.get(0).getClass());
      assertEquals(JDBCCacheLoader.class, loaders.get(1).getClass());

      // async = true
      clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "",
            "org.jboss.cache.loader.FileCacheLoader", "a=b", false, false, false, false, false);
      clc.addIndividualCacheLoaderConfig(UnitTestConfigurationFactory.buildIndividualCacheLoaderConfig("",
            "org.jboss.cache.loader.JDBCCacheLoader", "cache.jdbc.driver=com.mysql.jdbc.Driver\ncache.jdbc.url=jdbc:mysql://localhost/test\ncache.jdbc.user=user\ncache.jdbc.password=pwd", true, false, false, false));
      mgr = new CacheLoaderManager();
      mgr.setConfig(clc, null, null);
      cl = mgr.getCacheLoader();

      assertEquals(ChainingCacheLoader.class, cl.getClass());
      assertEquals(2, ((ChainingCacheLoader) cl).getSize());
      loaders = ((ChainingCacheLoader) cl).getCacheLoaders();
      assertEquals(FileCacheLoader.class, loaders.get(0).getClass());
      assertEquals(AsyncCacheLoader.class, loaders.get(1).getClass());
   }

   public void testMoreThanOneFetchPersistentState() throws Exception
   {
      CacheLoaderManager mgr = new CacheLoaderManager();
      CacheLoaderConfig cfg = createCacheLoaderCfg(false);
      CacheLoaderConfig.IndividualCacheLoaderConfig i = createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.FileCacheLoader");
      i.setFetchPersistentState(true);
      CacheLoaderConfig.IndividualCacheLoaderConfig i2 = createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.FileCacheLoader");
      i2.setFetchPersistentState(true);

      cfg.addIndividualCacheLoaderConfig(i);
      cfg.addIndividualCacheLoaderConfig(i2);

      assertEquals(2, cfg.getIndividualCacheLoaderConfigs().size());

      try
      {
         mgr.setConfig(cfg, null, null);
         assertTrue("Should throw exception since we have > 1 cache loader with fetchPersistentState as true", false);
      }
      catch (Exception e)
      {
         assertTrue(true);
      }

      // control cases which should not throw exceptions
      mgr = new CacheLoaderManager();
      cfg = createCacheLoaderCfg(false);
      i = createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.FileCacheLoader");
      i.setFetchPersistentState(true);

      cfg.addIndividualCacheLoaderConfig(i);

      assertEquals(1, cfg.getIndividualCacheLoaderConfigs().size());
      mgr.setConfig(cfg, null, null);

      // control cases which should not throw exceptions
      mgr = new CacheLoaderManager();
      cfg = createCacheLoaderCfg(false);
      i = createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.FileCacheLoader");
      i.setFetchPersistentState(true);
      i2 = createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.FileCacheLoader");
      i2.setFetchPersistentState(false);

      cfg.addIndividualCacheLoaderConfig(i);
      cfg.addIndividualCacheLoaderConfig(i2);

      assertEquals(2, cfg.getIndividualCacheLoaderConfigs().size());
      mgr.setConfig(cfg, null, null);

      // control cases which should not throw exceptions
      mgr = new CacheLoaderManager();
      cfg = createCacheLoaderCfg(false);
      i = createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.FileCacheLoader");
      i.setFetchPersistentState(false);
      i2 = createIndividualCacheLoaderConfig(cfg, true, "org.jboss.cache.loader.FileCacheLoader");
      i2.setFetchPersistentState(false);

      cfg.addIndividualCacheLoaderConfig(i);
      cfg.addIndividualCacheLoaderConfig(i2);

      assertEquals(2, cfg.getIndividualCacheLoaderConfigs().size());
      mgr.setConfig(cfg, null, null);
   }

   public void testSingletonConfiguration() throws Exception
   {
      /************************************************************************************************************/
      String conf;
      CacheLoaderConfig clc;
      CacheLoaderManager mgr;
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc;
      conf =
            "<loaders passivation=\"false\">\n" +
                  "   <preload/>\n" +
                  "   <loader class=\"org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader\">" +
                  "       <singletonStore enabled=\"true\"/>\n" +
                  "   </loader>" +
                  "</loaders>";

      clc = loadersElementParser.parseLoadersElement(strToElement(conf));
      mgr = new MockCacheLoaderManager();
      mgr.setConfig(clc, null, null);

      iclc = mgr.getCacheLoaderConfig().getFirstCacheLoaderConfig();
      assertNotNull("Singleton has been configured", iclc.getSingletonStoreConfig());
      assertTrue("Singleton should enabled", iclc.getSingletonStoreConfig().isSingletonStoreEnabled());
      assertEquals("Singleton class should be default", SingletonStoreCacheLoader.class.getName(), iclc.getSingletonStoreConfig().getSingletonStoreClass());
      assertNotNull("Singleton properties should be not null", iclc.getSingletonStoreConfig().getSingletonStoreproperties());
      assertTrue("Singleton properties should be empty", iclc.getSingletonStoreConfig().getSingletonStoreproperties().keySet().isEmpty());
      SingletonStoreDefaultConfig ssdc = ((SingletonStoreCacheLoader) mgr.getCacheLoader()).getSingletonStoreDefaultConfig();
      assertTrue("Singleton pushStateWhenCoordinator should be true (default)", ssdc.isPushStateWhenCoordinator());
      assertEquals("Singleton pushStateWhenCoordinatorTimeout should be default value", 20000, ssdc.getPushStateWhenCoordinatorTimeout());

      /************************************************************************************************************/
      conf =
            "<loaders passivation=\"false\">\n" +
                  "   <preload/>\n" +
                  "   <loader class=\"org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader\">" +
                  "       <singletonStore enabled=\"true\" class=\"org.jboss.cache.loader.CacheLoaderManagerTest$MockSingletonStoreCacheLoader\" />\n" +
                  "   </loader>" +
                  "</loaders>";
      clc = loadersElementParser.parseLoadersElement(strToElement(conf));
      mgr = new CacheLoaderManager();
      mgr.setConfig(clc, null, null);

      iclc = mgr.getCacheLoaderConfig().getFirstCacheLoaderConfig();
      assertNotNull("Singleton has been configured", iclc.getSingletonStoreConfig());
      assertTrue("Singleton should enabled", iclc.getSingletonStoreConfig().isSingletonStoreEnabled());
      assertEquals("Singleton class should be a user defined one", MockSingletonStoreCacheLoader.class.getName(), iclc.getSingletonStoreConfig().getSingletonStoreClass());
      assertNotNull("Singleton properties should be not null", iclc.getSingletonStoreConfig().getSingletonStoreproperties());
      assertTrue("Singleton properties should be empty", iclc.getSingletonStoreConfig().getSingletonStoreproperties().keySet().isEmpty());
      assertTrue(mgr.getCacheLoader() instanceof MockSingletonStoreCacheLoader);

      /************************************************************************************************************/

      conf =
            "   <loaders passivation=\"true\">\n" +
                  "      <preload/>\n" +
                  "      <loader class=\"org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader\">\n" +
                  "         <singletonStore enabled=\"true\">\n" +
                  "            <properties>\n" +
                  "               pushStateWhenCoordinator=false\n" +
                  "            </properties>\n" +
                  "         </singletonStore>\n" +
                  "      </loader>\n" +
                  "   </loaders>";
      clc = loadersElementParser.parseLoadersElement(strToElement(conf));
      mgr = new CacheLoaderManager();
      mgr.setConfig(clc, null, null);

      iclc = mgr.getCacheLoaderConfig().getFirstCacheLoaderConfig();
      assertNotNull("Singleton has been configured", iclc.getSingletonStoreConfig());
      assertTrue("Singleton should enabled", iclc.getSingletonStoreConfig().isSingletonStoreEnabled());
      assertEquals("Singleton class should be default", SingletonStoreCacheLoader.class.getName(), iclc.getSingletonStoreConfig().getSingletonStoreClass());
      assertNotNull("Singleton properties should be defined", iclc.getSingletonStoreConfig().getSingletonStoreproperties());
      ssdc = ((SingletonStoreCacheLoader) mgr.getCacheLoader()).getSingletonStoreDefaultConfig();
      assertFalse("Singleton pushStateWhenCoordinator should be false", ssdc.isPushStateWhenCoordinator());

      /************************************************************************************************************/

      conf =
            "   <loaders passivation=\"true\">\n" +
                  "      <preload/>\n" +
                  "      <loader class=\"org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader\">\n" +
                  "         <singletonStore enabled=\"true\">\n" +
                  "            <properties>\n" +
                  "                pushStateWhenCoordinator = true\n" +
                  "                pushStateWhenCoordinatorTimeout = 5000\n" +
                  "            </properties>\n" +
                  "         </singletonStore>\n" +
                  "      </loader>\n" +
                  "   </loaders>";
      clc = loadersElementParser.parseLoadersElement(strToElement(conf));
      mgr = new CacheLoaderManager();
      mgr.setConfig(clc, null, null);

      iclc = mgr.getCacheLoaderConfig().getFirstCacheLoaderConfig();
      assertNotNull("Singleton has been configured", iclc.getSingletonStoreConfig());
      assertTrue("Singleton should enabled", iclc.getSingletonStoreConfig().isSingletonStoreEnabled());
      assertEquals("Singleton class should be default", SingletonStoreCacheLoader.class.getName(), iclc.getSingletonStoreConfig().getSingletonStoreClass());
      assertNotNull("Singleton properties should not be defined", iclc.getSingletonStoreConfig().getSingletonStoreproperties());
      ssdc = ((SingletonStoreCacheLoader) mgr.getCacheLoader()).getSingletonStoreDefaultConfig();
      assertTrue("Singleton pushStateWhenCoordinator should be true", ssdc.isPushStateWhenCoordinator());
      assertEquals("Singleton pushStateWhenCoordinatorTimeout should be default value", 5000, ssdc.getPushStateWhenCoordinatorTimeout());

      /************************************************************************************************************/
      conf =
            "   <loaders passivation=\"false\" shared=\"true\">\n" +
                  "      <preload/>\n" +
                  "      <loader class=\"org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader\">\n" +
                  "         <singletonStore enabled=\"true\">\n" +
                  "            <properties>\n" +
                  "                pushStateWhenCoordinator = true\n" +
                  "                pushStateWhenCoordinatorTimeout = 5000\n" +
                  "            </properties>\n" +
                  "         </singletonStore>\n" +
                  "      </loader>\n" +
                  "   </loaders>";
      clc = loadersElementParser.parseLoadersElement(strToElement(conf));
      mgr = new CacheLoaderManager();
      try
      {
         mgr.setConfig(clc, null, null);
         fail("A cache loader cannot be configured as singleton and shared, should have thrown an Exception");
      }
      catch (Exception e)
      {
      }

      /************************************************************************************************************/

      conf =
            "   <loaders passivation=\"true\">\n" +
                  "      <preload/>\n" +
                  "      <loader class=\"org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader\">\n" +
                  "         <singletonStore enabled=\"true\" class=\"org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader\">\n" +
                  "         </singletonStore>\n" +
                  "      </loader>\n" +
                  "   </loaders>";
      clc = loadersElementParser.parseLoadersElement(strToElement(conf));
      mgr = new CacheLoaderManager();
      try
      {
         mgr.setConfig(clc, null, null);
         fail("A singleton store class implementation must extend AbstractDelegatingCacheLoader");
      }
      catch (Exception e)
      {
      }
   }

   private class MockCacheLoaderManager extends CacheLoaderManager
   {
      @Override
      protected void setCacheInLoader(CacheSPI c, CacheLoader loader)
      {
         /* do nothing */
      }
   }

   public static class MockSingletonStoreCacheLoader extends AbstractDelegatingCacheLoader
   {
      public MockSingletonStoreCacheLoader()
      {
         super(null);
      }
   }
}
