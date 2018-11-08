package org.jboss.cache.api;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeCreated;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.transaction.GenericTransactionManagerLookup;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import org.jboss.cache.*;
import org.testng.annotations.AfterMethod;

/**
 * Tests aspects of the {@link org.jboss.cache.Cache} public API when
 * destroy() has been called on the cache.
 *
 * @author Brian Stansberry
 */

@Test(groups = {"functional", "pessimistic"}, sequential = true, testName = "api.DestroyedCacheAPITest")
public class DestroyedCacheAPITest extends AbstractSingleCacheTest
{
   private Cache<String, String> cache;
   protected boolean optimistic;
   private Fqn parent = Fqn.fromString("/test/fqn");
   private Fqn child = Fqn.fromString("/test/fqn/child");
   private String version;
   private Node<String, String> root;

   public CacheSPI createCache()
   {
      // start a single cache instance
      UnitTestCacheFactory<String, String> cf = new UnitTestCacheFactory<String, String>();
      cache = cf.createCache("configs/local-tx.xml", false, getClass());
      cache.getConfiguration().setNodeLockingScheme(optimistic ? Configuration.NodeLockingScheme.OPTIMISTIC : Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.start();
      version = cache.getVersion();
      cache.getRoot().addChild(parent);
      cache.getRoot().addChild(child);
      root = cache.getRoot();
      cache.stop();
      cache.destroy();
      return (CacheSPI) cache;
   }

   /**
    * Tests that the configuration contains the values expected, as well as immutability of certain elements
    */
   public void testConfiguration()
   {
      Configuration c = cache.getConfiguration();
      assertEquals(Configuration.CacheMode.LOCAL, c.getCacheMode());
      assertEquals(GenericTransactionManagerLookup.class.getName(), c.getTransactionManagerLookupClass());

      // Certain values, e.g. CacheMode should be immutable once started, 
      // but we aren't started, so should be able to change them
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      assertEquals(Configuration.CacheMode.REPL_SYNC, c.getCacheMode());

      // others are always changeable.
      c.setLockAcquisitionTimeout(100);
      assertEquals(100, c.getLockAcquisitionTimeout());
   }

   /**
    * Basic usage of cache listeners
    * <p/>
    * A more complete test that tests notifications is in org.jboss.cache.notifications
    */
   public void testCacheListeners()
   {
      try
      {
         cache.getCacheListeners();
      }
      catch (IllegalStateException good)
      {
         // expected
      }

      Object dummy = new Listener();

      try
      {
         cache.addCacheListener(dummy);
      }
      catch (IllegalStateException good)
      {
         // expected
      }

      try
      {
         cache.removeCacheListener(dummy);
      }
      catch (IllegalStateException good)
      {
         // expected
      }
   }

   /**
    * Tests the basic gets, puts.  Expectation is all will throw an
    * ISE.
    * <p/>
    * BES 2008/03/22 -- This behavior is not actually documented.  Maintainers
    * shouldn't feel constrained from updating this test to match
    * agreed upon behavior changes; I'm just adding it so any changes to the
    * current behavior will trigger failures and ensure that people are aware of
    * the change and agree that it's correct.
    */
   public void testConvenienceMethods()
   {
      String key = "key", value = "value";
      Map<String, String> data = new HashMap<String, String>();
      data.put(key, value);

      try
      {
         cache.get(parent, key);
         fail("Get key on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.getNode(parent);
         fail("Get node on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.put(parent, key, value);
         fail("Put key/value on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.putForExternalRead(parent, key, value);
         fail("Put for external read on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.put(parent, data);
         fail("Put Map on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.move(child, Fqn.ROOT);
         fail("Remove move on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.getData(parent);
         fail("getData on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.getKeys(parent);
         fail("getKeys on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.clearData(parent);
         fail("clearData on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.remove(parent, key);
         fail("Remove key on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         cache.removeNode(parent);
         fail("Remove node on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

   }

   /**
    * Tests the basic node addition, existence check, get, remove operations.
    * Expectation is all will throw an ISE.
    * <p/>
    * BES 2008/03/22 -- This behavior is not actually documented.  Maintainers
    * shouldn't feel constrained from updating this test to match
    * agreed upon behavior changes; I'm just adding it so any changes to the
    * current behavior will trigger failures and ensure that people are aware of
    * the change and agree that it's correct.
    */
   public void testNodeAPI()
   {
      try
      {
         root.addChild(parent);
         fail("addChild on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         root.hasChild(parent);
         fail("hasChild on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         root.getChild(parent);
         fail("getChild on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }

      try
      {
         root.removeChild(parent);
         fail("removeChild on destroyed cache did not throw ISE");
      }
      catch (IllegalStateException good)
      {
      }
   }

   /**
    * Tests the evict operation. Expectation is it will throw an ISE.
    */
   public void testEvict()
   {
      try
      {
         cache.evict(parent, false);
         assert false : "Should throw ISE";
      }
      catch (IllegalStateException ok)
      {
      }

      try
      {
         cache.evict(child, false);
         assert false : "Should throw ISE";
      }
      catch (IllegalStateException ok)
      {
      }

      try
      {
         cache.evict(parent, true);
         assert false : "Should throw ISE";
      }
      catch (IllegalStateException ok)
      {
      }
   }

   public void testGetRegion()
   {
      assertNotNull(cache.getRegion(parent, false)); // should end up with the root region 
      assertNotNull(cache.getRegion(Fqn.ROOT, true));
   }

   public void testRemoveRegion()
   {
      assertFalse(cache.removeRegion(parent));
   }

   public void testGetLocalAddress()
   {
      assertEquals("CacheMode.LOCAL cache has no address", null, cache.getLocalAddress());
   }

   public void testGetMembers()
   {
      assertNull(cache.getMembers());
   }

   public void testGetCacheStatus()
   {
      assertEquals(CacheStatus.DESTROYED, cache.getCacheStatus());
   }

   public void testGetVersion()
   {
      assertEquals(version, cache.getVersion());
   }


   @CacheListener
   public class Listener
   {
      @NodeCreated
      public void nodeCreated(Event e)
      {
      }
   }
}
