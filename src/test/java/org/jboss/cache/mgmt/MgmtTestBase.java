package org.jboss.cache.mgmt;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import static org.jboss.cache.factories.UnitTestConfigurationFactory.buildSingleCacheLoaderConfig;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

@Test(groups = "functional", testName = "mgmt.MgmtTestBase")
public abstract class MgmtTestBase
{
   protected static final String CAPITAL = "capital";
   protected static final String CURRENCY = "currency";
   protected static final String POPULATION = "population";
   protected static final String AREA = "area";
   protected static final Fqn EUROPE = Fqn.fromString("/Europe");
   protected static final Fqn AUSTRIA = Fqn.fromString("/Europe/Austria");
   protected static final Fqn ENGLAND = Fqn.fromString("/Europe/England");
   protected static final Fqn ALBANIA = Fqn.fromString("/Europe/Albania");
   protected static final Fqn HUNGARY = Fqn.fromString("/Europe/Hungary");
   protected static final Fqn POLAND = Fqn.fromString("/Europe/Poland");

   protected boolean passivation = false;

   protected CacheSPI<String, Object> cache = null;
   protected CacheLoader cl;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = createCache();
      // populate cache with test data
      loadCache();
      cl = cache.getCacheLoaderManager().getCacheLoader();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   /**
    * Should trigger 2 cache load events
    */
   protected void loadCache() throws Exception
   {
      cache.put(EUROPE, null);

      Map<String, Object> austria = new HashMap<String, Object>();
      austria.put(CAPITAL, "VIENNA");
      austria.put(CURRENCY, "Euro");
      austria.put(POPULATION, 8184691);
      cache.put(AUSTRIA, austria);

      Map<String, Object> england = new HashMap<String, Object>();
      england.put(CAPITAL, "London");
      england.put(CURRENCY, "British Pound");
      england.put(POPULATION, 60441457);
      cache.put(ENGLAND, england);

      Map<String, Object> albania = new HashMap<String, Object>(4);
      albania.put(CAPITAL, "Tirana");
      albania.put(CURRENCY, "Lek");
      albania.put(POPULATION, 3563112);
      albania.put(AREA, 28748);
      cache.put(ALBANIA, albania);

      Map<String, Object> hungary = new HashMap<String, Object>(4);
      hungary.put(CAPITAL, "Budapest");
      hungary.put(CURRENCY, "Forint");
      hungary.put(POPULATION, 10006835);
      hungary.put(AREA, 93030);
      cache.put(HUNGARY, hungary);
   }

   private CacheSPI<String, Object> createCache() throws Exception
   {
      UnitTestCacheFactory<String, Object> instance = new UnitTestCacheFactory<String, Object>();
      Configuration c = new Configuration();
      c.setNodeLockingScheme(NodeLockingScheme.MVCC);
      c.setCacheMode(Configuration.CacheMode.LOCAL);
      c.setCacheLoaderConfig(getCacheLoaderConfig());
      c.setExposeManagementStatistics(true);

      CacheSPI<String, Object> cache = (CacheSPI<String, Object>) instance.createCache(c, false, getClass());
      cache.create();
      cache.start();
      return cache;
   }

   private CacheLoaderConfig getCacheLoaderConfig() throws Exception
   {
      return buildSingleCacheLoaderConfig(passivation, null, DummyInMemoryCacheLoader.class.getName(),
            "debug=true", false, false, false, false, false);
   }
}
