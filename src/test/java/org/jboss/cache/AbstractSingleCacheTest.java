package org.jboss.cache;

import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = {"functional", "unit"})
public abstract class AbstractSingleCacheTest<K, V> extends AbstractCacheTest<K, V>
{
   protected CacheSPI<K, V> cache;

   /**
    * This method will always be called before {@link #create()}.  If you override this, make sure you annotate the
    * overridden method with {@link org.testng.annotations.BeforeClass}.
    *
    * @throws Exception Just in case
    */
   @BeforeClass
   public void preCreate() throws Exception
   {
      // no op, made for overriding.
   }

   // Due to some weirdness with TestNG, it always appends the package and class name to the method names
   // provided on dependsOnMethods unless it thinks there already is a package.  This does accept regular expressions
   // though so .*. works.  Otherwise it won't detect overridden methods in subclasses.
   @BeforeClass(dependsOnMethods = "org.jboss.*.preCreate")
   protected void create() throws Exception
   {
      cache = createCache();
   }

   @AfterClass
   protected void destroy()
   {
      TestingUtil.killCaches(cache);
   }

   @AfterMethod
   protected void clearContent()
   {
      super.clearContent(cache);
   }

   protected abstract CacheSPI<K, V> createCache() throws Exception;
}
