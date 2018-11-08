/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.replicated;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.naming.Context;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import java.io.NotSerializableException;
import java.io.Serializable;

/**
 * Teting of replication exception for a Nonerislizable object
 *
 * @author Ben Wang
 * @version $Revision: 7451 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "replicated.ReplicationExceptionTest")
public class ReplicationExceptionTest
{
   private CacheSPI<String, ContainerData> cache1, cache2;

   //String old_factory = null;
   final String FACTORY = "org.jboss.cache.transaction.DummyContextFactory";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      //old_factory = System.getProperty(Context.INITIAL_CONTEXT_FACTORY);
      System.setProperty(Context.INITIAL_CONTEXT_FACTORY, FACTORY);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      // We just can't kill DummyTransactionManager. We are sharing single instance in more tests. 
      TestingUtil.killTransaction(DummyTransactionManager.getInstance());
      destroyCaches();
      /*
      if (old_factory != null)
      {
         System.setProperty(Context.INITIAL_CONTEXT_FACTORY, old_factory);
         old_factory = null;
      }
      */ 
   }

   private TransactionManager beginTransaction() throws SystemException, NotSupportedException
   {
      TransactionManager mgr = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();
      return mgr;
   }

   private void initCaches(Configuration.CacheMode caching_mode)
   {
      UnitTestCacheFactory<String, ContainerData> instance = new UnitTestCacheFactory<String, ContainerData>();
      Configuration conf1 = new Configuration();
      Configuration conf2 = new Configuration();
      
      conf1.setCacheMode(caching_mode);
      conf2.setCacheMode(caching_mode);
      conf1.setIsolationLevel(IsolationLevel.SERIALIZABLE);
      conf2.setIsolationLevel(IsolationLevel.SERIALIZABLE);

      conf1.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      conf2.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      /*
       cache1.setTransactionManagerLookupClass("org.jboss.cache.transaction.GenericTransactionManagerLookup");
       cache2.setTransactionManagerLookupClass("org.jboss.cache.transaction.GenericTransactionManagerLookup");
       */
      conf1.setLockAcquisitionTimeout(5000);
      conf2.setLockAcquisitionTimeout(5000);
      
      cache1 = (CacheSPI<String, ContainerData>) instance.createCache(conf1, false, getClass());
      cache2 = (CacheSPI<String, ContainerData>) instance.createCache(conf2, false, getClass());
      
      
      cache1.start();
      cache2.start();
   }

   void destroyCaches() throws Exception
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   public void testNonSerializableRepl() throws Exception
   {
      try
      {
         initCaches(Configuration.CacheMode.REPL_SYNC);

         cache1.put("/a/b/c", "test", new ContainerData());

         // We should not come here.
         assertNotNull("NonSerializableData should not be null on cache2", cache2.get("/a/b/c", "test"));
      }
      catch (RuntimeException runtime)
      {
         Throwable t = runtime.getCause();
         if (! (t instanceof NotSerializableException))
         {
            throw runtime;
         }
      }
   }

   public void testNonSerializableReplWithTx() throws Exception
   {
      TransactionManager tm;

      try
      {
         initCaches(Configuration.CacheMode.REPL_SYNC);

         tm = beginTransaction();
         cache1.put("/a/b/c", "test", new ContainerData());
         tm.commit();

         // We should not come here.
         assertNotNull("NonSerializableData should not be null on cache2", cache2.get("/a/b/c", "test"));
      }
      catch (RollbackException rollback)
      {
      }
      catch (Exception e)
      {
         // We should also examine that it is indeed throwing a NonSerilaizable exception.
         fail(e.toString());
      }
   }

   static class NonSerializabeData
   {
      int i;
   }

   static class ContainerData implements Serializable
   {
      int i;
      NonSerializabeData non_serializable_data;
      private static final long serialVersionUID = -8322197791060897247L;

      public ContainerData()
      {
         i = 99;
         non_serializable_data = new NonSerializabeData();
      }
   }
}
