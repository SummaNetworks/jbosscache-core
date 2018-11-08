package org.jboss.cache.buddyreplication;

import org.jboss.cache.CacheSPI;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterMethod;

import java.util.List;

/**
 * @author Mircea.Markus@jboss.com
 */
public abstract class AbstractNodeBasedBuddyTest extends BuddyReplicationTestsBase
{
   protected List<CacheSPI<Object, Object>> caches;
                        
   protected final String key = "key";
   protected  final String value = "value";

   @BeforeClass
   public abstract void createCaches() throws Exception;

   @AfterClass
   public void killCaches() throws Exception
   {
      super.cleanupCaches(caches, true);
   }

   @AfterMethod 
   public void tearDown() throws Exception
   {
      super.cleanupCaches(caches, false);
   }
}
