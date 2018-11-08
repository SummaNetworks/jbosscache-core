package org.jboss.cache.api.nodevalidity;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "pessimistic"} , testName = "api.nodevalidity.ReplicatedPessNodeValidityTest")
public class ReplicatedPessNodeValidityTest extends NodeValidityTestBase
{
   protected Cache<String, String> createObserver()
   {
      return newCache();
   }

   protected Cache<String, String> createModifier()
   {
      return newCache();
   }

   protected Cache<String, String> newCache()
   {
      UnitTestCacheFactory<String, String> f = new UnitTestCacheFactory<String, String>();
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      
      Cache<String, String> cache = f.createCache(c, false, getClass());
      nodeLockingSchemeSpecificSetup(cache.getConfiguration());
      cache.start();
      return cache;
   }
}
