package org.jboss.cache.profiling;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.testng.annotations.Test;

/**
 * Profile LOCAL mode operation
 * Importnat - make sure you inly enable these tests locally!
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.2.0
 */
@Test(groups = "profiling", testName = "profiling.ConstructionTest", enabled = false)
public class ConstructionTest
{
   Cache cache;
   private static final int WARMUP = 1000;
   private static final int LOOPS = 5000;

   public void testConstruction() throws InterruptedException
   {
      for (int i = 0; i < WARMUP; i++) new UnitTestCacheFactory<Object, Object>().createCache(getClass());
      System.out.println("Finished warmup.");
      System.gc();
      Thread.sleep(1000);
      System.out.println("Starting test");
      doConstructionTest();
   }

   public void doConstructionTest()
   {
      for (int i = 0; i < LOOPS; i++)
      {
         new UnitTestCacheFactory<Object, Object>().createCache(getClass());
         if (i % 100 == 0) System.out.println("In loop num " + i);
      }
   }
}
