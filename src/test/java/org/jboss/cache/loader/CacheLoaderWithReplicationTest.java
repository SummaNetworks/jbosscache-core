/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * Tests using cache loaders with replicating data
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = "functional", testName = "loader.CacheLoaderWithReplicationTest")
public class CacheLoaderWithReplicationTest
{
   private Cache<Object, Object> cache1, cache2;
   private Fqn fqn = Fqn.fromString("/a");
   private String key = "key";

   private CacheLoader loader1, loader2;
   private TransactionManager mgr1, mgr2;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache1 = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache1.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyInMemoryCacheLoader.class.getName(),
            "debug=true", false, true, false, false, false));
      cache1.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());

      cache2 = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache2.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyInMemoryCacheLoader.class.getName(),
            "debug=true", false, true, false, false, false));
      cache2.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());

      loader1 = loader2 = null;
      mgr1 = mgr2 = null;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (cache1 != null)
      {
         try
         {
            try
            {
               if (mgr1 != null) mgr1.rollback();
            }
            catch (Exception e)
            {

            }

            try
            {
               loader1.remove(fqn);
               cache1.removeNode(fqn);
            }
            catch (Exception e)
            {

            }
            TestingUtil.killCaches(cache1);
         }
         finally
         {
            cache1 = null;
            mgr1 = null;
            loader1 = null;
         }
      }

      if (cache2 != null)
      {
         try
         {
            try
            {
               if (mgr2 != null) mgr2.rollback();
            }
            catch (Exception e)
            {

            }
            try
            {
               loader2.remove(fqn);
               cache2.removeNode(fqn);
            }
            catch (Exception e)
            {

            }
            TestingUtil.killCaches(cache2);
         }
         finally
         {
            cache2 = null;
            mgr2 = null;
            loader2 = null;
         }

      }
   }

   private void createCaches(boolean sync, boolean optimistic) throws Exception
   {
      cache1.getConfiguration().setCacheMode(sync ? Configuration.CacheMode.REPL_SYNC : Configuration.CacheMode.REPL_ASYNC);
      cache2.getConfiguration().setCacheMode(sync ? Configuration.CacheMode.REPL_SYNC : Configuration.CacheMode.REPL_ASYNC);
      if (sync)
      {
         cache1.getConfiguration().setSyncCommitPhase(true);
         cache2.getConfiguration().setSyncCommitPhase(true);
         cache1.getConfiguration().setSyncRollbackPhase(true);
         cache2.getConfiguration().setSyncRollbackPhase(true);
      }

      if (optimistic)
      {
         cache1.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
         cache2.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      }
      else
      {
         cache1.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
         cache2.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      }

      cache1.start();
      cache2.start();

      TestingUtil.blockUntilViewsReceived(10000, cache1, cache2);

      loader1 = ((CacheSPI<Object, Object>) cache1).getCacheLoaderManager().getCacheLoader();
      loader2 = ((CacheSPI<Object, Object>) cache2).getCacheLoaderManager().getCacheLoader();

      // make sure everything is empty...
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));
      assertNull(cache1.getRoot().getChild(fqn));
      assertNull(cache2.getRoot().getChild(fqn));

      mgr1 = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr2 = cache2.getConfiguration().getRuntimeConfig().getTransactionManager();
   }

   public void testPessSyncRepl() throws Exception
   {
      createCaches(true, false);

      mgr1.begin();
      cache1.put(fqn, key, "value");

      assertEquals("value", cache1.get(fqn, key));
      assertNull(cache2.get(fqn, key));
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));
      mgr1.commit();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

      mgr1.begin();
      cache1.put(fqn, key, "value2");

      assertEquals("value2", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

      mgr1.rollback();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

   }

   public void testPessAsyncRepl() throws Exception
   {
      createCaches(false, false);
      ReplicationListener replListener = ReplicationListener.getReplicationListener(cache2);

      mgr1.begin();
      cache1.put(fqn, key, "value");

      assertEquals("value", cache1.get(fqn, key));
      assertNull(cache2.get(fqn, key));
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));

      replListener.expectWithTx(PutKeyValueCommand.class);
      mgr1.commit();
      replListener.waitForReplicationToOccur();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

      mgr1.begin();
      cache1.put(fqn, key, "value2");

      assertEquals("value2", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

      mgr1.rollback();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));
   }

   public void testOptSyncRepl() throws Exception
   {
      createCaches(true, true);
      mgr1.begin();
      cache1.put(fqn, key, "value");

      assertEquals("value", cache1.get(fqn, key));
      assertNull(cache2.get(fqn, key));
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));
      mgr1.commit();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));

      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

      mgr1.begin();
      cache1.put(fqn, key, "value2");

      assertEquals("value2", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

      mgr1.rollback();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));
   }

   public void testOptAsyncRepl() throws Exception
   {
      createCaches(false, true);

      ReplicationListener replListener = ReplicationListener.getReplicationListener(cache2);

      mgr1.begin();
      replListener.expectWithTx(PutKeyValueCommand.class);
      cache1.put(fqn, key, "value");

      assertEquals("value", cache1.get(fqn, key));
      assertNull(cache2.get(fqn, key));
      assertNull(loader1.get(fqn));
      assertNull(loader2.get(fqn));

      mgr1.commit();

      replListener.waitForReplicationToOccur();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

      mgr1.begin();
      cache1.put(fqn, key, "value2");

      assertEquals("value2", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));

      mgr1.rollback();

      assertEquals("value", cache1.get(fqn, key));
      assertEquals("value", cache2.get(fqn, key));
      assertEquals("value", loader1.get(fqn).get(key));
      assertEquals("value", loader2.get(fqn).get(key));
   }

}
