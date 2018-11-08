package org.jboss.cache;

import org.testng.annotations.*;
import org.jboss.cache.util.TestingUtil;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = {"functional", "unit"})
public abstract class AbstractMultipleCachesTest<K, V> extends AbstractCacheTest<K,V>
{
   protected Set<CacheSPI<K, V>> caches = new HashSet<CacheSPI<K,V>>();

   @BeforeClass
   public void create() throws Throwable
   {
      createCaches();
   }

   @AfterClass
   protected void destroy()
   {
      TestingUtil.killCaches(caches.toArray(new Cache[caches.size()]));
   }

   @AfterMethod
   protected void clearContent() throws Throwable
   {
      if (caches.isEmpty()) throw new IllegalStateException("No caches registered! Use registerCaches(Cache... caches) do that!");
      for (CacheSPI<K, V> cache : caches)
      {
         super.clearContent(cache);
      }
   }

   protected abstract void createCaches() throws Throwable;

   final protected void registerCaches(Cache... caches)
   {
      for (Cache c: caches) this.caches.add((CacheSPI<K, V>) c);
   }

}
