package org.jboss.cache.profiling;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

import java.io.IOException;

/**
 * Importnat - make sure you inly enable these tests locally!
 */
@Test(groups = "profiling", testName = "profiling.MemoryFootprintTest", enabled = false)
public class MemoryFootprintTest
{
   int numFqns = 100000;

   public void testLocal() throws IOException
   {
      Cache<String, String> c = new UnitTestCacheFactory<String, String>().createCache(false, getClass());
      c.getConfiguration().setNodeLockingScheme(NodeLockingScheme.MVCC);
      c.getConfiguration().setIsolationLevel(IsolationLevel.READ_COMMITTED);
//      c.getConfiguration().setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      c.start();

      for (int i = 100000; i < 100000 + numFqns; i++)
      {
         String key = "keyX" + i;
         String value = "valX" + i;
         Fqn fqn = Fqn.fromElements(i);
         c.put(fqn, key, value);
      }

      System.out.println("Hit enter when done");
      System.in.read();

      c.stop();
   }


}
