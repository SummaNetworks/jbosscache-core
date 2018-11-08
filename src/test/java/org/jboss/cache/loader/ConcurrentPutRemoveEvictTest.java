package org.jboss.cache.loader;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * To test JBCACHE-1355
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.2.0
 */
@Test(groups = "functional", enabled = false, testName = "loader.ConcurrentPutRemoveEvictTest")
// TODO: 2.2.0: Figure out why this occasionally hangs!!
public class ConcurrentPutRemoveEvictTest
{
   Cache<String, String> cache;
   Fqn fqn = Fqn.fromString("/a");
   String key = "key";
   boolean run = true;
   Set<Exception> exceptions = new HashSet<Exception>();

   @BeforeTest
   public void setUp() throws Exception
   {
      CacheLoaderConfig cacheLoaderConfig = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummyInMemoryCacheLoader.class.getName(),
            "", false, false, false, false, false);
      Configuration cfg = new Configuration();
      cfg.setCacheLoaderConfig(cacheLoaderConfig);
      cache = new UnitTestCacheFactory<String, String>().createCache(cfg, getClass());
      cache.put(fqn, key, "value");
   }

   @AfterTest
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void doTest() throws Exception
   {
      List<Thread> threads = new ArrayList<Thread>();

      threads.add(new Getter());
      threads.add(new RandomAdder());
      threads.add(new Evicter());

      for (Thread t : threads) t.start();

      // let these run for a while.
      TestingUtil.sleepThread(10000);

      run = false;

      for (Thread t : threads) t.join();

      if (!exceptions.isEmpty())
      {
         for (Exception e : exceptions) throw e;
      }
   }

   private class RandomAdder extends Thread
   {
      public void run()
      {
         int i = 0;
         while (run)
         {
            try
            {
               cache.put(fqn, key + (i++), "");
            }
            catch (Exception e)
            {
               // ignore
            }
         }
      }
   }


   private class Getter extends Thread
   {
      public void run()
      {
         while (run)
         {
            try
            {
               // note that we sometimes get a null back.  This is incorrect and inconsistent, but has to do with locks being held
               // on nodes.  Very similar to http://jira.jboss.org/jira/browse/JBCACHE-1165
               String value = cache.get(fqn, key);
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      }
   }

   private class Evicter extends Thread
   {
      public void run()
      {
         while (run)
         {
            try
            {
               cache.evict(fqn);
            }
            catch (Exception e)
            {
               // who cares
            }
         }
      }
   }

}
