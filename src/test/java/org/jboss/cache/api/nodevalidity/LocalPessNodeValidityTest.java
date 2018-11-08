package org.jboss.cache.api.nodevalidity;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "pessimistic"}, testName = "api.nodevalidity.LocalPessNodeValidityTest")
public class LocalPessNodeValidityTest extends NodeValidityTestBase
{
   public LocalPessNodeValidityTest()
   {
      clustered = false;
      nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;
   }


   Cache<String, String> onlyInstance;

   protected Cache<String, String> createObserver()
   {
      return returnOnlyInstance();
   }

   protected Cache<String, String> createModifier()
   {
      return returnOnlyInstance();
   }

   private Cache<String, String> returnOnlyInstance()
   {
      if (onlyInstance == null)
      {
         UnitTestCacheFactory<String, String> f = new UnitTestCacheFactory<String, String>();
         onlyInstance = f.createCache(false, getClass());
         nodeLockingSchemeSpecificSetup(onlyInstance.getConfiguration());
         onlyInstance.start();
      }
      return onlyInstance;
   }
}
