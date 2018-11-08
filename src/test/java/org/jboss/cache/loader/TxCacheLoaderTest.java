package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Set;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.AbstractMultipleCachesTest;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.Configuration;

/**
 * @author Bela Ban
 * @version $Id: TxCacheLoaderTest.java 7422 2009-01-09 09:21:18Z mircea.markus $
 */
@Test(groups = {"functional", "transaction"}, sequential = true, testName = "loader.TxCacheLoaderTest")
public class TxCacheLoaderTest extends AbstractMultipleCachesTest
{
   CacheSPI<Object, Object> cache1, cache2;
   private Fqn fqn = Fqn.fromString("/one/two/three");


   protected void createCaches() throws Throwable
   {
      Configuration c1 = new Configuration();
      c1.setCacheMode("repl_sync");
      c1.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      c1.setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      c1.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyInMemoryCacheLoader.class.getName(),
            "", false, false, false, false, false));
      
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c1, false, getClass());
      cache1.create();
      cache1.start();

      Configuration c2 = new Configuration();
      c2.setCacheMode("repl_sync");
      c2.setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      c2.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      c2.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyInMemoryCacheLoader.class.getName(),
            "", false, false, false, false, false));
      c2.setLockAcquisitionTimeout(2000);
      // cache2.setReplQueueInterval(3000);

      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c2, false, getClass());
      cache2.create();
      cache2.start();

      registerCaches(cache1, cache2);
   }

   @AfterMethod
   public void tearDown() throws Exception
   {
      // clean up cache loaders!!
      cache1.removeNode(Fqn.ROOT);
   }


   public void testTxPutCommit() throws Exception
   {
      TransactionManager mgr = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();


      cache1.put(fqn, "key1", "val1");
      cache1.put("/one/two/three/four", "key2", "val2");
      assertNull(cache2.get(fqn, "key1"));
      assertNull(cache2.get("/one/two/three/four", "key2"));
      mgr.commit();
      assertNotNull(cache1.getNode(fqn).getKeys());
      Set<?> children = cache1.getNode("/one").getChildrenNames();
      assertEquals(1, children.size());
      TestingUtil.sleepThread(2000);
      assertEquals("val1", cache2.get(fqn, "key1"));
      assertEquals("val2", cache2.get("/one/two/three/four", "key2"));
   }


   public void testTxPrepareAndRollback() throws Exception
   {
      final TransactionManager mgr = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();

      cache1.getConfiguration().setLockAcquisitionTimeout(1500);
      cache2.getConfiguration().setLockAcquisitionTimeout(1500);


      Thread locker = new Thread()
      {
         Transaction tx2 = null;

         public void run()
         {
            try
            {
               mgr.begin();
               tx2 = mgr.getTransaction();
               cache2.put(fqn, "block-key1", "block-val1");// acquires a lock on cache2./one/two/three
               TestingUtil.sleepThread(5000);
            }
            catch (Exception e)
            {
               e.printStackTrace();
            }
            finally
            {
               if (tx2 != null)
               {
                  try
                  {
                     mgr.rollback();
                  }
                  catch (SystemException e)
                  {
                     e.printStackTrace();
                  }
               }
            }
         }
      };

      locker.start();
      TestingUtil.sleepThread(1000);

      cache1.put(fqn, "key1", "val1");
      cache1.put("/one/two/three/four", "key2", "val2");

      try
      {
         mgr.commit();// prepare() on cache2 will fail due to lock held by locker thread
         fail("commit() should fail because we cannot acquire the lock on cache2");
      }
      catch (RollbackException rollback)
      {
         assertTrue(true);
      }

      assertNull(cache1.get(fqn, "key1"));
      assertNull(cache1.get("/one/two/three/four", "key1"));

   }


   public void testPutAfterTxCommit() throws Exception
   {
      TransactionManager mgr = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();

      cache1.put(fqn, "key1", "val1");
      assertTrue(cache1.exists(fqn));
      mgr.commit();
      assertTrue(cache1.exists(fqn));
      cache1.put("/a/b/c", null);// should be run outside a TX !
      assertTrue(cache1.exists("/a/b/c"));
   }

   public void testPutAfterTxRollback() throws Exception
   {
      TransactionManager mgr = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();

      cache1.put(fqn, "key1", "val1");
      assertTrue(cache1.exists(fqn));
      mgr.rollback();
      assertFalse(cache1.getCacheLoaderManager().getCacheLoader().exists(fqn));
      assertFalse(cache1.exists(fqn));
      cache1.put("/a/b/c", null);// should be run outside a TX !
      assertTrue(cache1.exists("/a/b/c"));
   }

}
