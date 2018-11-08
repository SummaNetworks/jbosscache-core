/*
* JBoss, Home of Professional Open Source
* Copyright 2006, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/

package org.jboss.cache.statetransfer;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.FileCacheLoader;
import org.jboss.cache.marshall.SelectedClassnameClassLoader;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.UnitTestDatabaseManager;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Abstract superclass of the StateTransfer tests.
 *
 * @author <a href="mailto://brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7646 $
 */
@Test(groups = {"functional"}, testName = "statetransfer.StateTransferTestBase")
public abstract class StateTransferTestBase
{
   protected static final int SUBTREE_SIZE = 10;

   public static final Fqn A = Fqn.fromString("/a");
   public static final Fqn B = Fqn.fromString("/b");
   public static final Fqn C = Fqn.fromString("/c");

   protected static final String ADDRESS_CLASSNAME = "org.jboss.cache.marshall.data.Address";
   protected static final String PERSON_CLASSNAME = "org.jboss.cache.marshall.data.Person";

   public static final Fqn A_B = Fqn.fromString("/a/b");
   public static final Fqn A_C = Fqn.fromString("/a/c");
   public static final Fqn A_D = Fqn.fromString("/a/d");

   public static final String JOE = "JOE";
   public static final String BOB = "BOB";
   public static final String JANE = "JANE";
   public static final Integer TWENTY = 20;
   public static final Integer FORTY = 40;

   protected Map<String, Cache> caches = new HashMap<String, Cache>();
   ClassLoader orig_TCL;
   private static int cacheCount = 0;


   protected abstract String getReplicationVersion();

   protected CacheSPI<Object, Object> createCache(boolean sync, boolean useMarshalling, boolean useCacheLoader)
         throws Exception
   {
      return createCache(sync, useMarshalling, useCacheLoader, false, true, true);
   }

   protected CacheSPI<Object, Object> createCache(boolean sync, boolean useMarshalling, boolean useCacheLoader, boolean fetchPersistentState)
         throws Exception
   {
      return createCache(sync, useMarshalling, useCacheLoader, false, true, fetchPersistentState);
   }

   protected CacheSPI<Object, Object> createCache(boolean sync, boolean useMarshalling, boolean useCacheLoader,
                                                  boolean cacheLoaderAsync, boolean startCache, boolean fetchPersistentState)
         throws Exception
   {
      if (useCacheLoader)
      {
         return createCache(sync, useMarshalling, getDefaultCacheLoader(), cacheLoaderAsync, startCache, fetchPersistentState);
      }
      else
      {
         return createCache(sync, useMarshalling, null, cacheLoaderAsync, startCache, fetchPersistentState);
      }
   }


   protected CacheSPI<Object, Object> createCache(boolean sync, boolean useMarshalling, String cacheLoaderClass,
                                                  boolean cacheLoaderAsync, boolean startCache, boolean fetchPersistentState) throws Exception
   {
      String cacheID = getNextUniqueCacheName();
      if (caches.get(cacheID) != null)
      {
         throw new IllegalStateException(cacheID + " already created");
      }

      CacheMode mode = sync ? CacheMode.REPL_SYNC : CacheMode.REPL_ASYNC;
      Configuration c = UnitTestConfigurationFactory.createConfiguration(mode);

      if (sync)
      {
         c.setSyncRollbackPhase(true);
         c.setSyncCommitPhase(true);
      }
      c.setReplVersionString(getReplicationVersion());
      // Use a long timeout to facilitate setting debugger breakpoints
      c.setStateRetrievalTimeout(60000);
      if (useMarshalling)
      {
         c.setUseRegionBasedMarshalling(true);
         c.setInactiveOnStartup(true);
      }
      if (cacheLoaderClass != null && cacheLoaderClass.length() > 0)
      {
         configureCacheLoader(c, cacheLoaderClass, cacheID, cacheLoaderAsync, fetchPersistentState);
      }

      additionalConfiguration(c);
      CacheSPI<Object, Object> tree = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      configureMultiplexer(tree);

      // Put the cache in the map before starting, so if it fails in
      // start it can still be destroyed later
      caches.put(cacheID, tree);

      if (startCache)
      {
         tree.start();
      }

      return tree;
   }

   protected void additionalConfiguration(Configuration c)
   {
      // to be overridden
      c.setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
   }

