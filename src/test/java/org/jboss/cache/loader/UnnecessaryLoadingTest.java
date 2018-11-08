package org.jboss.cache.loader;

import static org.easymock.EasyMock.*;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional"}, sequential = true, testName = "loader.UnnecessaryLoadingTest")
public class UnnecessaryLoadingTest
{
   private CacheSPI<Object, Object> cache;
   private Fqn parent = Fqn.fromString("/parent");
   private Fqn child = Fqn.fromString("/parent/child");
   private String k = "k", v = "v";
   private CacheLoader mockCacheLoader;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      CacheLoaderConfig clc = new CacheLoaderConfig();
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      clc.addIndividualCacheLoaderConfig(iclc);
      cache.getConfiguration().setCacheLoaderConfig(clc);
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      mockCacheLoader = createMock(CacheLoader.class);

      expect(mockCacheLoader.getConfig()).andReturn(null).anyTimes();
      mockCacheLoader.setCache((CacheSPI) anyObject());
      expectLastCall().anyTimes();
      mockCacheLoader.setConfig((CacheLoaderConfig.IndividualCacheLoaderConfig) anyObject());
      expectLastCall().anyTimes();
      mockCacheLoader.create();
      expectLastCall().anyTimes();
      mockCacheLoader.start();
      expectLastCall().anyTimes();
      mockCacheLoader.stop();
      expectLastCall().anyTimes();
      mockCacheLoader.destroy();
      expectLastCall().anyTimes();
      replay(mockCacheLoader);

      iclc.setCacheLoader(mockCacheLoader);
      cache.start();

      reset(mockCacheLoader);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      reset(mockCacheLoader);
      expect(mockCacheLoader.getConfig()).andReturn(null).anyTimes();
      mockCacheLoader.setCache((CacheSPI) anyObject());
      expectLastCall().anyTimes();
      mockCacheLoader.setConfig((CacheLoaderConfig.IndividualCacheLoaderConfig) anyObject());
      expectLastCall().anyTimes();
      mockCacheLoader.create();
      expectLastCall().anyTimes();
      mockCacheLoader.start();
      expectLastCall().anyTimes();
      mockCacheLoader.stop();
      expectLastCall().anyTimes();
      mockCacheLoader.destroy();
      expectLastCall().anyTimes();
      replay(mockCacheLoader);

