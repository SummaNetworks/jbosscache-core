package org.jboss.cache.factories;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.interceptors.OptimisticLockingInterceptor;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.marshall.AbstractMarshaller;
import org.jboss.cache.marshall.VersionAwareMarshaller;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", sequential = true, testName = "factories.LateConfigurationTest")
public class LateConfigurationTest
{
   CacheSPI c;

   @BeforeMethod
   public void setUp()
   {
      c = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(c);
      c = null;
   }

   public void testTransactionManager()
   {
      assert c.getTransactionManager() == null;

      c.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());

      assert c.getTransactionManager() == null;
      c.start();
      assert c.getTransactionManager() == DummyTransactionManager.getInstance();
   }

   public void testTransactionManagerinRuntime()
   {
      assert c.getTransactionManager() == null;

      c.getConfiguration().getRuntimeConfig().setTransactionManager(DummyTransactionManager.getInstance());

      assert c.getTransactionManager() == null;
      c.start();
      assert c.getTransactionManager() == DummyTransactionManager.getInstance();
   }


   public void testCacheLoader()
   {
      assert c.getCacheLoaderManager() != null;
      assert c.getCacheLoaderManager().getCacheLoader() == null;

      CacheLoaderConfig clc = new CacheLoaderConfig();
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      iclc.setCacheLoader(new DummyInMemoryCacheLoader());
      clc.addIndividualCacheLoaderConfig(iclc);
      c.getConfiguration().setCacheLoaderConfig(clc);

      c.start();
      assert c.getCacheLoaderManager() != null;
      assert c.getCacheLoaderManager().getCacheLoader() instanceof DummyInMemoryCacheLoader;
   }

   public void testBuddyManagerLocal()
   {
      // leaving cache mode as local
      assert c.getBuddyManager() == null;

      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      c.getConfiguration().setBuddyReplicationConfig(brc);

      c.start();
      assert c.getBuddyManager() == null;
   }

   public void testBuddyManager()
   {
      // we need to not be LOCAL if we want things to work with BR!
      c.getConfiguration().setCacheMode(Configuration.CacheMode.REPL_SYNC);
      assert c.getBuddyManager() == null;

      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      c.getConfiguration().setBuddyReplicationConfig(brc);

      c.start();
      assert c.getBuddyManager() != null;
      assert c.getBuddyManager().isEnabled();
   }


   public void testInterceptors()
   {
      assert TestingUtil.findInterceptor(c, OptimisticLockingInterceptor.class) == null;

      c.getConfiguration().setNodeLockingScheme("OPTIMISTIC");
      assert TestingUtil.findInterceptor(c, OptimisticLockingInterceptor.class) == null;
      c.start();
      assert TestingUtil.findInterceptor(c, OptimisticLockingInterceptor.class) != null;
   }

   public void testCacheMarshaller()
   {
      assert c.getMarshaller() instanceof VersionAwareMarshaller;

      c.getConfiguration().setCacheMarshaller(new AbstractMarshaller()
      {
         public void objectToObjectStream(Object obj, ObjectOutputStream out) throws Exception
         {
         }

         public Object objectFromObjectStream(ObjectInputStream in) throws Exception
         {
            return null;
         }

         public void objectToObjectStream(Object obj, ObjectOutputStream out, Fqn region)
         {
         }
      });

      c.start();

      assert !(c.getMarshaller() instanceof VersionAwareMarshaller) && c.getMarshaller() != null;
   }
}
