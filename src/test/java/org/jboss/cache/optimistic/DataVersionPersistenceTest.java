package org.jboss.cache.optimistic;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;

/**
 * Tests whether data versions are transferred along with state
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.DataVersionPersistenceTest")
public class DataVersionPersistenceTest
{
   private Cache cache;
   private CacheLoader loader;

   @BeforeMethod
   public void setUp() throws IOException
   {

      cache = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);

      CacheLoaderConfig clc = new CacheLoaderConfig();
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      iclc.setProperties("debug=true \n bin=" + getClass());
      iclc.setClassName(DummySharedInMemoryCacheLoader.class.getName());
      clc.addIndividualCacheLoaderConfig(iclc);

      cache.getConfiguration().setCacheLoaderConfig(clc);

      cache.start();

      loader = ((CacheSPI) cache).getCacheLoaderManager().getCacheLoader();
   }

   @AfterMethod
   public void tearDown()
   {
      try
      {
         cache.getConfiguration().getRuntimeConfig().getTransactionManager().rollback();
      }
      catch (Exception e)
      {
         // do nothing?
      }
      ((DummySharedInMemoryCacheLoader) loader).wipeBin();
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testStateTransferDefaultVersions() throws Exception
   {
      Fqn f = Fqn.fromString("/one/two/three");
      cache.put(f, "k", "v");


      cache.put(f, "k1", "v1");
      cache.remove(f, "k1");

      assert loader.get(f).containsKey("_JBOSS_INTERNAL_OPTIMISTIC_DATA_VERSION");
      assert ((DefaultDataVersion) loader.get(f).get("_JBOSS_INTERNAL_OPTIMISTIC_DATA_VERSION")).getRawVersion() == 3;

      NodeSPI n = (NodeSPI) cache.getRoot().getChild(f);
      DataVersion dv = n.getVersion();

      assert dv instanceof DefaultDataVersion : "Should be an instance of DefaultDataVersion";

      assert ((DefaultDataVersion) dv).getRawVersion() == 3 : "Should have accurate data version";

      // now restart cache instance
      cache.stop();
      cache.start();

      assert cache.get(f, "k").equals("v") : "Value should have peristed";

      n = (NodeSPI) cache.getRoot().getChild(f);

      dv = n.getVersion();

      assert dv instanceof DefaultDataVersion : "Should be an instance of DefaultDataVersion";

      assert ((DefaultDataVersion) dv).getRawVersion() == 3 : "Version should have peristed";
      // make sure leakage doesn't occur into data map
      assert n.getData().size() == 1;
   }

   public void testStateTransferCustomVersion() throws Exception
   {
      Fqn f = Fqn.fromString("/one/two/three");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(new CharVersion('A'));
      cache.put(f, "k", "v");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(new CharVersion('B'));
      cache.put(f, "k1", "v1");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(new CharVersion('C'));
      cache.remove(f, "k1");

      NodeSPI n = (NodeSPI) cache.getRoot().getChild(f);
      DataVersion dv = n.getVersion();

      assert dv instanceof CharVersion : "Should be an instance of CharVersion";

      assert ((CharVersion) dv).version == 'C' : "Should have accurate data version";

      // now restart cache instance
      cache.stop();
      cache.start();

      assert cache.get(f, "k").equals("v") : "Value should have peristed";

      n = (NodeSPI) cache.getRoot().getChild(f);

      dv = n.getVersion();

      assert dv instanceof CharVersion : "Should be an instance of CharVersion";

      assert ((CharVersion) dv).version == 'C' : "Version should have peristed";

      // make sure leakage doesn't occur into data map
      assert n.getData().size() == 1;
   }

   public static class CharVersion implements DataVersion
   {
      private char version = 'A';

      public CharVersion(char version)
      {
         this.version = version;
      }

      public boolean newerThan(DataVersion other)
      {
         if (other instanceof CharVersion)
         {
            CharVersion otherVersion = (CharVersion) other;
            return version > otherVersion.version;
         }
         else
         {
            return true;
         }
      }
   }

}
