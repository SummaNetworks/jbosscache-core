package org.jboss.cache.api;

import org.jboss.cache.AbstractSingleCacheTest;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.NodeNotExistsException;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeCreated;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.transaction.GenericTransactionManagerLookup;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests the {@link org.jboss.cache.Cache} public API at a high level
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 */

@Test(groups = {"functional", "pessimistic"}, sequential = true, testName = "api.CacheAPITest")
public class CacheAPITest extends AbstractSingleCacheTest 
{
   protected CacheSPI<String, String> cache;
   private List<String> events;

   public CacheSPI createCache()
   {
      // start a single cache instance
      UnitTestCacheFactory<String, String> cf = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) cf.createCache("configs/local-tx.xml", false, getClass());
      cache.getConfiguration().setEvictionConfig(null);
      configure(cache.getConfiguration());
      cache.start();
      events = new ArrayList<String>();
      return cache;
   }

   protected void configure(Configuration c)
   {
      c.setNodeLockingScheme(getNodeLockingScheme());
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      events.clear();
      cache.getRegionManager().reset();
   }

   protected NodeLockingScheme getNodeLockingScheme()
   {
      return NodeLockingScheme.PESSIMISTIC;
   }

   /**
    * Tests that the configuration contains the values expected, as well as immutability of certain elements
    */
   public void testConfiguration()
   {
      Configuration c = cache.getConfiguration();
      assertEquals(Configuration.CacheMode.LOCAL, c.getCacheMode());
      assertEquals(GenericTransactionManagerLookup.class.getName(), c.getTransactionManagerLookupClass());

      // note that certain values should be immutable.  E.g., CacheMode cannot be changed on the fly.
      try
      {
         c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
         assert false : "Should have thrown an Exception";
      }
      catch (ConfigurationException e)
      {
         // expected
      }

      // others should be changeable though.
      c.setLockAcquisitionTimeout(100);
   }

   public void testGetMembersInLocalMode()
   {
      assert cache.getMembers() == null : "Cache members should be null if running in LOCAL mode";
   }

   /**
    * Basic usage of cache listeners
    * <p/>
    * A more complete test that tests notifications is in org.jboss.cache.notifications
    */
   public void testCacheListeners()
   {
      assertEquals(0, cache.getCacheListeners().size());

      Object dummy = new Listener();

      cache.addCacheListener(dummy);

      assertEquals(1, cache.getCacheListeners().size());

      cache.getRoot().addChild(Fqn.fromString("/blah"));

      // test that the event was captured by the listener.

      // FOR A FULL TEST ON NOTIFICATIONS SEE TESTS IN org.jboss.cache.notifications
      assertEquals(1, events.size());

      cache.removeCacheListener(dummy);

      assertEquals(0, cache.getCacheListeners().size());
   }

   /**
    * All cache operations should happen on a {@link Node} - I.e., you look up a {@link Node} and perform data operations
    * on this {@link Node}.  For convenience and familiarity with JBoss Cache 1.x, we provide some helpers in {@link Cache}
    * which dives you direct data access to nodes.
    * <p/>
    * This test exercises these.
    */
   public void testConvenienceMethods()
   {
      Fqn fqn = Fqn.fromString("/test/fqn");
      String key = "key", value = "value";
      Map<String, String> data = new HashMap<String, String>();
      data.put(key, value);

      assertNull(cache.get(fqn, key));

      cache.put(fqn, key, value);

      assertEquals(value, cache.get(fqn, key));

      cache.remove(fqn, key);

      assertNull(cache.get(fqn, key));

      cache.put(fqn, data);

      assertEquals(value, cache.get(fqn, key));
   }


   /**
    * Another convenience method that tests node removal
    */
   public void testNodeConvenienceNodeRemoval()
   {
      // this fqn is relative, but since it is from the root it may as well be absolute
      Fqn fqn = Fqn.fromString("/test/fqn");
      cache.getRoot().addChild(fqn);
      assertTrue(cache.getRoot().hasChild(fqn));

      assertEquals(true, cache.removeNode(fqn));
      assertFalse(cache.getRoot().hasChild(fqn));
      // remove should REALLY remove though and not just mark as deleted/invalid.
      NodeSPI n = cache.peek(fqn, true, true);
      assert n == null;

      assertEquals(false, cache.removeNode(fqn));

      // remove should REALLY remove though and not just mark as deleted/invalid.
      n = cache.peek(fqn, true, true);
      assert n == null;

      // Check that it's removed if it has a child
      Fqn child = Fqn.fromString("/test/fqn/child");
      cache.getRoot().addChild(child);
      assertTrue(cache.getRoot().hasChild(child));

      assertEquals(true, cache.removeNode(fqn));
      assertFalse(cache.getRoot().hasChild(fqn));
      assertEquals(false, cache.removeNode(fqn));
   }

   /**
    * Tests basic eviction
    */
   public void testEvict()
   {
      Fqn one = Fqn.fromString("/one");
      Fqn two = Fqn.fromString("/one/two");
      String key = "key", value = "value";

      cache.getRoot().addChild(one).put(key, value);
      cache.getRoot().addChild(two).put(key, value);

      assertTrue(cache.getRoot().hasChild(one));
      assertFalse(cache.getRoot().getChild(one).getData().isEmpty());
      assertTrue(cache.getRoot().hasChild(two));
      assertFalse(cache.getRoot().getChild(two).getData().isEmpty());

      // evict two
      cache.evict(two, false);

      assertTrue(cache.getRoot().hasChild(one));
      assertTrue(cache.getRoot().getChild(one).getKeys().contains(key));
      assertFalse(cache.getRoot().hasChild(two));

      // now add 2 again...
      cache.getRoot().addChild(two).put(key, value);

      // now evict one, NOT recursive
      cache.evict(one, false);

      // one will NOT be removed, just emptied.
      assertTrue(cache.getRoot().hasChild(one));
      assertFalse(cache.getRoot().getChild(one).getKeys().contains(key));

      // two will be unaffected
      assertTrue(cache.getRoot().hasChild(two));
      assertTrue(cache.getRoot().getChild(two).getKeys().contains(key));
   }


   /**
    * Tests recursive eviction
    */
   public void testEvictRecursive()
   {
      Fqn one = Fqn.fromString("/one");
      Fqn two = Fqn.fromString("/one/two");
      String key = "key", value = "value";

      cache.getRoot().addChild(one).put(key, value);
      cache.getRoot().addChild(two).put(key, value);

      assertTrue(cache.getRoot().hasChild(one));
      assertFalse(cache.getRoot().getChild(one).getData().isEmpty());
      assertTrue(cache.getRoot().hasChild(two));
      assertFalse(cache.getRoot().getChild(two).getData().isEmpty());

      // evict two
      cache.evict(two, true);

      assertTrue(cache.getRoot().hasChild(one));
      assertFalse(cache.getRoot().getChild(one).getData().isEmpty());
      assertFalse(cache.getRoot().hasChild(two));

      // now add 2 again...
      cache.getRoot().addChild(two).put(key, value);

      // now evict one, recursive
      cache.evict(one, true);

      assertFalse(cache.getRoot().hasChild(one));
      assertFalse(cache.getRoot().hasChild(two));
   }


   /**
    * Again, see org.jboss.cache for more extensive tests on Regions.  This just tests the getRegion API on cache.
    */
   public void testRegion()
   {
      Region rootRegion = cache.getRegion(Fqn.ROOT, true);
      assertNotNull(rootRegion);// guaranteed never to return null if createIfAbsent is true.
      assertSame(rootRegion, cache.getRegion(Fqn.ROOT, true));

      Region otherRegion = cache.getRegion(Fqn.fromString("/other/region"), true);
      assertNotNull(otherRegion);
      assertSame(otherRegion, cache.getRegion(Fqn.fromString("/other/region"), true));
   }

   /**
    * Again, see org.jboss.cache for more extensive tests on Regions.  This just tests the getRegion API on cache.
    */
   public void testParentRegion1()
   {
      Region rootRegion = cache.getRegion(Fqn.ROOT, true);
      assertNotNull(rootRegion);// guaranteed never to return null if createIfAbsent is true.
      assertSame(rootRegion, cache.getRegion(Fqn.ROOT, false));

      Region otherRegion = cache.getRegion(Fqn.fromString("/other/region"), false);
      // should return the same parent region as root.

      assertSame(otherRegion, rootRegion);
   }

   /**
    * Again, see org.jboss.cache for more extensive tests on Regions.  This just tests the getRegion API on cache.
    */
   public void testParentRegion2()
   {
      Region rootRegion = cache.getRegion(Fqn.ROOT, true);
      Region parentRegion = cache.getRegion(Fqn.fromString("/parent"), true);
      assertNotSame("parentRegion should be a new region in its own right", rootRegion, parentRegion);

      Region childRegion = cache.getRegion(Fqn.fromString("/parent/region"), false);
      assertSame("Expecting the same region as parentRegion", childRegion, parentRegion);
   }


   /**
    * Again, see org.jboss.cache for more extensive tests on Regions.  This just tests the getRegion API on cache.
    */
   public void testNullRegion()
   {
      Region myRegion = cache.getRegion(Fqn.fromString("/myregion"), true);
      assertNotNull(myRegion);// guaranteed never to return null if createIfAbsent is true.
      assertSame(myRegion, cache.getRegion(Fqn.fromString("/myregion"), false));

      Region otherRegion = cache.getRegion(Fqn.fromString("/other/region"), false);
      // should return null since no eviction is in use.
      assert otherRegion == null;
   }

   public void testStopClearsData() throws Exception
   {
      Fqn a = Fqn.fromString("/a");
      Fqn b = Fqn.fromString("/a/b");
      String key = "key", value = "value";
      cache.getRoot().addChild(a).put(key, value);
      cache.getRoot().addChild(b).put(key, value);
      cache.getRoot().put(key, value);

      assertEquals(value, cache.getRoot().get(key));
      assertEquals(value, cache.getRoot().getChild(a).get(key));
      assertEquals(value, cache.getRoot().getChild(b).get(key));

      cache.stop();

      cache.start();

      assertNull(cache.getRoot().get(key));
      assertTrue(cache.getRoot().getData().isEmpty());
      assertTrue(cache.getRoot().getChildren().isEmpty());
   }

   public void testPhantomStructuralNodesOnRemove()
   {
      assert cache.peek(Fqn.fromString("/a/b/c"), true, true) == null;
      assert !cache.removeNode("/a/b/c");
      assert cache.peek(Fqn.fromString("/a/b/c"), true, true) == null;
      assert cache.peek(Fqn.fromString("/a/b"), true, true) == null;
      assert cache.peek(Fqn.fromString("/a"), true, true) == null;
   }

   public void testPhantomStructuralNodesOnRemoveTransactional() throws Exception
   {
      TransactionManager tm = cache.getTransactionManager();
      assert cache.peek(Fqn.fromString("/a/b/c"), true, true) == null;
      tm.begin();
      assert !cache.removeNode("/a/b/c");
      tm.commit();
      assert cache.peek(Fqn.fromString("/a/b/c"), true, true) == null;
      assert cache.peek(Fqn.fromString("/a/b"), true, true) == null;
      assert cache.peek(Fqn.fromString("/a"), true, true) == null;
   }

   public void testIsLeaf()
   {
      cache.put("/a/b/c", "k", "v");
      cache.put("/a/d", "k", "v");

      assert !cache.isLeaf(Fqn.ROOT);
      assert !cache.isLeaf("/a");
      assert !cache.isLeaf("/a/b");
      assert cache.isLeaf("/a/d");
      assert cache.isLeaf("/a/b/c");

      cache.removeNode("/a/b");
      cache.removeNode("/a/d");

      assert cache.isLeaf("/a");
      try
      {
         assert cache.isLeaf("/a/b");
         assert false;
      }
      catch (NodeNotExistsException expected)
      {
         assert true;
      }
   }

   public void testRpcManagerElements()
   {
      assertEquals("CacheMode.LOCAL cache has no address", null, cache.getLocalAddress());
      assertEquals("CacheMode.LOCAL cache has no members list", null, cache.getMembers());
   }

   @CacheListener
   public class Listener
   {

      @NodeCreated
      public void nodeCreated(Event e)
      {
         if (e.isPre()) events.add("Created");
      }
   }
}