   protected void createAndActivateRegion(CacheSPI<Object, Object> c, Fqn f)
   {
      Region r = c.getRegion(f, true);
      r.registerContextClassLoader(getClass().getClassLoader());
      r.activate();
   }

   /**
    * Provides a hook for multiplexer integration. This default implementation
    * is a no-op; subclasses that test mux integration would override
    * to integrate the given cache with a multiplexer.
    * <p/>
    * param cache a cache that has been configured but not yet created.
    */
   protected void configureMultiplexer(Cache cache) throws Exception
   {
      // default does nothing
   }

   /**
    * Provides a hook to check that the cache's channel came from the
    * multiplexer, or not, as expected.  This default impl asserts that
    * the channel did not come from the multiplexer.
    *
    * @param cache a cache that has already been started
    */
   protected void validateMultiplexer(Cache cache)
   {
      assertFalse("Cache is not using multiplexer", cache.getConfiguration().isUsingMultiplexer());
   }

   protected void startCache(Cache cache) throws Exception
   {
      cache.create();
      cache.start();

      validateMultiplexer(cache);
   }

   protected void configureCacheLoader(Configuration c, String cacheID, boolean async) throws Exception
   {
      configureCacheLoader(c, getDefaultCacheLoader(), cacheID, async, true);
   }

