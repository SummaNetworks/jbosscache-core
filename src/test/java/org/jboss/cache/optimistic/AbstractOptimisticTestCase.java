/**
 *
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.VersionedDataCommand;
import org.jboss.cache.commands.WriteCommand;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.interceptors.CacheMgmtInterceptor;
import org.jboss.cache.interceptors.CallInterceptor;
import org.jboss.cache.interceptors.NotificationInterceptor;
import org.jboss.cache.interceptors.OptimisticLockingInterceptor;
import org.jboss.cache.interceptors.OptimisticNodeInterceptor;
import org.jboss.cache.interceptors.OptimisticValidatorInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.marshall.MethodCall;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.Address;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * @author manik
 */
@Test(groups = {"functional", "optimistic"}, testName = "optimistic.AbstractOptimisticTestCase")
public class AbstractOptimisticTestCase
{
   // some test data shared among all the test cases
   protected Fqn fqn = Fqn.fromString("/blah");
   protected String key = "myKey", value = "myValue";

   protected CacheSPI<Object, Object> createCacheUnstarted() throws Exception
   {
      return createCacheUnstarted(true);
   }

   protected CacheSPI<Object, Object> createCacheUnstarted(boolean optimistic) throws Exception
   {

      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL), false, getClass());
      if (optimistic) cache.getConfiguration().setNodeLockingScheme("OPTIMISTIC");
      return cache;
   }

   protected CacheSPI<Object, Object> createCacheWithListener() throws Exception
   {
      return createCacheWithListener(new TestListener());
   }

   protected CacheSPI<Object, Object> createCacheWithListener(Object listener) throws Exception
   {
      CacheSPI<Object, Object> cache = createCacheUnstarted();
      cache.create();
      cache.start();
      cache.getNotifier().addCacheListener(listener);
      return cache;
   }

   /**
    * Returns a tree cache with passivation disabled in the loader.
    */
   protected CacheSPI<Object, Object> createCacheWithLoader() throws Exception
   {
      return createCacheWithLoader(false);
   }

   protected void setupTransactions(CacheSPI cache, Transaction tx)
   {
      cache.getInvocationContext().setTransaction(tx);
      GlobalTransaction gtx = cache.getCurrentTransaction(tx, true);
      cache.getInvocationContext().setGlobalTransaction(gtx);
      cache.getInvocationContext().setTransactionContext(cache.getTransactionTable().get(gtx));
   }

   protected CacheLoaderConfig getCacheLoaderConfig(boolean shared, boolean passivation) throws Exception
   {
      String cacheLoaderClass = shared ? DummySharedInMemoryCacheLoader.class.getName() : DummyInMemoryCacheLoader.class.getName();
      String props = "";
      if (shared)
      {
         props = "bin = " + getClass().getName();
      }
      return UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(passivation, null, cacheLoaderClass, props, false, (!shared), shared, false, false);
   }

   protected CacheSPI<Object, Object> createCacheWithLoader(boolean passivationEnabled) throws Exception
   {
      CacheSPI<Object, Object> cache = createCacheUnstarted();
      Configuration c = cache.getConfiguration();
      c.setCacheLoaderConfig(getCacheLoaderConfig(true, passivationEnabled));
      cache.create();
      cache.start();
      return cache;
   }


   protected CacheSPI<Object, Object> createCache() throws Exception
   {
      CacheSPI<Object, Object> cache = createCacheUnstarted();
      cache.create();
      cache.start();
      return cache;
   }


  protected CacheSPI createPessimisticCache() throws Exception
   {
      Configuration c = new Configuration();
      c.setClusterName("name");
      c.setStateRetrievalTimeout(5000);
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");

      CacheSPI cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      cache.create();
      cache.start();


      return cache;
   }

   protected CacheSPI createPessimisticCacheLocal() throws Exception
   {
      Configuration c = new Configuration();
      c.setClusterName("name");
      c.setStateRetrievalTimeout(5000);

      c.setCacheMode(Configuration.CacheMode.LOCAL);
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");

      CacheSPI cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.create();
      cache.start();


      return cache;
   }

   protected CacheSPI<Object, Object> createReplicatedCache(Configuration.CacheMode mode) throws Exception
   {
      return createReplicatedCache("test", mode);
   }

   protected CacheSPI<Object, Object> createReplicatedCache(String name, Configuration.CacheMode mode) throws Exception
   {
      return createReplicatedCache(name, mode, true);
   }

   protected CacheSPI<Object, Object> createReplicatedCache(String name, Configuration.CacheMode mode, boolean start) throws Exception
   {
      Configuration c = new Configuration();

      c.setClusterName(name);
      c.setStateRetrievalTimeout(5000);
      c.setCacheMode(mode);
      if (mode == Configuration.CacheMode.REPL_SYNC)
      {
         // make sure commits and rollbacks are sync as well
         c.setSyncCommitPhase(true);
         c.setSyncRollbackPhase(true);
      }
      c.setNodeLockingScheme("OPTIMISTIC");
      c.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());

      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      cache.getConfiguration().setSerializationExecutorPoolSize(0);
      
      if (start)
      {
         cache.create();
         cache.start();
      }

      return cache;
   }

   protected CacheSPI createReplicatedCacheWithLoader(boolean shared, Configuration.CacheMode cacheMode) throws Exception
   {
      return createReplicatedCacheWithLoader("temp-loader", shared, cacheMode);
   }

   protected CacheSPI<Object, Object> createReplicatedCacheWithLoader(boolean shared) throws Exception
   {
      return createReplicatedCacheWithLoader("temp-loader", shared, Configuration.CacheMode.REPL_SYNC);
   }

   protected CacheSPI createReplicatedCacheWithLoader(String name, boolean shared) throws Exception
   {
      return createReplicatedCacheWithLoader(name, shared, Configuration.CacheMode.REPL_SYNC);
   }

   protected CacheSPI<Object, Object> createReplicatedCacheWithLoader(String name, boolean shared, Configuration.CacheMode cacheMode) throws Exception
   {
      Configuration c = new Configuration();
      c.setClusterName(name);
      c.setStateRetrievalTimeout(5000);
      c.setCacheMode(cacheMode);
      c.setSyncCommitPhase(true);
      c.setSyncRollbackPhase(true);
      c.setNodeLockingScheme("OPTIMISTIC");
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      c.setCacheLoaderConfig(getCacheLoaderConfig(shared, false));
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      cache.create();
      cache.start();
      return cache;
   }

   protected Random random = new Random();

   protected void randomSleep(int min, int max)
   {
      long l = -1;
      while (l < min) l = random.nextInt(max);
      TestingUtil.sleepThread(l);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TransactionManager mgr = TransactionSetup.getManager();
      try
      {
         if (mgr.getTransaction() != null)
         {
            mgr.rollback();
         }
      }
      catch (SystemException e)
      {
         // do nothing
      }
      
      new UnitTestCacheFactory().cleanUp();
      
   }

   protected void setAlteredInterceptorChain(CommandInterceptor newLast, CacheSPI<Object, Object> spi)
   {
      spi.removeInterceptor(CacheMgmtInterceptor.class);
      spi.removeInterceptor(NotificationInterceptor.class);
      spi.removeInterceptor(OptimisticLockingInterceptor.class);
      spi.removeInterceptor(OptimisticValidatorInterceptor.class);
      spi.removeInterceptor(CallInterceptor.class);
      spi.addInterceptor(newLast, OptimisticNodeInterceptor.class);
   }

   public abstract class ExceptionThread extends Thread
   {
      protected Exception exception;

      public void setException(Exception e)
      {
         exception = e;
      }

      public Exception getException()
      {
         return exception;
      }
   }

   protected List<WriteCommand> injectDataVersion(List<WriteCommand> modifications)
   {
      List<MethodCall> newList = new LinkedList<MethodCall>();
      for (WriteCommand c : modifications)
      {
         if (c instanceof VersionedDataCommand)
         {
            ((VersionedDataCommand) c).setDataVersion(new DefaultDataVersion());
         }
//         Object[] oa = c.getArgs();
//         Object[] na = new Object[oa.length + 1];
//         System.arraycopy(oa, 0, na, 0, oa.length);
//         na[oa.length] = new DefaultDataVersion();
//         newList.add(MethodCallFactory.create(MethodDeclarations.getVersionedMethodId(c.getMethodId()), na));
      }
      return modifications;
   }

   protected class DummyAddress implements Address
   {
      private static final long serialVersionUID = -2628268587640985944L;

      public int compareTo(Address arg0)
      {
         return 0;
      }

      public int compareTo(Object o)
      {
         return 0;
      }

      public void readFrom(DataInputStream
            arg0)
      {
      }

      public void writeTo(DataOutputStream
            arg0)
      {
      }

      public void readExternal(ObjectInput
            arg0)
      {
      }

      public void writeExternal(ObjectOutput
            arg0)
      {
      }

      public int size()
      {
         return 0;
      }

      public boolean isMulticastAddress()
      {
         return false;
      }


   }

}
