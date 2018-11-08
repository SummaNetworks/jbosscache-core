package org.jboss.cache.loader;

import static org.easymock.EasyMock.*;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.util.CachePrinter;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * TODO merge with {@link UnnecessaryLoadingTest}.
 *
 * @author Elias Ross
 * @since 3.0.0
 */
@Test(groups = {"functional", "mvcc"}, enabled = false, testName = "loader.UnnecessaryLoadingSetDataTest", description = "To do with the setData() method on Cache, which will only be valid in 3.1.0.GA.")
public class UnnecessaryLoadingSetDataTest
{
   private CacheSPI<Object, Object> cache;
   private CacheLoader mockCacheLoader;
   private Fqn parent = Fqn.fromString("/parent");

   @DataProvider(name = "locking")
   public Object[][] createData1()
   {
      return new Object[][]{
            // TODO
            // { NodeLockingScheme.PESSIMISTIC },
            {NodeLockingScheme.MVCC},
      };
   }

   private void setUp(NodeLockingScheme locking) throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      CacheLoaderConfig clc = new CacheLoaderConfig();
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      clc.addIndividualCacheLoaderConfig(iclc);
      cache.getConfiguration().setCacheLoaderConfig(clc);
      cache.getConfiguration().setNodeLockingScheme(locking);
      mockCacheLoader = createMockCacheLoader();

      iclc.setCacheLoader(mockCacheLoader);
      cache.start();

      reset(mockCacheLoader);
   }

   public static CacheLoader createMockCacheLoader() throws Exception
   {
      CacheLoader mockCacheLoader = createMock(CacheLoader.class);

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
      return mockCacheLoader;
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

//   @Test(dataProvider = "locking", enabled = false)
   public void testDontLoadWithSetData(NodeLockingScheme locking) throws Exception
   {
      System.err.println(locking);
      System.err.println(locking);
      setUp(locking);

      Map<Object, Object> m0 = new HashMap<Object, Object>();
      m0.put("replace", "replace");
      Map<Object, Object> m1 = new HashMap<Object, Object>();
      m1.put("new", "new");

      mockCacheLoader.put(eq(parent), eq(m0));
      mockCacheLoader.put(eq(parent), eq(m1));
      mockCacheLoader.put(eq(parent), eq(m0));
      mockCacheLoader.get(parent);
      expectLastCall().andStubThrow(new IllegalStateException("no need to call get()"));
      mockCacheLoader.exists(parent);
      expectLastCall().andStubThrow(new IllegalStateException("no need to call exists()"));
      replay(mockCacheLoader);

      assertDataNotLoaded(parent);
//      cache.setData(parent, m0);
      assertDataLoaded(parent);
//      cache.setData(parent, m1);
      assertEquals(m1, cache.peek(parent, false).getData());

      // force removal, see if load happens
      cache.evict(parent);
//      cache.setData(parent, m0);
      // assertDataLoaded(parent);
      assertEquals(m0, cache.peek(parent, false).getData());

      verify(mockCacheLoader);
      CachePrinter.printCacheDetails(cache);
      cache.toString();
   }

}