      TestingUtil.killCaches(cache);
      cache = null;      
   }

   protected void assertDataLoaded(Fqn f)
   {
      assertTrue("Data should be loaded for node " + f, cache.peek(f, false).isDataLoaded());
   }

   protected void assertDataNotLoaded(Fqn f)
   {
      NodeSPI n = cache.peek(f, true);
      assertFalse("Data should not be loaded for node " + f, n != null && n.isDataLoaded());
   }


   public void testNoLoading() throws Exception
   {
      // we expect these nodes to be stored.
      expect(mockCacheLoader.put(eq(parent), eq(k), eq(v))).andReturn(null);
      expect(mockCacheLoader.put(eq(child), eq(k), eq(v))).andReturn(null);

      // create parent and child with data
      // new nodes being created, will result in loading them from the cache loader first
      expect(mockCacheLoader.get(eq(parent))).andReturn(null);
      expect(mockCacheLoader.get(eq(child))).andReturn(null);
      replay(mockCacheLoader);

      cache.put(parent, k, v);
      cache.put(child, k, v);

      // should be NO cache loading involved whatsoever
      // so no exceptions should be thrown by the mock CL
      verify(mockCacheLoader);
   }

   public void testLoadChild() throws Exception
   {
      // we expect these nodes to be stored.
      expect(mockCacheLoader.put(eq(parent), eq(k), eq(v))).andReturn(null);
      expect(mockCacheLoader.put(eq(child), eq(k), eq(v))).andReturn(null);

      // create parent and child with data
      // new nodes being created, will result in loading them from the cache loader first
      expect(mockCacheLoader.get(eq(parent))).andReturn(null);
      expect(mockCacheLoader.get(eq(child))).andReturn(null).times(2);
      replay(mockCacheLoader);

      cache.put(parent, k, v);
      cache.put(child, k, v);
      cache.evict(child, false);

      // there is no REAL cache loader so this return value should not be tested
      cache.get(child, k);

      // should be NO cache loading involved whatsoever
      // so no exceptions should be thrown by the mock CL
      verify(mockCacheLoader);
   }

   public void testDontLoadChild() throws Exception
   {
      // we expect these nodes to be stored.
      expect(mockCacheLoader.put(eq(parent), eq(k), eq(v))).andReturn(null);
      expect(mockCacheLoader.put(eq(child), eq(k), eq(v))).andReturn(null);

      // create parent and child with data
      // new nodes being created, will result in loading them from the cache loader first
      expect(mockCacheLoader.get(eq(parent))).andReturn(null);
      expect(mockCacheLoader.get(eq(child))).andReturn(null);

      replay(mockCacheLoader);

      cache.put(parent, k, v);
      cache.put(child, k, v);

      // nodes should be marked as having data loaded.
      assertDataLoaded(parent);
      assertDataLoaded(child);

      // now evict the parent
      cache.evict(parent, false);

      // only the evicted parent should have isDataLoaded() set to false.  Not the child.
      assertDataNotLoaded(parent);
      assertDataLoaded(child);
      assertNotNull(cache.peek(child, false));

      // there is no REAL cache loader so this return value should not be tested
      cache.get(child, k);

      // should be NO cache loading involved whatsoever
      verify(mockCacheLoader);
   }

   public void testUnnecessaryMultipleLoading() throws Exception
   {
      Map<Object, Object> m = new HashMap<Object, Object>();
      m.put("foo", "bar");

      // expecting a put on child
      mockCacheLoader.put(eq(child), eq(m));
      expect(mockCacheLoader.get(eq(child))).andReturn(null);
      expect((Set) mockCacheLoader.getChildrenNames(eq(parent))).andReturn(Collections.singleton(child.getLastElementAsString()));
      replay(mockCacheLoader);

      cache.put(child, m);
      assertDataLoaded(child);

      // should load child data
      // mockCacheLoader.expects(once()).method("get").with(eq(child));
      cache.get(child, "foo");
      assertDataLoaded(child);
      cache.get(child, "foo");
      assertDataLoaded(child);
      cache.get(child, "foo2"); // does not exist, will trigger a load
      assertDataLoaded(child);

      // should not load
      Node node = cache.getRoot().getChild(parent);
      assertDataLoaded(child);
      assertDataNotLoaded(parent);

      // needs to load children at this stage in case there are other children that have been evicted.
      Set children = node.getChildren(); //getchildrennames /parent
      assertEquals(1, children.size());
      assertDataLoaded(child);
      cache.get(child, "foo"); //get /parent/child
      assertDataLoaded(child);

      verify(mockCacheLoader);
   }

   public void testDontLoadDataWhenGettingNode() throws Exception
   {
      expect(mockCacheLoader.put(eq(parent), eq(k), eq(v))).andReturn(null);
      expect(mockCacheLoader.get(eq(parent))).andReturn(null);
      expect(mockCacheLoader.exists(eq(parent))).andReturn(true);
      replay(mockCacheLoader);

      cache.put(parent, k, v);
      assertDataLoaded(parent);
      // evict the parent
      cache.evict(parent, false);
      assertNull(cache.peek(parent, false));

      // now get node.
      Node n = cache.getRoot().getChild(parent);
      assertNotNull(n);
      assertDataNotLoaded(parent);

      verify(mockCacheLoader);
   }

   public void testDontLoadDataWhenClearingNode() throws Exception
   {
      expect(mockCacheLoader.put(eq(parent), eq(k), eq(v))).andReturn(null);
      expect(mockCacheLoader.get(eq(parent))).andReturn(null);
      expect(mockCacheLoader.exists(eq(parent))).andReturn(true);
      mockCacheLoader.removeData(eq(parent));
      replay(mockCacheLoader);

      cache.put(parent, k, v);
      assertDataLoaded(parent);
      // evict the parent
      cache.evict(parent, false);
      assertNull(cache.peek(parent, false));

      // now get node.
      Node n = cache.getRoot().getChild(parent);
      assertNotNull(n);
      assertDataNotLoaded(parent);

      // should not load node but should change isDataLoaded to true
      // will trigger a removedata() though
      n.clearData();

      assertDataLoaded(parent);
      verify(mockCacheLoader);
   }

   public void testDontLoadDataWhenReplacingNode() throws Exception
   {
      expect(mockCacheLoader.put(eq(parent), eq(k), eq(v))).andReturn(null);
      expect(mockCacheLoader.get(eq(parent))).andReturn(null);
      expect(mockCacheLoader.exists(eq(parent))).andReturn(true);
      mockCacheLoader.removeData(eq(parent));
      mockCacheLoader.put(eq(parent), eq(Collections.singletonMap((Object) "hello", (Object) "world")));
      replay(mockCacheLoader);

      cache.put(parent, k, v);
      assertTrue(cache.peek(parent, false).isDataLoaded());
      // evict the parent
      cache.evict(parent, false);
      assertNull(cache.peek(parent, false));

      // now get node.
      Node<Object, Object> n = cache.getRoot().getChild(parent);
      assertNotNull(n);
      assertDataNotLoaded(parent);

      // should not load node but should change isDataLoaded to true
      // will trigger a put() though
      // for the moment this does a get as well - which while unnecessary is the best we can do for now until we bring in
      // an AOP framework to work on nodes directly.
      n.replaceAll(Collections.singletonMap((Object) "hello", (Object) "world"));

      assertDataLoaded(parent);
      verify(mockCacheLoader);
   }

   public void testLazyLoadDataWhenWorkingWithNode() throws Exception
   {
      expect(mockCacheLoader.put(eq(parent), eq(k), eq(v))).andReturn(null);
      expect(mockCacheLoader.get(eq(parent))).andReturn(null);
      expect(mockCacheLoader.exists(eq(parent))).andReturn(true);
      expect(mockCacheLoader.get(eq(parent))).andReturn(Collections.singletonMap((Object) k, (Object) v));
      replay(mockCacheLoader);

      cache.put(parent, k, v);
      assertDataLoaded(parent);
      // evict the parent
      cache.evict(parent, false);
      assertNull(cache.peek(parent, false));

      // now get node.
      Node<Object, Object> n = cache.getRoot().getChild(parent);
      assertNotNull(n);
      assertDataNotLoaded(parent);

      // will trigger a load
      assertEquals(v, n.get(k));
      // should change isDataLoaded to true
      assertDataLoaded(parent);

      verify(mockCacheLoader);
   }

   public void testDontLoadWhenKeyInMemory() throws Exception
   {
      Map<Object, Object> m = new HashMap<Object, Object>();
      m.put("k2", "v2");

      expect(mockCacheLoader.get(eq(parent))).andReturn(null);
      expect(mockCacheLoader.put(eq(parent), eq(k), eq(v))).andReturn(null);
      Map<Object, Object> toExpect = new HashMap<Object, Object>(m);
      toExpect.put(k, v);
      mockCacheLoader.put(eq(parent), eq(toExpect));
      expect(mockCacheLoader.get(eq(parent))).andReturn(toExpect);
      replay(mockCacheLoader);
      cache.put(parent, k, v);
      assertDataLoaded(parent);

      // now evict
      cache.evict(parent, false);

      assertDataNotLoaded(parent);

      // should not load
      cache.put(parent, m);

      assertDataLoaded(parent);

      // now a get for an existing key should not trigger a load!
      assertEquals("v2", cache.get(parent, "k2"));

      assertDataLoaded(parent);

      // but going a get for a nonexistent key should!
      assertEquals(v, cache.get(parent, k));

      // should not have overwritten in-memory data
      assertEquals("v2", cache.get(parent, "k2"));
      verify(mockCacheLoader);
   }
}
