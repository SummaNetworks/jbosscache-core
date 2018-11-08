package org.jboss.cache.loader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.AbstractSingleCacheTest;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Modification;
import org.jboss.cache.Node;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.buddyreplication.BuddyManager;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.statetransfer.DefaultStateTransferManager;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.util.TestingUtil;
import org.jboss.util.stream.MarshalledValueInputStream;
import org.jboss.util.stream.MarshalledValueOutputStream;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;

/**
 * Commons tests for all CacheLoaders
 *
 * @author Bela Ban
 * @version $Id: CacheLoaderTestsBase.java 7629 2009-02-03 09:56:12Z manik.surtani@jboss.com $
 */
@Test(groups = {"functional"})
abstract public class CacheLoaderTestsBase extends AbstractSingleCacheTest
{
   static final Log log = LogFactory.getLog(CacheLoaderTestsBase.class);

   static final Fqn FQN = Fqn.fromString("/key");
   private static final Fqn SUBTREE_FQN = Fqn.fromRelativeElements(FQN, "subtree");

   private static final Fqn BUDDY_BASE = Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, "test");

   private static final Fqn BUDDY_PLUS_FQN = Fqn.fromRelativeFqn(BUDDY_BASE, FQN);

   private static final Fqn BUDDY_PLUS_SUBTREE_FQN = Fqn.fromRelativeFqn(BUDDY_BASE, SUBTREE_FQN);
   protected CacheLoader loader;


   protected CacheSPI createCache() throws Exception
   {
      cache = createUnstartedCache();
      cache.start();
      loader = cache.getCacheLoaderManager().getCacheLoader();
      postConfigure();
      return cache;
   }

   private CacheSPI<Object, Object> createUnstartedCache() throws Exception
   {
      CacheSPI<Object, Object> result = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      Configuration c = result.getConfiguration();
      c.setEvictionConfig(null);
      c.setCacheMode(Configuration.CacheMode.LOCAL);
      c.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());
      configureCache(result);
      return result;
   }

   @BeforeMethod
   public void clearLoader() throws Exception
   {
      loader.remove(Fqn.ROOT);
   }

   /**
    * Subclass if you need any further cfg after the cache starts.
    */
   protected void postConfigure()
   {
   }

   abstract protected void configureCache(CacheSPI cache) throws Exception;

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      cleanup();
      //if (loader != null) loader.remove(Fqn.ROOT);
   }

   protected void cleanup() throws Exception
   {
      // to be overridden
   }

   protected void addDelay()
   {
      // returns immediately in this case.  Subclasses may override where a delay is needed.
   }

   /**
    * Helper method to test the existence of a key
    *
    * @param fqn
    * @param key
    */
   protected boolean exists(String fqn, String key)
   {

      NodeSPI n = cache.peek(Fqn.fromString(fqn), false, false);
      if (key == null) return n != null;
      return n != null && n.getKeysDirect().contains(key);
   }

   public void testPrint() throws CacheException
   {

      final Fqn NODE = Fqn.fromString("/test");
      final String KEY = "key";
      cache.put(NODE, KEY, 10);
      cache.evict(NODE, false);
      addDelay();
      Node ret = cache.getNode(NODE);
      assertNotNull(ret);
   }

   public void testPut() throws CacheException
   {

      final String NODE = "/test";
      final String KEY = "key";
      Object retval;
      cache.removeNode(NODE);
      addDelay();
      retval = cache.put(NODE, KEY, 10);
      assertEquals(null, retval);
      retval = cache.put(NODE, KEY, 20);
      addDelay();
      assertEquals(10, retval);
      cache.evict(Fqn.fromString(NODE), false);// evicts from memory, but *not* from store
      addDelay();
      log.debug("put 30, expect 20 back");
      retval = cache.put(NODE, KEY, 30);
      assertEquals(20, retval);
   }


   public void testPut2() throws Exception
   {


      final String NODE = "/a/b/c";
      assertNull(loader.get(Fqn.fromString(NODE)));
      final String KEY = "key";
      Object retval;
      cache.removeNode(NODE);
      assertNull(loader.get(Fqn.fromString(NODE)));
      addDelay();
      retval = cache.put(NODE, KEY, 10);
      assertNull(retval);
      addDelay();
      retval = cache.put(NODE, KEY, 20);
      assertEquals(10, retval);
      cache.evict(Fqn.fromString(NODE), false);// evicts from memory, but *not* from store
      cache.evict(Fqn.fromString("/a/b"), false);
      cache.evict(Fqn.fromString("/a"), false);
      addDelay();
      log.debug("replace KEY with 30, expect 20");
      retval = cache.put(NODE, KEY, 30);
      assertEquals(20, retval);
   }

   /**
    * Tests various Map puts.
    */
   public void testPut3() throws Exception
   {
      final Fqn NODE = Fqn.fromString("/a/b/c");


      cache.removeNode(NODE);
      addDelay();
      Map<Object, Object> m = new HashMap<Object, Object>();
      m.put("a", "b");
      m.put("c", "d");
      Map<Object, Object> m2 = new HashMap<Object, Object>();
      m2.put("e", "f");
      m2.put("g", "h");

      cache.put(NODE, m);

      addDelay();
      cache.get(NODE, "X");

      assertEquals(m, loader.get(NODE));
      assertEquals(m, cache.getNode(NODE).getData());
      cache.evict(NODE, false);
      addDelay();
      cache.get(NODE, "X");
      assertEquals(m, cache.getNode(NODE).getData());
      cache.evict(NODE, false);
      assertEquals(m, cache.getNode(NODE).getData());
      cache.get(NODE, "X");
      cache.put(NODE, m2);
      Map<Object, Object> data = cache.getNode(NODE).getData();
      assertEquals("combined", 4, data.size());
   }

   public void testPutNullDataMap() throws Exception
   {
      Fqn f = Fqn.fromString("/a");
      assert !cache.exists(f);
      assert !loader.exists(f);
      cache.put(f, null);
      Map fromLoader = loader.get(f);
      assert fromLoader != null : "Node should exist in the loader";
      assert fromLoader.isEmpty() : "Should not contain any data";
   }

   public void testPutNullDataMapNodeHasData() throws Exception
   {
      Fqn f = Fqn.fromString("/a");


      cache.put(f, "key", "value");
      assert cache.exists(f);
      assert loader.exists(f);
      cache.put(f, null);
      Map fromLoader = loader.get(f);
      assert fromLoader != null : "Node should exist in the loader";
      assert fromLoader.size() == 1 : "Should contain original data";
   }


   public void testShallowMove() throws Exception
   {

      Fqn a = Fqn.fromString("/a");
      Fqn b = Fqn.fromString("/b");
      Fqn a_b = Fqn.fromString("/a/b");
      String key = "key", valueA = "A", valueB = "B";

      cache.put(a, key, valueA);
      cache.put(b, key, valueB);

      addDelay();

      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();
      assertEquals(valueA, loader.get(a).get(key));
      assertEquals(valueB, loader.get(b).get(key));
      assertTrue(loader.getChildrenNames(Fqn.ROOT).contains("a"));
      assertTrue(loader.getChildrenNames(Fqn.ROOT).contains("b"));

      // now move
      cache.move(b, a);

      addDelay();

      assertEquals(valueA, loader.get(a).get(key));
      assertNull(loader.get(b));
      assertEquals(valueB, loader.get(a_b).get(key));

   }

   public void testDeepMove() throws Exception
   {


      Fqn a = Fqn.fromString("/a");
      Fqn b = Fqn.fromString("/b");
      Fqn a_b = Fqn.fromString("/a/b");
      Fqn b_c = Fqn.fromString("/b/c");
      Fqn a_b_c = Fqn.fromString("/a/b/c");

      String key = "key", valueA = "A", valueB = "B", valueC = "C";

      cache.put(a, key, valueA);
      cache.put(b, key, valueB);
      cache.put(b_c, key, valueC);


      addDelay();

      assertEquals(valueA, cache.getCacheLoaderManager().getCacheLoader().get(a).get(key));
      assertEquals(valueB, cache.getCacheLoaderManager().getCacheLoader().get(b).get(key));
      assertEquals(valueC, cache.getCacheLoaderManager().getCacheLoader().get(b_c).get(key));

      // now move
      cache.move(b, a);

      addDelay();

      assertEquals(valueA, cache.getCacheLoaderManager().getCacheLoader().get(a).get(key));
      assertNull(cache.getCacheLoaderManager().getCacheLoader().get(b));
      assertEquals(valueB, cache.getCacheLoaderManager().getCacheLoader().get(a_b).get(key));
      assertNull(cache.getCacheLoaderManager().getCacheLoader().get(b_c));
      assertEquals(valueC, cache.getCacheLoaderManager().getCacheLoader().get(a_b_c).get(key));

   }


   /**
    * Tests various put combos which should exercise the CacheLoaderInterceptor.
    */
   public void testPutRemoveCombos() throws Exception
   {


      final String NODE = "/a/b/c";
      cache.removeNode(NODE);
      Fqn fqn = Fqn.fromString(NODE);
      addDelay();
      Map<Object, Object> m = new HashMap<Object, Object>();
      m.put("a", "b");
      m.put("c", "d");
      loader.put(fqn, m);
      cache.put(NODE, "e", "f");
      addDelay();
      cache.get(NODE, "X");
      assertEquals(3, cache.getNode(NODE).getData().size());
      cache.evict(fqn, false);
      cache.get(NODE, "X");
      cache.remove(NODE, "e");
      assertEquals(2, cache.getNode(NODE).getData().size());
   }

   public void testGet() throws CacheException
   {


      final String NODE = "/a/b/c";
      Object retval;
      cache.removeNode(NODE);
      addDelay();
      retval = cache.put(NODE, "1", 10);
      assertNull(retval);
      addDelay();
      cache.put(NODE, "2", 20);
      cache.evict(Fqn.fromString("/a/b/c"), false);
      assertNull("DataNode should not exisit ", cache.peek(Fqn.fromString("/a/b/c"), false, false));
      addDelay();
      retval = cache.get(NODE, "1");
      assertEquals(10, retval);
      retval = cache.get(NODE, "2");
      assertEquals(20, retval);
   }

   public void testGetNode() throws CacheException
   {


      final String NODE = "/a/b/c";
      Node<Object, Object> retval;
      cache.removeNode(NODE);
      addDelay();
      cache.put(NODE, "1", 10);
      cache.evict(Fqn.fromString(NODE), false);
      assertNull("DataNode should not exisit ", cache.peek(Fqn.fromString("/a/b/c"), false, false));
      addDelay();
      retval = cache.getNode(NODE);

      assertNotNull("Should not be null", retval);

      assertEquals(10, retval.get("1"));
   }


   public void testSerialization() throws CacheException
   {


      SamplePojo pojo = new SamplePojo(39, "Bela");
      pojo.getHobbies().add("Running");
      pojo.getHobbies().add("Beerathlon");
      pojo.getHobbies().add("Triathlon");
      cache.put("/mypojo", 322649, pojo);
      addDelay();
      assertNotNull(cache.get("/mypojo", 322649));
      cache.evict(Fqn.fromString("/mypojo"), false);
      assertNull(cache.peek(Fqn.fromString("/mypojo"), false, false));
      SamplePojo pojo2 = (SamplePojo) cache.get("/mypojo", 322649);// should fetch from CacheLoader
      assertNotNull(pojo2);
      assertEquals(39, pojo2.getAge());
      assertEquals("Bela", pojo2.getName());
      assertEquals(3, pojo2.getHobbies().size());
   }

   /**
    * Just adds some data that wil be later retrieved. This test has to be run first
    */
   public void testPopulate()
   {

      try
      {
         Map<Object, Object> m = new HashMap<Object, Object>();
         for (int i = 0; i < 10; i++)
         {
            m.put("key" + i, "val" + i);
         }
         cache.put("/a/b/c", m);

         // force preload on /1/2/3/4/5
         cache.getCacheLoaderManager().preload(Fqn.fromString("/1/2/3/4/5"), true, true);

         cache.put("/1/2/3/4/5", null);
         cache.put("/1/2/3/4/5/a", null);
         cache.put("/1/2/3/4/5/b", null);
         cache.put("/1/2/3/4/5/c", null);
         cache.put("/1/2/3/4/5/d", null);
         cache.put("/1/2/3/4/5/e", null);
         cache.put("/1/2/3/4/5/d/one", null);
         cache.put("/1/2/3/4/5/d/two", null);
         cache.put("/1/2/3/4/5/d/three", null);
         // cache.put("/a/b/c", "newKey", "newValue");

         assertTrue(cache.exists("/1/2/3/4"));
         assertTrue(cache.exists("/a/b/c"));
         assert (!exists("/a/b/c/d", null));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }


   public void testPreloading() throws CacheException
   {

      cache.removeNode(Fqn.ROOT);
      cache.put("1/2/3/4/5/d", "key", "val");
      cache.evict(Fqn.fromString("1/2/3/4/5/d"));
      addDelay();
      assert (!exists("1/2/3/4/5/d", null));// exists() doesn't load
      cache.getNode("1/2/3/4/5/d");// get *does* load
      assertTrue(cache.exists("1/2/3/4/5/d"));
   }


   @SuppressWarnings("unchecked")
   public void testCacheLoading2() throws Exception
   {

      Set keys = null;
      cache.put("/a/b/c", "key", "val");
      keys = cache.getNode("/a/b/c").getKeys();
      assertNotNull(keys);
      assertEquals(1, keys.size());
   }


   public void testExists()
   {

      cache.put("/eins/zwei/drei", "key1", "val1");
      assertTrue(cache.exists("/eins/zwei/drei"));
      assert (exists("/eins/zwei/drei", "key1"));
      assert (!exists("/eins/zwei/drei", "key2"));
      assert (!exists("/uno/due/tre", null));
      assert (!exists("/une/due/tre", "key1"));
   }

   public void testGetChildren()
   {

      try
      {
         cache.put("/1/2/3/4/5/d/one", null);
         cache.put("/1/2/3/4/5/d/two", null);
         cache.put("/1/2/3/4/5/d/three", null);
         Set children = cache.getNode("/1/2/3/4/5/d").getChildrenNames();
         assertNotNull(children);
         assertEquals(3, children.size());
         assertTrue(children.contains("one"));
         assertTrue(children.contains("two"));
         assertTrue(children.contains("three"));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }


   public void testGetChildrenWithEviction() throws CacheException
   {

      cache.put("/a/b/c/1", null);
      cache.put("/a/b/c/2", null);
      cache.put("/a/b/c/3", null);
      cache.evict(Fqn.fromString("/a/b/c/1"));
      cache.evict(Fqn.fromString("/a/b/c/2"));
      cache.evict(Fqn.fromString("/a/b/c/3"));
      cache.evict(Fqn.fromString("/a/b/c"));
      cache.evict(Fqn.fromString("/a/b"));
      cache.evict(Fqn.fromString("/a"));
      cache.evict(Fqn.fromString("/"));
      addDelay();
      Set children = cache.getNode("/a/b/c").getChildrenNames();
      assertNotNull(children);
      assertEquals(3, children.size());
      assertTrue(children.contains("1"));
      assertTrue(children.contains("2"));
      assertTrue(children.contains("3"));
   }

   public void testGetChildren2()
   {

      try
      {
         cache.put("/1", null);
         cache.put("a", null);
         Set children = cache.getRoot().getChildrenNames();
         assertNotNull(children);
         assertEquals(2, children.size());
         assertTrue(children.contains("1"));
         assertTrue(children.contains("a"));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }

   public void testGetChildren3()
   {

      try
      {
         cache.put("/1", null);
         cache.put("a", null);
         Set children = cache.getRoot().getChildrenNames();
         assertNotNull(children);
         assertEquals(2, children.size());
         assertTrue(children.contains("1"));
         assertTrue(children.contains("a"));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }

   public void testGetChildren4()
   {

      try
      {
         if (!cache.exists("/a/b/c"))
         {
            cache.put("/a/b/c", null);
         }
         Set children = cache.getChildrenNames((Fqn) null);
         assertTrue(children.isEmpty());
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }


   public void testGetChildren5()
   {

      try
      {
         cache.put("/a/1", null);
         cache.put("/a/2", null);
         cache.put("/a/3", null);

         Node n = cache.getNode("/a");
         assertNotNull(n);

         Set children = n.getChildrenNames();
         assertNotNull(children);
         assertEquals(3, children.size());
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }


   public void testGetChildren6()
   {

      try
      {
         cache.put("/a/1", null);
         cache.put("/a/2", null);
         cache.put("/a/3", null);
         cache.evict(Fqn.fromString("/a/1"));
         cache.evict(Fqn.fromString("/a/2"));
         cache.evict(Fqn.fromString("/a/3"));
         cache.evict(Fqn.fromString("/a"));
         addDelay();
         assertNotNull(cache.getNode("/a"));

         Set children = cache.getNode("/a").getChildrenNames();
         assertNotNull("No children were loaded", children);
         assertEquals("3 children weren't loaded", 3, children.size());
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }

   public void testGetChildren7()
   {

      try
      {
         cache.put("/a/1", null);
         cache.put("/a/2", null);
         cache.put("/a/3", null);
         cache.put("/a", "test", "test");
         cache.evict(Fqn.fromString("/a/1"));
         cache.evict(Fqn.fromString("/a/2"));
         cache.evict(Fqn.fromString("/a/3"));
         cache.evict(Fqn.fromString("/a"));
         addDelay();
         Object val = cache.get("/a", "test");
         assertEquals("attributes weren't loaded", "test", val);

         Set children = cache.getNode("/a").getChildrenNames();
         assertNotNull("No children were loaded", children);
         assertEquals("3 children weren't loaded", 3, children.size());
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }

   public void testGetChildren8()
   {

      cache.put("/a/1", null);
      cache.put("/a/2", null);
      cache.put("/a/3", null);
      cache.evict(Fqn.fromString("/a/1"));
      cache.evict(Fqn.fromString("/a/2"));
      cache.evict(Fqn.fromString("/a/3"));
      cache.evict(Fqn.fromString("/a"));
      addDelay();
      assertNull(cache.get("/a", "test"));

      cache.getNode("/a/1");
      Set children = cache.getNode("/a").getChildrenNames();
      assertNotNull("No children were loaded", children);
      assertEquals("3 children weren't loaded", 3, children.size());
   }

   public void testGetChildren9()
   {

      try
      {
         cache.put("/a/1", null);
         cache.put("/a/2", null);
         cache.put("/a/3", null);
         cache.evict(Fqn.fromString("/a/1"));
         cache.evict(Fqn.fromString("/a/2"));
         cache.evict(Fqn.fromString("/a/3"));
         cache.evict(Fqn.fromString("/a"));
         addDelay();
         assertNull(cache.get("/a", "test"));

         cache.getNode("/a/1");
         Set children = cache.getNode("/a").getChildrenNames();
         assertNotNull("No children were loaded", children);
         assertEquals("3 children weren't loaded", 3, children.size());

         cache.evict(Fqn.fromString("/a/1"));
         cache.evict(Fqn.fromString("/a/2"));
         cache.evict(Fqn.fromString("/a/3"));
         cache.evict(Fqn.fromString("/a"));

         assertNull(cache.get("/a", "test"));

         cache.getNode("/a/1");
         children = cache.getNode("/a").getChildrenNames();
         assertNotNull("No children were loaded", children);
         assertEquals("3 children weren't loaded", 3, children.size());
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }


   public void testGetChildren10()
   {

      try
      {
         cache.put("/a/1", null);
         cache.put("/a/2", null);
         cache.put("/a/3", null);
         cache.evict(Fqn.fromString("/a/1"));
         cache.evict(Fqn.fromString("/a/2"));
         cache.evict(Fqn.fromString("/a/3"));
         cache.evict(Fqn.fromString("/a"));
         addDelay();
         assertNull(cache.get("/a", "test"));

         cache.getNode("/a/1");
         Set children = cache.getNode("/a").getChildrenNames();
         assertNotNull("No children were loaded", children);
         assertEquals("3 children weren't loaded", 3, children.size());

         children = cache.getNode("/a").getChildrenNames();
         assertNotNull("No children were loaded", children);
         assertEquals("3 children weren't loaded", 3, children.size());
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }


   public void testGetChildren11()
   {

      Set children;
      try
      {
         cache.put("/a/b", "key", "val");
         cache.put("/a/b/1", "key", "val");
         cache.put("/a/b/2", "key", "val");
         cache.put("/a/b/3", "key", "val");
         cache.put("/a/b/1/tmp", "key", "val");
         cache.put("/a/b/2/tmp", "key", "val");
         cache.put("/a/b/3/tmp", "key", "val");

         cache.evict(Fqn.fromString("/a"));
         cache.evict(Fqn.fromString("/a/b"));
         cache.evict(Fqn.fromString("/a/b/1"));
         cache.evict(Fqn.fromString("/a/b/2"));
         cache.evict(Fqn.fromString("/a/b/3"));

         // now load the children - this set childrenLoaded in /a/b to true
         children = cache.getNode("/a/b").getChildrenNames();
         assertEquals(3, children.size());

         cache.evict(Fqn.fromString("/a/b"));
         cache.evict(Fqn.fromString(("/a/b/1/tmp")));
         cache.evict(Fqn.fromString(("/a/b/2/tmp")));
         cache.evict(Fqn.fromString(("/a/b/3/tmp")));
         cache.evict(Fqn.fromString(("/a/b/1")));
         cache.evict(Fqn.fromString(("/a/b/2")));
         cache.evict(Fqn.fromString(("/a/b/3")));
         cache.evict(Fqn.fromString("/a"));

         children = cache.getNode("/a/b").getChildrenNames();
         assertEquals(3, children.size());
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }


   public void testGetChildren12()
   {
      Set children;
      cache.put("/a/b", "key", "val");
      cache.put("/a/b/1", "key", "val");
      cache.put("/a/b/2", "key", "val");
      cache.put("/a/b/3", "key", "val");
      children = cache.getNode("/a/b").getChildrenNames();
      assertEquals(3, children.size());

      cache.evict(Fqn.fromString("/a/b/3"));
      cache.evict(Fqn.fromString("/a/b/2"));
      // cache.evict(Fqn.fromString("/a/b/1"));
      cache.evict(Fqn.fromString("/a/b"));
      cache.evict(Fqn.fromString("/a"));

      NodeSPI n = cache.getNode("/a/b");
      assert !n.isChildrenLoaded();

      children = cache.getNode("/a/b").getChildrenNames();
      assertEquals(3, children.size());

      cache.evict(Fqn.fromString("/a/b/3"));
      cache.evict(Fqn.fromString("/a/b/2"));
      // cache.evict(Fqn.fromString("/a/b/1"));
      cache.evict(Fqn.fromString("/a/b"));
      cache.evict(Fqn.fromString("/a"));
      children = cache.getNode("/a/b").getChildrenNames();
      assertEquals(3, children.size());
   }

   public void testLoaderGetChildrenNames() throws Exception
   {


      Fqn f = Fqn.fromString("/a");
      cache.put(f, "k", "v");
      assertEquals("v", loader.get(f).get("k"));
      assertNull(loader.getChildrenNames(f));
   }

   public void testGetKeys() throws Exception
   {

      Fqn f = Fqn.fromString("/a");
      cache.put(f, "one", "one");
      cache.put(f, "two", "two");
      cache.evict(f);
      Set keys = cache.getRoot().getChild(f).getKeys();
      assertEquals("Correct # of keys", 2, keys.size());
      assertTrue("Has key 'one", keys.contains("one"));
      assertTrue("Has key 'two", keys.contains("two"));
   }

   public void testGetData() throws Exception
   {


      Fqn f = Fqn.fromString("/a");
      assert !cache.exists(f);
      assert !loader.exists(f);
      cache.put(f, "one", "one");
      cache.put(f, "two", "two");
      cache.evict(f);
      Map data = cache.getRoot().getChild(f).getData();
      assertEquals("incorrect # of entries", 2, data.size());
      assertEquals("Has key 'one", "one", data.get("one"));
      assertEquals("Has key 'two", "two", data.get("two"));

   }


   public void testRemoveData()
   {

      String key = "/x/y/z/";
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      assertEquals(3, cache.getNode(key).getKeys().size());
      cache.getNode(key).clearData();
      Set keys = cache.getNode(key).getKeys();
      assertEquals(0, keys.size());
      cache.removeNode("/x");
      Object val = cache.get(key, "keyA");
      assertNull(val);
   }


   public void testRemoveData2()
   {

      Set keys;
      Fqn key = Fqn.fromString("/x/y/z/");
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      addDelay();
      keys = cache.getNode(key).getKeys();
      assertEquals(3, keys.size());
      cache.getNode(key).clearData();
      cache.evict(key);
      addDelay();
      keys = cache.getNode(key).getKeys();
      assertNotNull(keys);
      assertEquals(0, keys.size());
   }

   public void testRemoveData3()
   {
      Set keys;
      Fqn key = Fqn.fromString("/x/y/z/");
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      keys = cache.getNode(key).getKeys();
      assertEquals(3, keys.size());
      cache.evict(key);
      cache.getNode(key).clearData();
      keys = cache.getNode(key).getKeys();
      assertEquals("no more keys", 0, keys.size());
   }

   public void testRemoveData4() throws Exception
   {
      Set keys;
      Fqn key = Fqn.fromString("/x/y/z/");
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      keys = cache.getNode(key).getKeys();
      assertEquals(3, keys.size());
      cache.evict(key);
      Map<Object, Object> map = new HashMap<Object, Object>();
      map.put("keyA", "valA");
      map.put("keyB", "valB");
      map.put("keyC", "valC");
      assertEquals(map, loader.get(key));
      Node n = cache.getRoot().getChild(key);
      n.clearData();
      assertEquals(Collections.emptyMap(), loader.get(key));
   }

   public void testReplaceAll() throws Exception
   {


      Set keys;
      Fqn key = Fqn.fromString("/x/y/z/");
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      keys = cache.getNode(key).getKeys();
      assertEquals(3, keys.size());
      cache.evict(key);

      Map<Object, Object> map = new HashMap<Object, Object>();
      map.put("keyA", "valA");
      map.put("keyB", "valB");
      map.put("keyC", "valC");
      Map<Object, Object> newMap = new HashMap<Object, Object>();
      newMap.put("keyD", "valD");
      newMap.put("keyE", "valE");

      assertEquals(map, loader.get(key));
      Node<Object, Object> n = cache.getRoot().getChild(key);
      n.replaceAll(newMap);
      assertEquals(newMap, loader.get(key));
   }

   public void testRemoveKey()
   {

      String key = "/x/y/z/";
      cache.put(key, "keyA", "valA");
      assertEquals(1, cache.getNode(key).getKeys().size());
      cache.put(key, "keyB", "valB");
      assertEquals(2, cache.getNode(key).getKeys().size());
      cache.put(key, "keyC", "valC");
      assertEquals(3, cache.getNode(key).getKeys().size());
      cache.remove(key, "keyA");
      assertEquals(2, cache.getNode(key).getKeys().size());
      cache.removeNode("/x");
   }


   public void testRemoveKey2() throws CacheException
   {

      final String NODE = "/test";
      final String KEY = "key";
      Object retval;
      cache.removeNode(NODE);
      retval = cache.put(NODE, KEY, 10);
      assertNull(retval);
      addDelay();
      retval = cache.remove(NODE, KEY);
      assertEquals(10, retval);
      addDelay();
      retval = cache.remove(NODE, KEY);
      assertNull(retval);
   }

   public void testRemoveKey3() throws CacheException
   {

      final String NODE = "/test";
      final String KEY = "key";
      Object retval;
      cache.removeNode(NODE);
      retval = cache.put(NODE, KEY, 10);
      assertNull(retval);

      cache.evict(Fqn.fromString(NODE));// evicts from memory, but *not* from store
      addDelay();
      retval = cache.remove(NODE, KEY);
      assertEquals(10, retval);

      cache.evict(Fqn.fromString(NODE));// evicts from memory, but *not* from store
      addDelay();
      retval = cache.remove(NODE, KEY);
      assertNull(retval);
   }


   public void testRemove()
   {

      String key = "/x/y/z/";
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      cache.removeNode("/x");
      assertNull(cache.get(key, "keyA"));
      addDelay();
      Set keys = cache.getKeys(key);
      assertNull("got keys " + keys, keys);
      cache.removeNode("/x");
   }


   public void testRemoveRoot()
   {

      assertEquals(0, cache.getRoot().getKeys().size());
      cache.put("/1/2/3/4/5", null);
      cache.put("uno/due/tre", null);
      cache.put("1/2/3/a", null);
      cache.put("/eins/zwei/drei", null);
      cache.put("/one/two/three", null);
      cache.removeNode(Fqn.ROOT);
      assertEquals(0, cache.getRoot().getKeys().size());
   }

   public void testLoaderRemoveRoot() throws Exception
   {


      Fqn f = Fqn.fromString("/a");
      loader.put(f, "k", "v");
      assertTrue(loader.get(f).containsKey("k"));
      loader.remove(f);
      assertNull(loader.get(f));
      loader.put(f, "k", "v");
      assertTrue(loader.get(f).containsKey("k"));
      loader.remove(Fqn.ROOT);
      assertNull("Removing Fqn.ROOT should cause all children to be removed as well", loader.get(f));
   }

   public void testEvictionWithCacheLoader()
   {

      cache.put("/first/second", "key1", "val1");// stored in cache loader
      cache.put("/first/second/third", "key2", "val2");// stored in cache loader
      cache.evict(Fqn.fromString("/first/second"));// doesn't remove node, just data !
      addDelay();
      assertTrue(cache.exists("/first/second/third"));
      assertTrue(cache.exists("/first/second"));
      assertTrue(cache.exists("/first"));
      String val = (String) cache.get("/first/second", "key1");// should be loaded from cache loader
      assertEquals("val1", val);
      assertTrue(cache.exists("/first/second/third"));
      assertTrue(cache.exists("/first/second"));
      assertTrue(cache.exists("/first"));
   }


   public void testEvictionWithCacheLoader2()
   {

      cache.put("/first/second/third", "key1", "val1");// stored in cache loader
      cache.evict(Fqn.fromString("/first/second/third"));// removes node, because there are no children
      addDelay();
      assert (!exists("/first/second/third", null));
      assertTrue(cache.exists("/first/second"));
      assertTrue(cache.exists("/first"));
      String val = (String) cache.get("/first/second/third", "key1");// should be loaded from cache loader
      assertEquals("val1", val);
      assertTrue(cache.exists("/first/second/third"));
      assertTrue(cache.exists("/first/second"));
      assertTrue(cache.exists("/first"));
   }


   public void testEvictionWithGetChildrenNames() throws Exception
   {

      cache.put("/a/1", null);
      cache.put("/a/2", null);
      cache.put("/a/3", null);
      // cache.put("/a/1/tmp", null);
      cache.evict(Fqn.fromString("/a/1"));
      cache.evict(Fqn.fromString("/a/2"));
      cache.evict(Fqn.fromString("/a/3"));
      cache.evict(Fqn.fromString("/a"));
      addDelay();

      TransactionManager mgr = getTransactionManager();

      mgr.begin();
      Set children = cache.getNode("/a").getChildrenNames();

      assertEquals(3, children.size());
      assertTrue(children.contains("1"));
      assertTrue(children.contains("2"));
      assertTrue(children.contains("3"));

      mgr.commit();
   }


   public void testTxPutCommit() throws Exception
   {
      TransactionManager mgr = getTransactionManager();
      mgr.begin();


      cache.put("/one/two/three", "key1", "val1");
      cache.put("/one/two/three/four", "key2", "val2");
      mgr.commit();
      assertNotNull("Cache has node /one/two/three", cache.getNode("/one/two/three").getKeys());
      assertNotNull("Loader has node /one/two/three", loader.get(Fqn.fromString("/one/two/three")));
      Set<?> children = cache.getNode("/one").getChildrenNames();
      assertEquals("Cache has correct number of children", 1, children.size());
      children = loader.getChildrenNames(Fqn.fromString("/one"));
      assertEquals("Loader has correct number of children", 1, children.size());
      cache.removeNode(Fqn.ROOT);
   }

   public void testTxPutRollback() throws Exception
   {
      TransactionManager mgr = getTransactionManager();


      cache.removeNode("/one");
      addDelay();
      mgr.begin();

      cache.put("/one/two/three", "key1", "val1");
      cache.put("/one/two/three/four", "key2", "val2");
      log.debug("NODE1 " + cache.getNode("/one/two/three").getData());
      mgr.rollback();
      log.debug("NODE2 " + cache.getNode("/one/two/three"));
      assertEquals(null, cache.get("/one/two/three", "key1"));
      assertEquals(null, cache.get("/one/two/three/four", "key2"));
      addDelay();
      assertNull("Loader should not have node /one/two/three", loader.get(Fqn.fromString("/one/two/three")));
      assertNull("Cache should not have node /one/two/three", cache.getKeys("/one/two/three"));
      Set<?> children = cache.getChildrenNames("/one");
      assertEquals("Cache has no children under /one", 0, children.size());
      children = loader.getChildrenNames(Fqn.fromString("/one"));
      assertEquals("Loader has no children under /one", null, children);
   }


   /**
    * Tests basic operations without a transaction.
    */
   public void testBasicOperations()
         throws Exception
   {

      doTestBasicOperations();
   }

   /**
    * Tests basic operations with a transaction.
    */
   public void testBasicOperationsTransactional()
         throws Exception
   {

      TransactionManager mgr = getTransactionManager();
      mgr.begin();
      doTestBasicOperations();
      mgr.commit();
   }

   /**
    * Tests basic operations.
    */
   private void doTestBasicOperations() throws Exception
   {

      /* One FQN only. */
      doPutTests(Fqn.fromString("/key"));
      doRemoveTests(Fqn.fromString("/key"));
      // assertEquals(0, loader.loadEntireState().length);

      /* Add three FQNs, middle FQN last. */
      doPutTests(Fqn.fromString("/key1"));
      doPutTests(Fqn.fromString("/key3"));
      doPutTests(Fqn.fromString("/key2"));
      assertEquals(4, loader.get(Fqn.fromString("/key1")).size());
      assertEquals(4, loader.get(Fqn.fromString("/key2")).size());
      assertEquals(4, loader.get(Fqn.fromString("/key3")).size());

      /* Remove middle FQN first, then the others. */
      doRemoveTests(Fqn.fromString("/key2"));
      doRemoveTests(Fqn.fromString("/key3"));
      doRemoveTests(Fqn.fromString("/key1"));
      assertNull(loader.get(Fqn.fromString("/key1")));
      assertNull(loader.get(Fqn.fromString("/key2")));
      assertNull(loader.get(Fqn.fromString("/key3")));
      // assertEquals(0, loader.loadEntireState().length);
   }

   /**
    * Do basic put tests for a given FQN.
    */
   private void doPutTests(Fqn fqn)
         throws Exception
   {


      assertTrue(!loader.exists(fqn));

      /* put(Fqn,Object,Object) and get(Fqn,Object) */
      Object oldVal;
      oldVal = loader.put(fqn, "one", "two");
      assertNull(oldVal);
      addDelay();
      oldVal = loader.put(fqn, "three", "four");
      assertNull(oldVal);
      addDelay();
      assertEquals("two", loader.get(fqn).get("one"));
      assertEquals("four", loader.get(fqn).get("three"));
      addDelay();
      oldVal = loader.put(fqn, "one", "xxx");
      assertEquals("two", oldVal);
      addDelay();
      oldVal = loader.put(fqn, "one", "two");
      assertEquals("xxx", oldVal);

      /* get(Fqn) */
      addDelay();
      Map<Object, Object> map = new HashMap<Object, Object>(loader.get(fqn));
      assertEquals(2, map.size());
      assertEquals("two", map.get("one"));
      assertEquals("four", map.get("three"));

      /* put(Fqn,Map) */
      map.put("five", "six");
      map.put("seven", "eight");
      loader.put(fqn, map);
      addDelay();
      assertEquals("six", loader.get(fqn).get("five"));
      assertEquals("eight", loader.get(fqn).get("seven"));
      assertEquals(map, loader.get(fqn));
      assertEquals(4, map.size());

      assertTrue(loader.exists(fqn));
   }

   /**
    * Do basic remove tests for a given FQN.
    */
   private void doRemoveTests(Fqn fqn)
         throws Exception
   {

      /* remove(Fqn,Object) */
      Object oldVal;
      oldVal = loader.remove(fqn, "one");
      assertEquals("two", oldVal);
      addDelay();
      oldVal = loader.remove(fqn, "five");
      assertEquals("six", oldVal);
      addDelay();
      assertNull(loader.get(fqn).get("one"));
      assertNull(loader.get(fqn).get("five"));
      assertEquals("four", loader.get(fqn).get("three"));
      assertEquals("eight", loader.get(fqn).get("seven"));
      Map map = loader.get(fqn);
      assertEquals(2, map.size());
      assertEquals("four", map.get("three"));
      assertEquals("eight", map.get("seven"));

      /* remove(Fqn) */
      assertTrue(loader.exists(fqn));
      loader.remove(fqn);
      addDelay();
      map = loader.get(fqn);
      assertNull("Should be null", map);
      assertTrue(!loader.exists(fqn));
   }

   /**
    * Tests creating implicit intermediate nodes when a leaf node is created,
    * and tests removing subtrees.
    */
   public void testMultiLevelTree()
         throws Exception
   {

      /* Create top level node implicitly. */
      assertTrue(!loader.exists(Fqn.fromString("/key0")));
      loader.put(Fqn.fromString("/key0/level1/level2"), null);
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/key0/level1/level2")));
      assertTrue(loader.exists(Fqn.fromString("/key0/level1")));
      assertTrue(loader.exists(Fqn.fromString("/key0")));

      /* Remove leaf, leaving implicitly created middle level. */
      loader.put(Fqn.fromString("/key0/x/y"), null);
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/key0/x/y")));
      assertTrue(loader.exists(Fqn.fromString("/key0/x")));
      loader.remove(Fqn.fromString("/key0/x/y"));
      addDelay();
      assertTrue(!loader.exists(Fqn.fromString("/key0/x/y")));
      assertTrue(loader.exists(Fqn.fromString("/key0/x")));

      /* Delete top level to delete everything. */
      loader.remove(Fqn.fromString("/key0"));
      addDelay();
      assertTrue(!loader.exists(Fqn.fromString("/key0")));
      assertTrue(!loader.exists(Fqn.fromString("/key0/level1/level2")));
      assertTrue(!loader.exists(Fqn.fromString("/key0/level1")));
      assertTrue(!loader.exists(Fqn.fromString("/key0/x")));

      /* Add three top level nodes as context. */
      loader.put(Fqn.fromString("/key1"), null);
      loader.put(Fqn.fromString("/key2"), null);
      loader.put(Fqn.fromString("/key3"), null);
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/key1")));
      assertTrue(loader.exists(Fqn.fromString("/key2")));
      assertTrue(loader.exists(Fqn.fromString("/key3")));

      /* Put /key3/level1/level2.  level1 should be implicitly created. */
      assertTrue(!loader.exists(Fqn.fromString("/key3/level1")));
      assertTrue(!loader.exists(Fqn.fromString("/key3/level1/level2")));
      loader.put(Fqn.fromString("/key3/level1/level2"), null);
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/key3/level1/level2")));
      assertTrue(loader.exists(Fqn.fromString("/key3/level1")));

      /* Context nodes should still be intact. */
      assertTrue(loader.exists(Fqn.fromString("/key1")));
      assertTrue(loader.exists(Fqn.fromString("/key2")));
      assertTrue(loader.exists(Fqn.fromString("/key3")));

      /* Remove middle level only. */
      loader.remove(Fqn.fromString("/key3/level1"));
      addDelay();
      assertTrue(!loader.exists(Fqn.fromString("/key3/level1/level2")));
      assertTrue(!loader.exists(Fqn.fromString("/key3/level1")));

      /* Context nodes should still be intact. */
      assertTrue(loader.exists(Fqn.fromString("/key1")));
      assertTrue(loader.exists(Fqn.fromString("/key2")));
      assertTrue(loader.exists(Fqn.fromString("/key3")));

      /* Delete first root, leaving other roots. */
      loader.remove(Fqn.fromString("/key1"));
      addDelay();
      assertTrue(!loader.exists(Fqn.fromString("/key1")));
      assertTrue(loader.exists(Fqn.fromString("/key2")));
      assertTrue(loader.exists(Fqn.fromString("/key3")));

      /* Delete last root, leaving other roots. */
      loader.remove(Fqn.fromString("/key3"));
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/key2")));
      assertTrue(!loader.exists(Fqn.fromString("/key3")));

      /* Delete final root, leaving none. */
      loader.remove(Fqn.fromString("/key2"));
      addDelay();
      assertTrue(!loader.exists(Fqn.fromString("/key0")));
      assertTrue(!loader.exists(Fqn.fromString("/key1")));
      assertTrue(!loader.exists(Fqn.fromString("/key2")));
      assertTrue(!loader.exists(Fqn.fromString("/key3")));

      /* Repeat all tests above using put(Fqn,Object,Object) and get(Fqn) */

      assertNull(loader.get(Fqn.fromString("/key0")));
      loader.put(Fqn.fromString("/key0/level1/level2"), "a", "b");
      addDelay();
      assertNotNull(loader.get(Fqn.fromString("/key0/level1/level2")));
      assertNotNull(loader.get(Fqn.fromString("/key0/level1")));
      assertEquals(0, loader.get(Fqn.fromString("/key0/level1")).size());
      assertEquals(0, loader.get(Fqn.fromString("/key0")).size());

      loader.put(Fqn.fromString("/key0/x/y"), "a", "b");
      addDelay();
      assertNotNull(loader.get(Fqn.fromString("/key0/x/y")));
      assertNotNull(loader.get(Fqn.fromString("/key0/x")));
      assertEquals(0, loader.get(Fqn.fromString("/key0/x")).size());
      loader.remove(Fqn.fromString("/key0/x/y"));
      addDelay();
      assertNull(loader.get(Fqn.fromString("/key0/x/y")));
      assertNotNull(loader.get(Fqn.fromString("/key0/x")));
      assertEquals(0, loader.get(Fqn.fromString("/key0/x")).size());

      loader.remove(Fqn.fromString("/key0"));
      addDelay();
      assertNull(loader.get(Fqn.fromString("/key0")));
      assertNull(loader.get(Fqn.fromString("/key0/level1/level2")));
      assertNull(loader.get(Fqn.fromString("/key0/level1")));
      assertNull(loader.get(Fqn.fromString("/key0/x")));

      loader.put(Fqn.fromString("/key1"), "a", "b");
      loader.put(Fqn.fromString("/key2"), "a", "b");
      loader.put(Fqn.fromString("/key3"), "a", "b");
      addDelay();
      assertNotNull(loader.get(Fqn.fromString("/key1")));
      assertNotNull(loader.get(Fqn.fromString("/key2")));
      assertNotNull(loader.get(Fqn.fromString("/key3")));

      assertNull(loader.get(Fqn.fromString("/key3/level1")));
      assertNull(loader.get(Fqn.fromString("/key3/level1/level2")));
      loader.put(Fqn.fromString("/key3/level1/level2"), "a", "b");
      addDelay();
      assertNotNull(loader.get(Fqn.fromString("/key3/level1/level2")));
      assertNotNull(loader.get(Fqn.fromString("/key3/level1")));
      assertEquals(0, loader.get(Fqn.fromString("/key3/level1")).size());

      assertNotNull(loader.get(Fqn.fromString("/key1")));
      assertNotNull(loader.get(Fqn.fromString("/key2")));
      assertNotNull(loader.get(Fqn.fromString("/key3")));

      loader.remove(Fqn.fromString("/key3/level1"));
      addDelay();
      assertNull(loader.get(Fqn.fromString("/key3/level1/level2")));
      assertNull(loader.get(Fqn.fromString("/key3/level1")));

      assertNotNull(loader.get(Fqn.fromString("/key1")));
      assertNotNull(loader.get(Fqn.fromString("/key2")));
      assertNotNull(loader.get(Fqn.fromString("/key3")));

      loader.remove(Fqn.fromString("/key1"));
      addDelay();
      assertNull(loader.get(Fqn.fromString("/key1")));
      assertNotNull(loader.get(Fqn.fromString("/key2")));
      assertNotNull(loader.get(Fqn.fromString("/key3")));

      loader.remove(Fqn.fromString("/key3"));
      addDelay();
      assertNotNull(loader.get(Fqn.fromString("/key2")));
      assertNull(loader.get(Fqn.fromString("/key3")));

      loader.remove(Fqn.fromString("/key2"));
      addDelay();
      assertNull(loader.get(Fqn.fromString("/key0")));
      assertNull(loader.get(Fqn.fromString("/key1")));
      assertNull(loader.get(Fqn.fromString("/key2")));
      assertNull(loader.get(Fqn.fromString("/key3")));
   }

   /**
    * Tests the getChildrenNames() method.
    */
   public void testGetChildrenNames()
         throws Exception
   {


      checkChildren(Fqn.ROOT, null);
      checkChildren(Fqn.fromString("/key0"), null);

      loader.put(Fqn.fromString("/key0"), null);
      addDelay();
      checkChildren(Fqn.ROOT, new String[]{"key0"});

      loader.put(Fqn.fromString("/key1/x"), null);
      addDelay();
      checkChildren(Fqn.ROOT, new String[]{"key0", "key1"});
      checkChildren(Fqn.fromString("/key1"), new String[]{"x"});

      loader.remove(Fqn.fromString("/key1/x"));
      addDelay();
      checkChildren(Fqn.ROOT, new String[]{"key0", "key1"});
      checkChildren(Fqn.fromString("/key0"), null);
      checkChildren(Fqn.fromString("/key1"), null);

      loader.put(Fqn.fromString("/key0/a"), null);
      loader.put(Fqn.fromString("/key0/ab"), null);
      loader.put(Fqn.fromString("/key0/abc"), null);
      addDelay();
      checkChildren(Fqn.fromString("/key0"),
            new String[]{"a", "ab", "abc"});

      loader.put(Fqn.fromString("/key0/xxx"), null);
      loader.put(Fqn.fromString("/key0/xx"), null);
      loader.put(Fqn.fromString("/key0/x"), null);
      addDelay();
      checkChildren(Fqn.fromString("/key0"),
            new String[]{"a", "ab", "abc", "x", "xx", "xxx"});

      loader.put(Fqn.fromString("/key0/a/1"), null);
      loader.put(Fqn.fromString("/key0/a/2"), null);
      loader.put(Fqn.fromString("/key0/a/2/1"), null);
      addDelay();
      checkChildren(Fqn.fromString("/key0/a/2"), new String[]{"1"});
      checkChildren(Fqn.fromString("/key0/a"), new String[]{"1", "2"});
      checkChildren(Fqn.fromString("/key0"),
            new String[]{"a", "ab", "abc", "x", "xx", "xxx"});
      //
      //      loader.put(Fqn.fromString("/key0/\u0000"), null);
      //      loader.put(Fqn.fromString("/key0/\u0001"), null);
      //      checkChildren(Fqn.fromString("/key0"),
      //                    new String[] { "a", "ab", "abc", "x", "xx", "xxx",
      //                                   "\u0000", "\u0001"});
      //
      //      loader.put(Fqn.fromString("/\u0001"), null);
      //      checkChildren(Fqn.ROOT, new String[] { "key0", "key1", "\u0001" });
      //
      //      loader.put(Fqn.fromString("/\u0001/\u0001"), null);
      //      checkChildren(Fqn.fromString("/\u0001"), new String[] { "\u0001" });
      //
      //      loader.put(Fqn.fromString("/\u0001/\uFFFF"), null);
      //      checkChildren(Fqn.fromString("/\u0001"),
      //                    new String[] { "\u0001", "\uFFFF" });
      //
      //      loader.put(Fqn.fromString("/\u0001/\uFFFF/\u0001"), null);
      //      checkChildren(Fqn.fromString("/\u0001/\uFFFF"),
      //                    new String[] { "\u0001" });
   }

   /**
    * Checks that the given list of children part names is returned.
    */
   private void checkChildren(Fqn fqn, String[] names)
         throws Exception
   {


      Set set = loader.getChildrenNames(fqn);
      if (names != null)
      {
         assertEquals(names.length, set.size());
         for (int i = 0; i < names.length; i += 1)
         {
            assertTrue(set.contains(names[i]));
         }
      }
      else
      {
         assertNull(set);
      }
   }

   /**
    * Tests basic operations without a transaction.
    */
   public void testModifications()
         throws Exception
   {

      doTestModifications();
   }

   /**
    * Tests basic operations with a transaction.
    */
   public void testModificationsTransactional()
         throws Exception
   {

      TransactionManager mgr = getTransactionManager();
      mgr.begin();
      doTestModifications();
      mgr.commit();
   }

   /**
    * Tests modifications.
    */
   private void doTestModifications()
         throws Exception
   {

      /* PUT_KEY_VALUE, PUT_DATA */
      List<Modification> list = createUpdates();
      loader.put(list);
      addDelay();
      checkModifications(list);

      /* REMOVE_KEY_VALUE */
      list = new ArrayList<Modification>();
      Modification mod = new Modification();
      mod.setType(Modification.ModificationType.REMOVE_KEY_VALUE);
      mod.setFqn(FQN);
      mod.setKey("one");
      list.add(mod);
      loader.put(list);
      addDelay();
      checkModifications(list);

      /* REMOVE_NODE */
      list = new ArrayList<Modification>();
      mod = new Modification();
      mod.setType(Modification.ModificationType.REMOVE_NODE);
      mod.setFqn(FQN);
      list.add(mod);
      loader.put(list);
      addDelay();
      checkModifications(list);
      assertNull(loader.get(FQN));

      /* REMOVE_DATA */
      loader.put(FQN, "one", "two");
      list = new ArrayList<Modification>();
      mod = new Modification();
      mod.setType(Modification.ModificationType.REMOVE_DATA);
      mod.setFqn(FQN);
      list.add(mod);
      loader.put(list);
      addDelay();
      checkModifications(list);
   }

   /**
    * Tests a one-phase transaction.
    */
   public void testOnePhaseTransaction()
         throws Exception
   {


      List<Modification> mods = createUpdates();
      loader.prepare(null, mods, true);
      checkModifications(mods);
   }

   /**
    * Tests a two-phase transaction.
    */
   public void testTwoPhaseTransaction()
         throws Exception
   {

      // Object txnKey = new Object();
      TransactionManager mgr = getTransactionManager();
      mgr.begin();
      Transaction tx = mgr.getTransaction();

      List<Modification> mods = createUpdates();
      loader.prepare(tx, mods, false);
      loader.commit(tx);
      addDelay();
      checkModifications(mods);
      mgr.commit();
   }

   /**
    * Tests rollback of a two-phase transaction.
    */
   public void testTransactionRollback() throws Exception
   {

      loader.remove(Fqn.fromString("/"));
      int num;
      try
      {
         ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
         MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
         loader.loadEntireState(os);
         num = baos.size();
      }
      catch (UnsupportedOperationException ex)
      {
         log.info("caught unsupported operation exception that's okay: ", ex);
         return;
      }

      Object txnKey = new Object();
      List<Modification> mods = createUpdates();
      loader.prepare(txnKey, mods, false);
      loader.rollback(txnKey);
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      assertEquals(num, baos.size());
   }

   /**
    * Tests rollback of a two-phase transaction that is mediated by the cache.
    */
   public void testIntegratedTransactionRollback() throws Exception
   {

      loader.remove(Fqn.fromString("/"));
      cache.put(FQN, "K", "V");
      assert loader.get(FQN).get("K").equals("V");
      assert cache.get(FQN, "K").equals("V");      

      // now modify K in a tx
      cache.getTransactionManager().begin();
      cache.put(FQN, "K", "V2");
      assert loader.get(FQN).get("K").equals("V");
      assert cache.get(FQN, "K").equals("V2");
      cache.getTransactionManager().rollback();

      assert loader.get(FQN).get("K").equals("V");
      assert cache.get(FQN, "K").equals("V");
   }

   /**
    * Creates a set of update (PUT_KEY_VALUE, PUT_DATA) modifications.
    */
   private List<Modification> createUpdates()
   {

      List<Modification> list = new ArrayList<Modification>();

      Modification mod = new Modification();
      mod.setType(Modification.ModificationType.PUT_KEY_VALUE);
      mod.setFqn(FQN);
      mod.setKey("one");
      mod.setValue("two");
      list.add(mod);

      mod = new Modification();
      mod.setType(Modification.ModificationType.PUT_KEY_VALUE);
      mod.setFqn(FQN);
      mod.setKey("three");
      mod.setValue("four");
      list.add(mod);

      Map<Object, Object> map = new HashMap<Object, Object>();
      // any call to putAll() will result in all the cached data being written
      map.put("one", "two");
      map.put("three", "four");
      map.put("five", "six");
      map.put("seven", "eight");
      mod = new Modification();
      mod.setType(Modification.ModificationType.PUT_DATA);
      mod.setFqn(FQN);
      mod.setData(map);
      list.add(mod);

      return list;
   }

   /**
    * Checks that a list of modifications was applied.
    */
   private void checkModifications(List<Modification> list) throws Exception
   {


      for (Modification mod : list)
      {
         Fqn fqn = mod.getFqn();
         switch (mod.getType())
         {
            case PUT_KEY_VALUE:
               assertEquals(mod.getValue(), loader.get(fqn).get(mod.getKey()));
               break;
            case PUT_DATA:
               Map map = mod.getData();
               for (Object key : map.keySet())
               {
                  assertEquals(map.get(key), loader.get(fqn).get(key));
               }
               break;
            case REMOVE_KEY_VALUE:
               assertNull(loader.get(fqn).get(mod.getKey()));
               break;
            case REMOVE_DATA:
               map = loader.get(fqn);
               assertNotNull(map);
               assertEquals(0, map.size());
               break;
            case REMOVE_NODE:
               assertNull(loader.get(fqn));
               break;
            default:
               fail("unknown type: " + mod);
               break;
         }
      }
   }

   /**
    * Tests that null keys and values work as for a standard Java Map.
    */
   public void testNullKeysAndValues()
         throws Exception
   {


      loader.put(FQN, null, "x");
      addDelay();
      assertEquals("x", loader.get(FQN).get(null));
      Map<Object, Object> map = loader.get(FQN);
      assertEquals(1, map.size());
      assertEquals("x", map.get(null));

      loader.put(FQN, "y", null);
      addDelay();
      assertNull(loader.get(FQN).get("y"));
      map = loader.get(FQN);
      assertEquals(2, map.size());
      assertEquals("x", map.get(null));
      assertNull(map.get("y"));

      loader.remove(FQN, null);
      addDelay();
      assertNull(loader.get(FQN).get(null));
      assertEquals(1, loader.get(FQN).size());

      loader.remove(FQN, "y");
      addDelay();
      assertNotNull(loader.get(FQN));
      assertEquals(0, loader.get(FQN).size());

      map = new HashMap<Object, Object>();
      map.put(null, null);
      loader.put(FQN, map);
      addDelay();
      Map m = loader.get(FQN);
      m.toString();
      //throw new RuntimeException("MAP " + loader.get(FQN).getClass());
      /*
      assertEquals(map, loader.get(FQN));

      loader.remove(FQN);
      addDelay();
      assertNull(loader.get(FQN));

      map = new HashMap();
      map.put("xyz", null);
      map.put(null, "abc");
      loader.put(FQN, map);
      addDelay();
      assertEquals(map, loader.get(FQN));

      loader.remove(FQN);
      addDelay();
      assertNull(loader.get(FQN))*/
   }

   /**
    * Test non-default database name.
    */
   public void testDatabaseName()
         throws Exception
   {


      loader.put(FQN, "one", "two");
      addDelay();
      assertEquals("two", loader.get(FQN).get("one"));
   }

   /**
    * Test load/store state.
    */
   public void testLoadAndStore()
         throws Exception
   {

      // Empty state
      loader.remove(Fqn.fromString("/"));

      // Use a complex object to ensure that the class catalog is used.
      Complex c1 = new Complex();
      Complex c2 = new Complex(c1);

      // Add objects
      loader.put(FQN, 1, c1);
      loader.put(FQN, 2, c2);
      addDelay();
      assertEquals(c1, loader.get(FQN).get(1));
      assertEquals(c2, loader.get(FQN).get(2));
      assertEquals(2, loader.get(FQN).size());

      // Save state
      byte[] state;
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      try
      {
         loader.loadEntireState(os);
      }
      catch (UnsupportedOperationException ex)
      {
      }
      finally
      {
         cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
         os.close();
         assertTrue(baos.size() > 0);
         state = baos.toByteArray();
      }

      /* Restore state. */
      ByteArrayInputStream bais = new ByteArrayInputStream(state);
      MarshalledValueInputStream is = new MarshalledValueInputStream(bais);
      try
      {
         loader.storeEntireState(is);
      }
      catch (UnsupportedOperationException ex)
      {
      }
      finally
      {
         is.close();
      }

      addDelay();
      assertEquals(c1, loader.get(FQN).get(1));
      assertEquals(c2, loader.get(FQN).get(2));
      assertEquals(2, loader.get(FQN).size());
   }

   /**
    * Complex object whose class description is stored in the class catalog.
    */
   public static class Complex implements Serializable
   {
      /**
       * The serialVersionUID
       */
      private static final long serialVersionUID = -6810871775584708565L;

      Complex nested;

      Complex()
      {
         this(null);
      }

      Complex(Complex nested)
      {
         this.nested = nested;
      }

      public boolean equals(Object o)
      {
         if (!(o instanceof Complex))
         {
            return false;
         }
         Complex x = (Complex) o;
         return (nested != null) ? nested.equals(x.nested)
               : (x.nested == null);
      }

      public int hashCode()
      {
         if (nested == null)
         {
            return super.hashCode();
         }
         else
         {
            return 13 + nested.hashCode();
         }
      }
   }

   public void testRemoveInTransactionCommit() throws Exception
   {


      Fqn fqn = Fqn.fromString("/a/b");
      loader.remove(fqn);
      String key = "key";
      String value = "value";
      cache.put(fqn, key, value);
      loader = cache.getCacheLoaderManager().getCacheLoader();

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));

      cache.getTransactionManager().begin();

      cache.removeNode(fqn);

      cache.getNode(fqn);// forces the node to be loaded from cache loader again
      cache.getTransactionManager().commit();

      log.debug("expect the cache and the loader to be null here.");
      assertEquals(null, cache.get(fqn, key));
      assertEquals(null, loader.get(fqn));
   }


   public void testRemoveInTransactionRollback() throws Exception
   {


      Fqn fqn = Fqn.fromString("/a/b");
      loader.remove(fqn);
      String key = "key";
      String value = "value";

      cache.put(fqn, key, value);
      loader = cache.getCacheLoaderManager().getCacheLoader();

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));

      cache.getTransactionManager().begin();

      cache.removeNode(fqn);
      cache.getNode(fqn);// forces a reload from cloader
      cache.getTransactionManager().rollback();

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));
   }


   /**
    * See http://jira.jboss.com/jira/browse/JBCACHE-352
    */
   public void testRemoveAndGetInTransaction() throws Exception
   {

      Fqn fqn = Fqn.fromString("/a/b");
      String key = "key";
      String value = "value";

      cache.put(fqn, key, value);
      CacheLoader loader = cache.getCacheLoaderManager().getCacheLoader();

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));

      cache.getTransactionManager().begin();

      cache.removeNode(fqn);
      assertNull("Expecting a null since I have already called a remove() - see JBCACHE-352", cache.get(fqn, key));
      cache.getTransactionManager().rollback();

      assertEquals(value, cache.get(fqn, key));
      assertEquals(value, loader.get(fqn).get(key));
   }

   public void testPutAllAfterEvict() throws Exception
   {


      Fqn fqn = Fqn.fromString("/a/b");
      Map<String, String> original = new HashMap<String, String>();
      Map<String, String> toAdd = new HashMap<String, String>();
      Map<String, String> all = new HashMap<String, String>();

      original.put("1", "One");
      original.put("2", "Two");
      toAdd.put("3", "Three");
      toAdd.put("4", "Four");

      all.putAll(original);
      all.putAll(toAdd);

      cache.put(fqn, original);
      assert loader.get(fqn).equals(original);
      cache.evict(fqn);
      assert loader.get(fqn).equals(original);
      cache.put(fqn, toAdd);
      assert loader.get(fqn).equals(all);
      cache.evict(fqn);
      assert loader.get(fqn).equals(all);
   }

   public void testPutAllAfterEvictWithChild() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a/b");
      Map<String, String> original = new HashMap<String, String>();
      Map<String, String> toAdd = new HashMap<String, String>();
      Map<String, String> all = new HashMap<String, String>();

      original.put("1", "One");
      original.put("2", "Two");
      toAdd.put("3", "Three");
      toAdd.put("4", "Four");

      all.putAll(original);
      all.putAll(toAdd);

      cache.put(fqn, original);
      cache.put(Fqn.fromRelativeElements(fqn, "c"), "x", "y"); // put stuff in a child so that evicting fqn will only clear its data map.
      assert loader.get(fqn).equals(original);
      cache.evict(fqn);
      assert loader.get(fqn).equals(original);
      cache.put(fqn, toAdd);
      assert loader.get(fqn).equals(all);
      cache.evict(fqn);
      assert loader.get(fqn).equals(all);
   }


   /**
    * Test load/store state.
    */
   public void testPartialLoadAndStore()
         throws Exception
   {

      /* Empty state. */
      loader.remove(Fqn.fromString("/"));
      // assertEquals(0, loader.loadEntireState().length);
      //      loader.storeEntireState(new byte[0]);
      //      assertEquals(0, loader.loadEntireState().length);
      //      loader.storeEntireState(null);
      //      assertEquals(0, loader.loadEntireState().length);
      //      assertEquals(null, loader.get(FQN));

      /* Use a complex object to ensure that the class catalog is used. */
      Complex c1 = new Complex();
      Complex c2 = new Complex(c1);
      Complex c3 = new Complex();
      Complex c4 = new Complex(c3);

      /* Add objects. */
      loader.put(FQN, 1, c1);
      loader.put(FQN, 2, c2);
      loader.put(SUBTREE_FQN, 1, c3);
      loader.put(SUBTREE_FQN, 2, c4);
      addDelay();
      assertEquals(c1, loader.get(FQN).get(1));
      assertEquals(c2, loader.get(FQN).get(2));
      assertEquals(2, loader.get(FQN).size());
      assertEquals(c3, loader.get(SUBTREE_FQN).get(1));
      assertEquals(c4, loader.get(SUBTREE_FQN).get(2));
      assertEquals(2, loader.get(SUBTREE_FQN).size());

      /* Save state. */
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      loader.loadState(SUBTREE_FQN, os);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      os.close();
      assertTrue(baos.size() > 0);
      loader.remove(SUBTREE_FQN);

      /* Restore state. */

      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      MarshalledValueInputStream is = new MarshalledValueInputStream(bais);
      loader.storeState(SUBTREE_FQN, is);
      is.close();
      addDelay();
      assertEquals(c1, loader.get(FQN).get(1));
      assertEquals(c2, loader.get(FQN).get(2));
      assertEquals(2, loader.get(FQN).size());
      assertEquals(c3, loader.get(SUBTREE_FQN).get(1));
      assertEquals(c4, loader.get(SUBTREE_FQN).get(2));
      assertEquals(2, loader.get(SUBTREE_FQN).size());
   }

   public void testBuddyBackupStore() throws Exception
   {

      /* Empty state. */
      loader.remove(Fqn.ROOT);

      /* Use a complex object to ensure that the class catalog is used. */
      Complex c1 = new Complex();
      Complex c2 = new Complex(c1);
      Complex c3 = new Complex();
      Complex c4 = new Complex(c3);

      /* Add objects. */
      loader.put(FQN, 1, c1);
      loader.put(FQN, 2, c2);
      loader.put(SUBTREE_FQN, 1, c3);
      loader.put(SUBTREE_FQN, 2, c4);
      addDelay();
      assertEquals(c1, loader.get(FQN).get(1));
      assertEquals(c2, loader.get(FQN).get(2));
      assertEquals(2, loader.get(FQN).size());
      assertEquals(c3, loader.get(SUBTREE_FQN).get(1));
      assertEquals(c4, loader.get(SUBTREE_FQN).get(2));
      assertEquals(2, loader.get(SUBTREE_FQN).size());

      /* Save state. */
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      loader.loadState(FQN, os);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      os.close();
      assertTrue(baos.size() > 0);

      /* Restore state. */
      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      MarshalledValueInputStream is = new MarshalledValueInputStream(bais);
      loader.storeState(BUDDY_BASE, is);
      is.close();
      addDelay();
      assertEquals(c1, loader.get(BUDDY_PLUS_FQN).get(1));
      assertEquals(c2, loader.get(BUDDY_PLUS_FQN).get(2));
      assertEquals(2, loader.get(BUDDY_PLUS_FQN).size());
      assertEquals(c3, loader.get(BUDDY_PLUS_SUBTREE_FQN).get(1));
      assertEquals(c4, loader.get(BUDDY_PLUS_SUBTREE_FQN).get(2));
      assertEquals(2, loader.get(BUDDY_PLUS_SUBTREE_FQN).size());

   }

   public void testIgnoreModifications() throws Exception
   {
      Fqn fqn = Fqn.fromString("/a");
      cache.put(fqn, "k", "v");
      assert "v".equals(cache.get(fqn, "k"));
      assert "v".equals(loader.get(fqn).get("k"));

      CacheSPI<Object, Object> secondCache = createUnstartedCache();
      secondCache.getConfiguration().getCacheLoaderConfig().getIndividualCacheLoaderConfigs().get(0).setIgnoreModifications(true);
      secondCache.start();
      CacheLoader secondLoader = secondCache.getCacheLoaderManager().getCacheLoader();
      postConfigure();

      // CCL uses it's own mechanisms to ensure read-only behaviour
      if (!(secondLoader instanceof ChainingCacheLoader))
      {
         // test that the cache loader is wrapped by a read-only delegate
         assert secondLoader instanceof ReadOnlyDelegatingCacheLoader;
      }

      // old state should be persisted.
      assert "v".equals(secondLoader.get(fqn).get("k"));
      assert "v".equals(secondCache.get(fqn, "k"));

      // the loader should now be read-only
      secondCache.put(fqn, "k", "v2");
      assert "v2".equals(secondCache.get(fqn, "k"));
      assert "v".equals(loader.get(fqn).get("k"));
      TestingUtil.killCaches(secondCache);
   }

   public void testCacheLoaderThreadSafety() throws Exception
   {
      threadSafetyTest(true);
   }

   public void testCacheLoaderThreadSafetyMultipleFqns() throws Exception
   {
      threadSafetyTest(false);
   }

   //todo mmarkus add a parameter here to user grater values for different mvn profiles
   protected void threadSafetyTest(final boolean singleFqn) throws Exception
   {
      final CountDownLatch latch = new CountDownLatch(1);
      final Fqn fqn = Fqn.fromString("/a/b/c");
      final List<Fqn> fqns = new ArrayList<Fqn>(30);
      final Random r = new Random();
      if (!singleFqn)
      {
         for (int i = 0; i < 30; i++)
         {
            Fqn f = Fqn.fromString("/a/b/c/" + i);
            fqns.add(f);
            loader.put(f, "k", "v");
         }
      }
      else
      {
         loader.put(fqn, "k", "v");
      }
      final int loops = 100;
      final Set<Exception> exceptions = new CopyOnWriteArraySet<Exception>();

      Thread remover1 = new Thread("Remover-1")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  loader.remove(singleFqn ? fqn : fqns.get(r.nextInt(fqns.size())));
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };

      remover1.start();

      Thread remover2 = new Thread("Remover-2")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  loader.remove(singleFqn ? fqn : fqns.get(r.nextInt(fqns.size())), "k");
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };

      remover2.start();


      Thread reader1 = new Thread("Reader-1")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  loader.get(singleFqn ? fqn : fqns.get(r.nextInt(fqns.size())));
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };
      reader1.start();

      Thread reader2 = new Thread("Reader-2")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  loader.getChildrenNames(singleFqn ? fqn : fqns.get(r.nextInt(fqns.size())));
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };
      reader2.start();


      Thread writer1 = new Thread("Writer-1")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  loader.put(singleFqn ? fqn : fqns.get(r.nextInt(fqns.size())), "k", "v");
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };
      writer1.start();

      Thread writer2 = new Thread("Writer-2")
      {
         public void run()
         {
            try
            {
               latch.await();
               for (int i = 0; i < loops; i++)
               {
                  loader.put(singleFqn ? fqn : fqns.get(r.nextInt(fqns.size())), new HashMap<Object, Object>());
               }
            }
            catch (Exception e)
            {
               exceptions.add(e);
            }
         }
      };
      writer2.start();


      latch.countDown();
      reader1.join();
      reader2.join();
      remover1.join();
      remover2.join();
      writer1.join();
      writer2.join();

      for (Exception e : exceptions) throw e;
   }

   protected TransactionManager getTransactionManager()
   {

      return cache.getConfiguration().getRuntimeConfig().getTransactionManager();
   }

   // TODO: re-enable once we have setData() implemented in 3.1.0

//   public void testSetData() throws Exception
//   {
//      log.info("testSetData");
//      Fqn key = Fqn.fromElements("key");
//      Map<Object, Object> map = new HashMap<Object, Object>();
//      Map<Object, Object> loaderMap;
//      map.put("a", "a");
//      map.put("c", "c");
//      log.info("PUT");
//      cache.put(key, "x", "x");
//      cache.setData(key, map);
//      assertEquals(map, cache.getData(key));
//      log.info("GET");
//      loaderMap = loader.get(key);
//      assertEquals(map, loaderMap);
//
//      assertNull(cache.get(key, "x"));
//      assertEquals("c", cache.get(key, "c"));
//      assertEquals("a", cache.get(key, "a"));
//      loaderMap = loader.get(key);
//      assertEquals(map, loaderMap);
//   }

}
