package org.jboss.cache.config;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.JChannel;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Tests that JBC prefers an injected Channel to creating one via
 * a configured JChannelFactory and stack name.
 *
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7451 $
 */
@Test(groups = {"functional"}, testName = "config.ChannelInjectionTest")
public class ChannelInjectionTest
{
   private Set<Cache<String, String>> caches = new HashSet<Cache<String, String>>();

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      for (Cache cache : caches)
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
      caches = null;
   }

   public void testChannelInjectionPreference() throws Exception
   {
      Cache<String, String> cache1 = createCache();
      Cache<String, String> cache2 = createCache();

      Configuration conf = UnitTestConfigurationFactory.getEmptyConfiguration();

      JChannel ch1 = new JChannel(conf.getClusterConfig());
      cache1.getConfiguration().getRuntimeConfig().setChannel(ch1);

      JChannel ch2 = new JChannel(conf.getClusterConfig());
      cache2.getConfiguration().getRuntimeConfig().setChannel(ch2);

      cache1.start();
      cache2.start();

      TestingUtil.blockUntilViewsReceived(new Cache[]{cache1, cache2}, 10000);

      Fqn fqn = Fqn.fromString("/a");
      cache1.put(fqn, "key", "value");
      assertEquals("Value replicated", "value", cache2.get(fqn, "key"));
   }

   private Cache<String, String> createCache()
   {
      Configuration config = new Configuration();
      config.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      Cache<String, String> cache = instance.createCache(config, false, getClass());
      caches.add(cache);

      return cache;
   }
}