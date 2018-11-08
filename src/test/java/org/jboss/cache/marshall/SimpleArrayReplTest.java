package org.jboss.cache.marshall;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Random;
import org.jboss.cache.UnitTestCacheFactory;

@Test(groups = "functional", testName = "marshall.SimpleArrayReplTest")
public class SimpleArrayReplTest
{
   public void testArrayRepl() throws CloneNotSupportedException
   {
      int streamsize = 11000;
      byte[] b = new byte[streamsize];
      new Random().nextBytes(b);
      Cache<String, byte[]> cache1 = null, cache2 = null;

      try
      {
         Configuration c = new Configuration();
         c.setCacheMode(CacheMode.REPL_SYNC);
         c.setNodeLockingScheme(NodeLockingScheme.MVCC);
         cache1 = new UnitTestCacheFactory<String, byte[]>().createCache(c.clone(), getClass());
         cache2 = new UnitTestCacheFactory<String, byte[]>().createCache(c.clone(), getClass());

         TestingUtil.blockUntilViewsReceived(60000, cache1, cache2);

         cache1.put(Fqn.fromString("/a"), "test", b);
         byte[] bytesBack = cache2.get(Fqn.fromString("/a"), "test");

         assert Arrays.equals(b, bytesBack);
      }
      finally
      {
         TestingUtil.killCaches(cache1, cache2);
      }
   }
}
