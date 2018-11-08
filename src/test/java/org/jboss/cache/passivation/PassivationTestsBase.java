package org.jboss.cache.passivation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.*;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.SamplePojo;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.statetransfer.DefaultStateTransferManager;
import org.jboss.cache.transaction.DummyTransactionManager;
import org.jboss.cache.util.TestingUtil;
import org.jboss.util.stream.MarshalledValueInputStream;
import org.jboss.util.stream.MarshalledValueOutputStream;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.*;

/**
 * Base tests for passivation using any of the cache loaders
 *
 * @author <a href="mailto:{hmesha@novell.com}">{Hany Mesha}</a>
 * @version $Id: PassivationTestsBase.java 7735 2009-02-19 13:40:55Z manik.surtani@jboss.com $
 */
@Test(groups = "functional", testName = "passivation.PassivationTestsBase")
abstract public class PassivationTestsBase
{

   Log log = LogFactory.getLog(getClass());

   //Cache Loader fields
   static final Fqn FQN = Fqn.fromString("/key");
   protected CacheLoader loader;
   protected CacheSPI<Object, Object> cache;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setCacheMode("local");

      configureCache();
      cache.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache.getConfiguration().setIsolationLevel(IsolationLevel.SERIALIZABLE);
      cache.create();
      cache.start();
      loader = cache.getCacheLoaderManager().getCacheLoader();
   }

   abstract protected void configureCache() throws Exception;


   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      log.info("**** TEARING DOWN ****");
      if (loader != null) loader.remove(Fqn.ROOT);
      TestingUtil.killCaches(cache);
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

   protected boolean exists(String fqn)
   {
      return exists(fqn, null);
   }

   public void testPutPassivation() throws Exception
   {
      final Fqn NODE = Fqn.fromString("/test");
      final String KEY = "key";
      Object retval;
      cache.removeNode(NODE);// nothing to remove
      addDelay();
      retval = cache.put(NODE, KEY, 10);// put in memory
      assertNull(retval);
      retval = cache.put(NODE, KEY, 20);// put in memory
      addDelay();
      assertEquals(10, retval);// get from memory
      cache.evict(NODE, true);// passivate node
      addDelay();
      retval = cache.put(NODE, KEY, 30);// activate node then does put in memory
      assertFalse(loader.exists(NODE));
      assertEquals(20, retval);
   }

   public void testPut2Passivation() throws Exception
   {
      final Fqn NODE = Fqn.fromString("/a/b/c");
      final String KEY = "key";
      Object retval;
      cache.removeNode(NODE);// nothing to remove
      addDelay();
      retval = cache.put(NODE, KEY, 10);// put in memory
      assertNull(retval);
      addDelay();
      retval = cache.put(NODE, KEY, 20);// put in memory
      assertEquals(10, retval);
      cache.evict(NODE, true);// passivate node
      cache.evict(Fqn.fromString("/a/b"), true);// passivate parent node
      cache.evict(Fqn.fromString("/a"), true);// passivate parent node
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/a/b/c")));
      retval = cache.put(NODE, KEY, 30);// activate node, put in memory new value
      assertFalse(loader.exists(NODE));
      assertEquals(20, retval);
   }


   public void testSerializationPassivation() throws CacheException
   {
      Fqn fqn = Fqn.fromString("/mypojo");
      SamplePojo pojo = new SamplePojo(39, "Hany");
      pojo.getHobbies().add("Running");
      pojo.getHobbies().add("Beerathlon");
      pojo.getHobbies().add("Triathlon");
      cache.put(fqn, 322649, pojo);// put in memory
      addDelay();
      assertNotNull(cache.get(fqn, 322649));// get from memory
      cache.evict(fqn, false);// passivate node
      try
      {
         assertTrue(loader.exists(fqn));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
      SamplePojo pojo2 = (SamplePojo) cache.get(fqn, 322649);// activate node
      try
      {
         assertFalse(loader.exists(fqn));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
      assertNotNull(pojo2);
      assertEquals(39, pojo2.getAge());
      assertEquals("Hany", pojo2.getName());
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

         // force preloading this node from the cache loader.
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

         assert (exists("/1/2/3/4"));
         assert (exists("/a/b/c"));
         assert (!exists("/a/b/c/d"));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
   }


   public void testPreloadingPassivation() throws Exception
   {
      cache.removeNode(Fqn.ROOT);// remove nothing
      cache.put("1/2/3/4/5/d", "key", "val");// put in memory
      cache.evict(Fqn.fromString("1/2/3/4/5/d"));// passivate node
      addDelay();
      try
      {
         assertTrue(loader.exists(Fqn.fromString("1/2/3/4/5/d")));
      }
      catch (Exception e)
      {
         fail(e.toString());
      }
      cache.getNode("1/2/3/4/5/d");// get from loader but doesn't load attributes
      assertEquals(true, loader.exists(Fqn.fromString("1/2/3/4/5/d")));
      assert (exists("1/2/3/4/5/d"));
      cache.get("1/2/3/4/5/d", "key");// activate node
      assertEquals(false, loader.exists(Fqn.fromString("1/2/3/4/5/d")));
   }


   public void testCacheLoading2() throws Exception
   {
      Set<Object> keys = null;
      cache.put("/a/b/c", "key", "val");
      keys = cache.getNode(Fqn.fromString("/a/b/c")).getKeys();
      assertNotNull(keys);
      assertEquals(1, keys.size());
   }


   public void testExists() throws Exception
   {
      cache.put("/eins/zwei/drei", "key1", "val1");
      assert (exists("/eins/zwei/drei"));
      assert (exists("/eins/zwei/drei", "key1"));
      assert (!exists("/eins/zwei/drei", "key2"));
      assert (!exists("/uno/due/tre"));
      assert (!exists("/une/due/tre", "key1"));
   }

   public void testGetChildren() throws Exception
   {
      cache.put("/d/one", null);
      cache.put("/d/two", null);
      cache.put("/d/three", null);
      cache.getNode("/d");
      Set children = cache.getNode("/d").getChildrenNames();
      assertNotNull(children);
      assertEquals(3, children.size());
      assertTrue(children.contains("one"));
      assertTrue(children.contains("two"));
      assertTrue(children.contains("three"));
   }


   public void testGetChildrenWithEvictionPassivation() throws Exception
   {
      cache.put("/a/b/c/1", null);
      cache.put("/a/b/c/2", null);
      cache.put("/a/b/c/3", null);
      cache.evict(Fqn.fromString("/a/b/c/1"));// passivate node
      cache.evict(Fqn.fromString("/a/b/c/2"));// passivate node
      cache.evict(Fqn.fromString("/a/b/c/3"));// passivate node
      cache.evict(Fqn.fromString("/a/b/c"));// passivate node
      cache.evict(Fqn.fromString("/a/b"));// passivate node
      cache.evict(Fqn.fromString("/a"));// passivate node
      cache.evict(Fqn.fromString("/"));// passivate node
      addDelay();
      Set children = cache.getNode("/a/b/c").getChildrenNames();// load node children names
      assertNotNull(children);
      assertEquals(3, children.size());
      assertTrue(children.contains("1"));
      assertTrue(children.contains("2"));
      assertTrue(children.contains("3"));

      assertTrue(loader.exists(Fqn.fromString("/a/b/c")));

      cache.get("/a/b/c/1", "test");// load child
      cache.get("/a/b/c/2", "test");// load child
      cache.get("/a/b/c/3", "test");// load child
      cache.get("/a/b/c", "test");// load attributes

      assertFalse(loader.exists(Fqn.fromString("/a/b/c/1")));
      assertFalse(loader.exists(Fqn.fromString("/a/b/c/2")));
      assertFalse(loader.exists(Fqn.fromString("/a/b/c/3")));
      assertFalse(loader.exists(Fqn.fromString("/a/b/c")));
   }

   public void testGetChildren2()
   {
      try
      {
         cache.put("/1", null);
         cache.put("a", null);
         Set children = cache.getRoot().getChildrenNames();// get root node children names
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
         Set children = cache.getRoot().getChildrenNames();// get children from root node
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
      if (!cache.exists("/a/b/c"))
      {
         cache.put("/a/b/c", null);
      }
      Set children = cache.getChildrenNames((Fqn) null);// get "null* node children names
      assertTrue(children.isEmpty());
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


   public void testGetChildren6Passivation() throws Exception
   {
      cache.put("/a/1", null);// put node in memory
      cache.put("/a/2", null);// put node in memory
      cache.put("/a/3", null);// put node in memory
      cache.evict(Fqn.fromString("/a/1"));// passivate node
      cache.evict(Fqn.fromString("/a/2"));// passivate node
      cache.evict(Fqn.fromString("/a/3"));// passivate node
      cache.evict(Fqn.fromString("/a"));// passivate node
      assertTrue(loader.exists(Fqn.fromString("/a")));
      addDelay();
      assertNotNull(cache.getNode("/a"));// load node
      assertTrue(loader.exists(Fqn.fromString("/a")));// children haven't been loaded
      Set children = cache.getNode("/a").getChildrenNames();
      assertNotNull("No children were loaded", children);
      assertEquals("3 children weren't loaded", 3, children.size());
      cache.get("/a/1", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/1")));
      cache.get("/a/2", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/2")));
      cache.get("/a/3", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/3")));
      cache.get("/a", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a")));
   }

   public void testGetChildren7Passivation() throws Exception
   {
      cache.put("/a/1", null);
      cache.put("/a/2", null);
      cache.put("/a/3", null);
      cache.put("/a", "test", "test");
      cache.evict(Fqn.fromString("/a/1"));// passivate node
      cache.evict(Fqn.fromString("/a/2"));// passivate node
      cache.evict(Fqn.fromString("/a/3"));// passivate node
      cache.evict(Fqn.fromString("/a"));// passivate node
      assert !exists("/a");
      assertTrue(loader.exists(Fqn.fromString("/a")));
      addDelay();
      Object val = cache.get("/a", "test");// load node's attributes but not children
      assertEquals("attributes weren't loaded", "test", val);

      Set children = cache.getNode("/a").getChildrenNames();
      assertNotNull("No children were loaded", children);
      assertEquals("3 children weren't loaded", 3, children.size());
      cache.get("/a/1", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/1")));
      cache.get("/a/2", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/2")));
      cache.get("/a/3", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/3")));
      assertFalse(loader.exists(Fqn.fromString("/a")));
   }

   public void testGetChildren8Passivation() throws Exception
   {
      cache.put("/a/1", null);
      cache.put("/a/2", null);
      cache.put("/a/3", null);
      cache.evict(Fqn.fromString("/a/1"));// passivate node
      cache.evict(Fqn.fromString("/a/2"));// passivate node
      cache.evict(Fqn.fromString("/a/3"));// passivate node
      cache.evict(Fqn.fromString("/a"));// passivate node

      addDelay();
      assertNull(cache.get("/a", "test"));// load attributes only
      assertTrue(loader.exists(Fqn.fromString("/a")));// loaded attibutes but not children

      assertNull(cache.get("/a/1", "test"));// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/1")));// loaded attributes and has no children
      Set children = cache.getNode("/a").getChildrenNames();// load children names
      assertNotNull("No children were loaded", children);
      assertEquals("3 children weren't loaded", 3, children.size());
      assertTrue(loader.exists(Fqn.fromString("/a")));//loaded children but didn't initalizae them
   }

   public void testGetChildren9Passivation() throws Exception
   {
      cache.put("/a/1", null);
      cache.put("/a/2", null);
      cache.put("/a/3", null);
      cache.evict(Fqn.fromString("/a/1"));// passivate node
      cache.evict(Fqn.fromString("/a/2"));// passivate node
      cache.evict(Fqn.fromString("/a/3"));// passivate node
      cache.evict(Fqn.fromString("/a"));// passivate node
      assertTrue(loader.exists(Fqn.fromString("/a")));
      addDelay();

      cache.get("/a/1", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/1")));
      cache.get("/a/2", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/2")));
      cache.get("/a/3", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/3")));
      Set children = cache.getNode("/a").getChildrenNames();// get node's children names
      assertNotNull("No children were loaded", children);
      assertEquals("3 children weren't loaded", 3, children.size());
      assertNull(cache.get("/a", "test"));// load attributes and has no children by now, activation
      assertFalse(loader.exists(Fqn.fromString("/a")));

      cache.evict(Fqn.fromString("/a/1"));// passivate node
      cache.evict(Fqn.fromString("/a/2"));// passivate node
      cache.evict(Fqn.fromString("/a/3"));// passivate node
      cache.evict(Fqn.fromString("/a"));// passivate node
      assertTrue(loader.exists(Fqn.fromString("/a")));

      cache.get("/a/1", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/1")));
      cache.get("/a/2", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/2")));
      cache.get("/a/3", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/3")));
      children = cache.getNode("/a").getChildrenNames();// get children names
      assertNotNull("No children were loaded", children);
      assertEquals("3 children weren't loaded", 3, children.size());
      assertNull(cache.get("/a", "test"));// load attributes and has no children by now, activation
      assertFalse(loader.exists(Fqn.fromString("/a")));
   }


   public void testGetChildren10Passivation() throws Exception
   {
      cache.put("/a/1", null);
      cache.put("/a/2", null);
      cache.put("/a/3", null);
      cache.evict(Fqn.fromString("/a/1"));// passivate node
      cache.evict(Fqn.fromString("/a/2"));// passivate node
      cache.evict(Fqn.fromString("/a/3"));// passivate node
      cache.evict(Fqn.fromString("/a"));// passivate node
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/a")));
      assertNull(cache.get("/a", "test"));// load attributes from loader
      // don't remove from loader though since children may be present
      assertTrue(loader.exists(Fqn.fromString("/a")));

      cache.get("/a/1", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/1")));
      cache.get("/a/2", "test");// activate node
      assertFalse(loader.exists(Fqn.fromString("/a/2")));
      cache.get("/a/3", "test");// passivate node
      assertFalse(loader.exists(Fqn.fromString("/a/3")));
      Set children = cache.getNode("/a").getChildrenNames();
      assertNotNull("No children were loaded", children);
      assertEquals("3 children weren't loaded", 3, children.size());

      assertNull(cache.get("/a", "test"));// activate node
      assertFalse(loader.exists(Fqn.fromString("/a")));
   }

   public void testRemoveData() throws Exception
   {


      String key = "/x/y/z/";
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      assertEquals(3, cache.getNode(key).getKeys().size());
      cache.getNode(key).clearData();
      Set<Object> keys = cache.getNode(key).getKeys();
      assertEquals(0, keys.size());
      cache.removeNode("/x");
      Object val = cache.get(key, "keyA");
      assertNull(val);
   }


   public void testRemoveData2Passivation() throws Exception
   {
      Set<Object> keys;
      Fqn key = Fqn.fromString("/x/y/z/");
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      addDelay();
      keys = cache.getNode(key).getKeys();
      assertEquals(3, keys.size());
      cache.getNode(key).clearData();
      cache.evict(key);// passivate node

      addDelay();
      keys = cache.getNode(key).getKeys();// activate node
      assertFalse(loader.exists(key));
      assertEquals(0, keys.size());
   }

   public void testRemoveData3Passivation() throws Exception
   {
      Set<Object> keys;
      Fqn key = Fqn.fromString("/x/y/z/");
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      keys = cache.getNode(key).getKeys();
      assertEquals(3, keys.size());
      cache.evict(key);// passivate node
      assertTrue(loader.exists(key));
      cache.getNode(key).clearData();
      keys = cache.getNode(key).getKeys();// activate node
      assertFalse(loader.exists(key));
      assertEquals(0, keys.size());
   }

   public void testRemoveKey() throws Exception
   {
      String key = "/x/y/z/";
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      cache.remove(key, "keyA");
      assertEquals(2, cache.getNode(key).getKeys().size());
      cache.removeNode("/x");
   }


   public void testRemoveKey2() throws CacheException
   {
      final Fqn NODE = Fqn.fromString("/test");
      final String KEY = "key";
      Object retval = null;
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

   public void testRemoveKey3Passivation() throws Exception
   {
      final Fqn NODE = Fqn.fromString("/test");
      final String KEY = "key";
      Object retval = null;
      cache.removeNode(NODE);
      retval = cache.put(NODE, KEY, 10);
      assertNull(retval);

      cache.evict(NODE);// passivate node
      addDelay();
      assertTrue(loader.exists(NODE));
      assertEquals(10, loader.get(NODE).get(KEY));
      retval = cache.remove(NODE, KEY);// activate node
      assertEquals(10, retval);
      assertFalse(loader.exists(NODE));

      cache.evict(NODE);// passiave node
      addDelay();
      retval = cache.remove(NODE, KEY);// activate node
      assertFalse(loader.exists(NODE));
      assertNull(retval);
   }


   public void testRemove() throws Exception
   {
      String key = "/x/y/z/";
      cache.put(key, "keyA", "valA");
      cache.put(key, "keyB", "valB");
      cache.put(key, "keyC", "valC");
      cache.removeNode("/x");
      assertNull(cache.get(key, "keyA"));
      addDelay();
      Set<Object> keys = cache.getKeys(key);
      assertNull(keys);
      cache.removeNode("/x");
   }


   public void testRemoveRoot() throws Exception
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


   public void testEvictionWithCacheLoaderPassivation() throws Exception
   {
      cache.put("/first/second", "key1", "val1");
      cache.put("/first/second/third", "key2", "val2");
      cache.evict(Fqn.fromString("/first/second"));// pasivate node to cache loader
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/first/second")));
      assert (exists("/first"));
      String val = (String) cache.get("/first/second", "key1");
      assertFalse(loader.exists(Fqn.fromString("/first/second")));
      assertEquals("val1", val);
      String val2 = (String) cache.get("/first/second/third", "key2");// activate node
      assertFalse(loader.exists(Fqn.fromString("/first/second/third")));
      assertEquals("val2", val2);
      assert (exists("/first/second/third"));
      assert (exists("/first/second"));
      assert (exists("/first"));
   }


   public void testEvictionWithCacheLoaderPassivation2() throws Exception
   {
      cache.put("/first/second/third", "key1", "val1");// stored in cache loader
      cache.evict(Fqn.fromString("/first/second/third"));// passivate node, note: it has no children
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/first/second/third")));
      assert (exists("/first/second"));
      assert (exists("/first"));
      String val = (String) cache.get("/first/second/third", "key1");// activate node
      assertFalse(loader.exists(Fqn.fromString("/first/second/third")));
      assertEquals("val1", val);
      assert (exists("/first/second/third"));
      assert (exists("/first/second"));
      assert (exists("/first"));
   }


   public void testEvictionWithGetChildrenNamesPassivation() throws Exception
   {
      cache.put("/a/1", null);
      cache.put("/a/2", null);
      cache.put("/a/3", null);
      cache.evict(Fqn.fromString("/a/1"));// passivate node
      assertTrue(loader.exists(Fqn.fromString("/a/1")));
      cache.evict(Fqn.fromString("/a/2"));// passivate node
      assertTrue(loader.exists(Fqn.fromString("/a/2")));
      cache.evict(Fqn.fromString("/a/3"));// passivate node
      assertTrue(loader.exists(Fqn.fromString("/a/3")));
      cache.evict(Fqn.fromString("/a"));// passivate node
      assertTrue(loader.exists(Fqn.fromString("/a")));
      addDelay();
      DummyTransactionManager mgr = DummyTransactionManager.getInstance();
      mgr.begin();
      mgr.getTransaction();
      Set<?> children = cache.getNode("/a").getChildrenNames();
      assertEquals(3, children.size());
      assertTrue(children.contains("1"));
      assertTrue(children.contains("2"));
      assertTrue(children.contains("3"));
      mgr.commit();
   }


   public void testPutDataMapAfterPassivation() throws Exception
   {
      Fqn f = Fqn.fromString("/a");
      assert !cache.exists(f);
      assert !loader.exists(f);

      Map<Object, Object> input = new HashMap();
      input.put("one", "one");
      input.put("two", "two");
      cache.put(f, input);

      cache.evict(f);

      input = new HashMap();
      input.put("one", "oneA");
      cache.put(f, input);

      Map data = cache.getRoot().getChild(f).getData();
      assertEquals("incorrect # of entries", 2, data.size());
      assertEquals("Has key 'one", "oneA", data.get("one"));
      assertEquals("Has key 'two", "two", data.get("two"));

   }


   public void testTxPutCommit() throws Exception
   {
      DummyTransactionManager mgr = DummyTransactionManager.getInstance();
      mgr.begin();

      cache.put("/one/two/three", "key1", "val1");
      cache.put("/one/two/three/four", "key2", "val2");

      mgr.commit();

      assertNotNull(cache.getNode("/one/two/three").getKeys());
      assertEquals("val1", cache.get(Fqn.fromString("/one/two/three"), "key1"));
      mgr.begin();

      cache.evict(Fqn.fromString("/one/two/three"));
      cache.evict(Fqn.fromString("/one/two/three/four"));

      mgr.commit();
      assertTrue(loader.exists(Fqn.fromString("/one/two/three")));
      assertTrue(loader.exists(Fqn.fromString("/one/two/three/four")));
      assertNotNull(cache.getNode("/one/two/three").getKeys());
      Set<?> children = cache.getNode("/one").getChildrenNames();
      assertEquals(1, children.size());
      cache.removeNode(Fqn.ROOT);
   }

   public void testTxReadCommit() throws Exception
   {
      DummyTransactionManager mgr = DummyTransactionManager.getInstance();
      mgr.begin();

      cache.put("/one/two/three", "key1", "val1");
      cache.put("/one/two/three/four", "key2", "val2");

      mgr.commit();

      assertNotNull(cache.getNode("/one/two/three").getKeys());
      assertEquals("val1", cache.get(Fqn.fromString("/one/two/three"), "key1"));
      mgr.begin();

      cache.evict(Fqn.fromString("/one/two/three"));
      cache.evict(Fqn.fromString("/one/two/three/four"));

      mgr.commit();
      assertTrue(loader.exists(Fqn.fromString("/one/two/three")));
      assertTrue(loader.exists(Fqn.fromString("/one/two/three/four")));

      // now do a READ in a TX
      mgr.begin();
      assert cache.get("/one/two/three", "key1").equals("val1");
      assert cache.get("/one/two/three/four", "key2").equals("val2");
      mgr.commit();

      // these should NOT exist in the CL anymore!
      assert !loader.exists(Fqn.fromString("/one/two/three/four"));
   }


   public void testTxPutRollback() throws Exception
   {
      DummyTransactionManager mgr = DummyTransactionManager.getInstance();

      cache.removeNode("/one");
      addDelay();
      mgr.begin();

      cache.put("/one/two/three", "key1", "val1");
      cache.put("/one/two/three/four", "key2", "val2");
      mgr.rollback();
      addDelay();
      assertNull(cache.getNode("/one/two/three"));
      assert cache.getNode("/one") == null;

      assertFalse(loader.exists(Fqn.fromString("/one/two/three")));
      assertFalse(loader.exists(Fqn.fromString("/one/two/three/four")));
   }


   public void testPassivationAndActivation() throws Exception
   {
      Object val, val2;
      Fqn NODE = Fqn.fromString("/test");
      loader.remove(Fqn.fromString("/"));
      cache.put(NODE, "key", "val");
      //val=loader.get(NODE).get("key");
      assertNull("value cannot be passivated yet (only on eviction)", loader.get(NODE));
      cache.evict(NODE);
      assertEquals(0, cache.getNumberOfNodes());
      assertEquals(0, cache.getNumberOfAttributes());
      val = loader.get(NODE).get("key");
      assertNotNull("value must have been passivated on evict()", val);
      assertEquals(val, "val");
      val2 = cache.get(NODE, "key");
      assertNotNull(val2);
      assertEquals(val, val2);
      //      val=loader.get(NODE).get("key");
      assertNull("value should have been deleted from store on activation", loader.get(NODE));
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

      DummyTransactionManager mgr = DummyTransactionManager.getInstance();
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
      assertEquals(null, loader.get(Fqn.fromString("/key1")));
      assertEquals(null, loader.get(Fqn.fromString("/key2")));
      assertEquals(null, loader.get(Fqn.fromString("/key3")));
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
      Map<Object, Object> map = loader.get(fqn);
      assertEquals(2, map.size());
      assertEquals("two", map.get("one"));
      assertEquals("four", map.get("three"));

      Map<Object, Object> map2 = new HashMap<Object, Object>(map);
      /* put(Fqn,Map) */
      map2.put("five", "six");
      map2.put("seven", "eight");
      loader.put(fqn, map2);
      addDelay();
      assertEquals("six", loader.get(fqn).get("five"));
      assertEquals("eight", loader.get(fqn).get("seven"));
      assertEquals(map2, loader.get(fqn));
      assertEquals(4, map2.size());

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
      assertEquals(null, loader.get(fqn).get("one"));
      assertEquals(null, loader.get(fqn).get("five"));
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
      assertNull(loader.get(fqn));
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
      Fqn k0 = Fqn.fromString("/key0");

      assertTrue(!loader.exists(k0));
      loader.put(Fqn.fromString("/key0/level1/level2"), null);
      addDelay();
      assertTrue(loader.exists(Fqn.fromString("/key0/level1/level2")));
      assertTrue(loader.exists(Fqn.fromString("/key0/level1")));
      assertTrue(loader.exists(k0));

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
      loader.remove(k0);
      addDelay();
      assertTrue(!loader.exists(k0));
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
      assertTrue(!loader.exists(k0));
      assertTrue(!loader.exists(Fqn.fromString("/key1")));
      assertTrue(!loader.exists(Fqn.fromString("/key2")));
      assertTrue(!loader.exists(Fqn.fromString("/key3")));

      /* Repeat all tests above using put(Fqn,Object,Object) and get(Fqn) */

      assertNull(loader.get(k0));
      loader.put(Fqn.fromString("/key0/level1/level2"), "a", "b");
      addDelay();
      assertNotNull(loader.get(Fqn.fromString("/key0/level1/level2")));
      assertNotNull(loader.get(Fqn.fromString("/key0/level1")));
      assertTrue(loader.get(Fqn.fromString("/key0/level1")).isEmpty());
      assertNotNull(loader.get(k0));
      assertTrue(loader.get(Fqn.fromString("/key0")).isEmpty());

      loader.put(Fqn.fromString("/key0/x/y"), "a", "b");
      addDelay();
      assertNotNull(loader.get(Fqn.fromString("/key0/x/y")));
      assertNotNull(loader.get(Fqn.fromString("/key0/x")));
      assertTrue(loader.get(Fqn.fromString("/key0/x")).isEmpty());
      loader.remove(Fqn.fromString("/key0/x/y"));
      addDelay();
      assertNull(loader.get(Fqn.fromString("/key0/x/y")));
      assertNotNull(loader.get(Fqn.fromString("/key0/x")));
      assertTrue(loader.get(Fqn.fromString("/key0/x")).isEmpty());

      loader.remove(k0);
      addDelay();
      assertNull(loader.get(k0));
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
      assertTrue(loader.get(Fqn.fromString("/key3/level1")).isEmpty());

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
      assertNull(loader.get(k0));
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
      DummyTransactionManager mgr = DummyTransactionManager.getInstance();
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
      assertEquals(null, loader.get(FQN));

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
   public void testTwoPhaseTransactionPassivation()
         throws Exception
   {
      Object txnKey = new Object();
      List<Modification> mods = createUpdates();
      loader.prepare(txnKey, mods, false);
      loader.commit(txnKey);
      addDelay();
      checkModifications(mods);
   }

   /**
    * Tests rollback of a two-phase transaction.
    */
   public void testTransactionRollbackPassivation()
         throws Exception
   {
      loader.remove(Fqn.fromString("/"));

      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      os.close();
      int num = baos.size();

      Object txnKey = new Object();
      List<Modification> mods = createUpdates();
      loader.prepare(txnKey, mods, false);
      loader.rollback(txnKey);

      baos = new ByteArrayOutputStream(1024);
      os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      os.close();

      assertEquals(num, baos.size());
   }

   /**
    * Creates a set of update (PUT_KEY_VALUE, PUT_DATA) modifications.
    */
   private List<Modification> createUpdates()
   {
      List<Modification> list = new ArrayList<Modification>();

      Map<String, String> map = new HashMap<String, String>();
      map.put("five", "six");
      map.put("seven", "eight");
      Modification mod = new Modification();
      mod.setType(Modification.ModificationType.PUT_DATA);
      mod.setFqn(FQN);
      mod.setData(map);
      list.add(mod);

      mod = new Modification();
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

      return list;
   }

   /**
    * Checks that a list of modifications was applied.
    */
   private void checkModifications(List<Modification> list)
         throws Exception
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
               for (Object key : mod.getData().keySet())
               {
                  assertEquals(mod.getData().get(key), loader.get(fqn).get(key));
               }
               break;
            case REMOVE_KEY_VALUE:
               assertEquals(null, loader.get(fqn).get(mod.getKey()));
               break;
            case REMOVE_DATA:
               Map map = loader.get(fqn);
               assertNotNull(map);
               assertTrue(map.isEmpty());
               break;
            case REMOVE_NODE:
               assertEquals(null, loader.get(fqn));
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
      assertEquals(null, loader.get(FQN).get("y"));
      map = loader.get(FQN);
      assertEquals(2, map.size());
      assertEquals("x", map.get(null));
      assertEquals(null, map.get("y"));

      loader.remove(FQN, null);
      addDelay();
      assertEquals(null, loader.get(FQN).get(null));
      assertEquals(1, loader.get(FQN).size());

      loader.remove(FQN, "y");
      addDelay();
      assertNotNull(loader.get(FQN));
      assertNull(loader.get(FQN).get("y"));
      assertEquals(0, loader.get(FQN).size());

      map = new HashMap<Object, Object>();
      map.put(null, null);
      loader.put(FQN, map);
      addDelay();
      assertEquals(map, loader.get(FQN));

      loader.remove(FQN);
      addDelay();
      assertNull(loader.get(FQN));

      map = new HashMap<Object, Object>();
      map.put("xyz", null);
      map.put(null, "abc");
      loader.put(FQN, map);
      addDelay();
      assertEquals(map, loader.get(FQN));

      loader.remove(FQN);
      addDelay();
      assertNull(loader.get(FQN));
   }

   /**
    * Test non-default database name.
    */
   public void testDatabaseNamePassivation()
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
      /* Empty state. */
      loader.remove(Fqn.fromString("/"));

      /* Use a complex object to ensure that the class catalog is used. */
      Complex c1 = new Complex();
      Complex c2 = new Complex(c1);

      /* Add objects. */
      loader.put(FQN, 1, c1);
      loader.put(FQN, 2, c2);
      addDelay();
      assertEquals(c1, loader.get(FQN).get(1));
      assertEquals(c2, loader.get(FQN).get(2));
      assertEquals(2, loader.get(FQN).size());

      /* Save state. */
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      os.close();
      assertTrue(baos.size() > 0);

      byte[] savedState = baos.toByteArray();

      /* Restore state. */
      ByteArrayInputStream bais = new ByteArrayInputStream(savedState);
      MarshalledValueInputStream is = new MarshalledValueInputStream(bais);
      loader.storeEntireState(is);
      is.close();

      addDelay();
      assertEquals(c1, loader.get(FQN).get(1));
      assertEquals(c2, loader.get(FQN).get(2));
      assertEquals(2, loader.get(FQN).size());
   }


   /**
    * Complex object whose class description is stored in the class catalog.
    */
   private static class Complex implements Serializable
   {
      /**
       * The serialVersionUID
       */
      private static final long serialVersionUID = 8950692199236424832L;

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
         try
         {
            Complex x = (Complex) o;
            return (nested != null) ? nested.equals(x.nested)
                  : (x.nested == null);
         }
         catch (ClassCastException e)
         {
            return false;
         }
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
}