   protected void configureCacheLoader(Configuration c, String cacheloaderClass, String cacheID,
                                       boolean async, boolean fetchPersistentState) throws Exception
   {
      if (cacheloaderClass != null)
      {
         if (cacheloaderClass.equals("org.jboss.cache.loader.JDBCCacheLoader"))
         {
            Properties prop = UnitTestDatabaseManager.getTestDbProperties();
            CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.JDBCCacheLoader",
                  prop, false, true, false, false, false);
            clc.getFirstCacheLoaderConfig().setPurgeOnStartup(true);
            c.setCacheLoaderConfig(clc);
         } else if (cacheloaderClass.equals(FileCacheLoader.class.getName()))
         {
            String tmpLocation = getTempLocation(cacheID);
            File file = new File(tmpLocation);
            cleanFile(file);
            file.mkdir();
            tmpLocation = escapeWindowsPath(tmpLocation);
            String props = "location = " + tmpLocation + "\n";
            c.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", cacheloaderClass,
                  props, async, fetchPersistentState, false, false, false));
         }
         else
         {
            assert !cacheloaderClass.equals(FileCacheLoader.class.getName());
            c.setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", cacheloaderClass,
                  "", async, fetchPersistentState, false, false, false));
         }
      }
   }

   protected void initialStateTferWithLoaderTest(String cacheLoaderClass1, String cacheLoaderClass2, boolean asyncLoader) throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCache(false, false, cacheLoaderClass1, false, true, true);

      cache1.put(A_B, "name", JOE);
      cache1.put(A_B, "age", TWENTY);
      cache1.put(A_C, "name", BOB);
      cache1.put(A_C, "age", FORTY);

      CacheSPI<Object, Object> cache2 = createCache(false, false, cacheLoaderClass2, asyncLoader, true, true);

      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{cache1, cache2}, 60000);

      if (asyncLoader) TestingUtil.sleepThread(100);

      CacheLoader loader = cache2.getCacheLoaderManager().getCacheLoader();

      long start = System.currentTimeMillis();
      while (asyncLoader && System.currentTimeMillis() - start < 10000)
      {
         try
         {
            doAssertion(cache2, loader);
            break;
         } catch (Throwable ae)
         {
            //allow this within the timeout
         }
      }
      doAssertion(cache2, loader);
   }

   private void doAssertion(CacheSPI<Object, Object> cache2, CacheLoader loader)
         throws Exception
   {
      assertEquals("Incorrect loader name for /a/b", JOE, loader.get(A_B).get("name"));
      assertEquals("Incorrect loader age for /a/b", TWENTY, loader.get(A_B).get("age"));
      assertEquals("Incorrect loader name for /a/c", BOB, loader.get(A_C).get("name"));
      assertEquals("Incorrect loader age for /a/c", FORTY, loader.get(A_C).get("age"));

      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache2.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache2.get(A_C, "age"));
   }

   protected String getTempLocation(String cacheID)
   {
      String tmp_location = TestingUtil.TEST_FILES;
      File file = new File(tmp_location);
      file = new File(file, cacheID);
      return file.getAbsolutePath();
   }

   protected String escapeWindowsPath(String path)
   {
      if ('/' == File.separatorChar)
      {
         return path;
      }

      char[] chars = path.toCharArray();
      StringBuilder sb = new StringBuilder();
      for (char aChar : chars)
      {
         if (aChar == '\\')
         {
            sb.append('\\');
         }
         sb.append(aChar);
      }
      return sb.toString();
   }

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {caches = new HashMap<String, Cache>();

      // Save the TCL in case a test changes it
       orig_TCL = Thread.currentThread().getContextClassLoader();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      // Restore the TCL in case a test changed it
      Thread.currentThread().setContextClassLoader(orig_TCL);

      for (String cacheID : caches.keySet())
      {
         try
         {
            stopCache(caches.get(cacheID));
//            TestingUtil.sleepThread(1500);
            File file = new File(getTempLocation(cacheID));
            cleanFile(file);
         }
         catch (Exception e)
         {
            // errors in teardown should not fail test
         }
      }

      // repeat.  Make sure everything is properly STOPPED!!!

      for (Cache c : caches.values())
      {
         TestingUtil.killCaches(c);
      }
   }

   protected void stopCache(Cache cache)
   {
      if (cache != null)
      {
         try
         {
            TestingUtil.killCaches(cache);
         }
         catch (Exception e)
         {
            e.printStackTrace(System.out);
         }
      }
   }

   protected void cleanFile(File file)
   {
      File[] children = file.listFiles();
      if (children != null)
      {
         for (File child : children)
         {
            cleanFile(child);
         }
      }

      if (file.exists())
      {
         file.delete();
      }
      if (file.exists())
      {
         file.deleteOnExit();
      }
   }

   protected ClassLoader getClassLoader() throws Exception
   {
      String[] includesClasses = {"org.jboss.cache.marshall.Person",
            "org.jboss.cache.marshall.Address"};
      String[] excludesClasses = {};
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      return new SelectedClassnameClassLoader(includesClasses, excludesClasses, cl);
   }

   protected ClassLoader getNotFoundClassLoader() throws Exception
   {
      String[] notFoundClasses = {"org.jboss.cache.marshall.Person",
            "org.jboss.cache.marshall.Address"};
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      return new SelectedClassnameClassLoader(null, null, notFoundClasses, cl);
   }

   protected abstract class CacheUser implements Runnable
   {
      protected Semaphore semaphore;
      protected CacheSPI<Object, Object> cache;
      protected String name;
      protected Exception exception;
      protected Thread thread;

      CacheUser()
      {
      }

      CacheUser(Semaphore semaphore,
                String name,
                boolean sync,
                boolean activateRoot)
            throws Exception
      {
         this.cache = createCache(sync, true, false);
         this.semaphore = semaphore;
         this.name = name;

         if (activateRoot)
         {
            cache.getRegion(Fqn.ROOT, true).activate();
         }
      }

      CacheUser(Semaphore semaphore,
                String name,
                boolean sync,
                boolean activateRoot, long stateRetrievalTimeout)
            throws Exception
      {
         cache = createCache(sync, true, false, false, false, true);
         cache.getConfiguration().setStateRetrievalTimeout(stateRetrievalTimeout);
         cache.start();
         this.semaphore = semaphore;
         this.name = name;

         if (activateRoot)
         {
            cache.getRegion(Fqn.ROOT, true).activate();
         }
      }

      public void run()
      {
         boolean acquired = false;
         try
         {
            acquired = semaphore.tryAcquire(60, TimeUnit.SECONDS);
            if (!acquired)
            {
               throw new Exception(name + " cannot acquire semaphore");
            }

            useCache();

         }
         catch (Exception e)
         {
            e.printStackTrace(System.out);

            // Save it for the test to check
            exception = e;
         }
         finally
         {
            if (acquired)
            {
               semaphore.release();
            }
         }

      }

      abstract void useCache() throws Exception;

      public Exception getException()
      {
         return exception;
      }

      public CacheSPI<Object, Object> getCacheSPI()
      {
         return cache;
      }

      public String getName()
      {
         return name;
      }

      public void start()
      {
         thread = new Thread(this, name);
         thread.start();
      }

      public void cleanup()
      {
         if (thread != null && thread.isAlive())
         {
            thread.interrupt();
         }
      }
   }

   protected String getDefaultCacheLoader()
   {
      return org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader.class.getName();
   }

   private String getNextUniqueCacheName()
   {
      return getClass().getSimpleName() + cacheCount++;
   }
}
