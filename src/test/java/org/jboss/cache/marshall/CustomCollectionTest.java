package org.jboss.cache.marshall;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import static org.jboss.cache.marshall.CustomCollectionTest.MarshallingMode.*;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit test to cover replication and marshalling of custom collections
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, testName = "marshall.CustomCollectionTest")
public class CustomCollectionTest extends RegionBasedMarshallingTestBase implements Serializable
{
   private transient Cache<Object, Object> cache1 = null;
   private transient Cache<Object, Object> cache2 = null;
   private String myListClass = MyList.class.getName();
   private String mySetClass = MySet.class.getName();
   private String myMapClass = MyMap.class.getName();

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      super.setUp();
      cache1 = createCache();
      cache2 = createCache();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
      super.tearDown();
   }

   public void testMap() throws Exception
   {
      doMapTest(DEFAULT_WITHOUT_MARSHALLED_VALUES);
   }

   public void testMapWithRegions() throws Exception
   {
      doMapTest(CUSTOM_CLASSLOADER_WITH_REGIONS);
   }

   public void testMapWithMarshalledValues() throws Exception
   {
      doMapTest(CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES);
   }

   public void testMapWithMarshalledValuesDefaultClassloader() throws Exception
   {
      doMapTest(DEFAULT_WITH_MARSHALLED_VALUES);
   }

   public void testSet() throws Exception
   {
      doSetTest(DEFAULT_WITHOUT_MARSHALLED_VALUES);
   }

   public void testSetWithRegions() throws Exception
   {
      doSetTest(CUSTOM_CLASSLOADER_WITH_REGIONS);
   }

   public void testSetWithMarshalledValues() throws Exception
   {
      doSetTest(CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES);
   }

   public void testSetWithMarshalledValuesDefaultClassloader() throws Exception
   {
      doSetTest(DEFAULT_WITH_MARSHALLED_VALUES);
   }

   public void testList() throws Exception
   {
      doListTest(DEFAULT_WITHOUT_MARSHALLED_VALUES);
   }

   public void testListWithRegions() throws Exception
   {
      doListTest(CUSTOM_CLASSLOADER_WITH_REGIONS);
   }

   public void testListWithMarshalledValues() throws Exception
   {
      doListTest(CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES);
   }

   public void testListWithMarshalledValuesDefaultClassloader() throws Exception
   {
      doListTest(DEFAULT_WITH_MARSHALLED_VALUES);
   }

   enum MarshallingMode
   {
      DEFAULT_WITH_MARSHALLED_VALUES, DEFAULT_WITHOUT_MARSHALLED_VALUES, CUSTOM_CLASSLOADER_WITH_REGIONS, CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES;

      boolean isUsingCustomClassLoader()
      {
         return this == CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES || this == CUSTOM_CLASSLOADER_WITH_REGIONS;
      }
   }

   @SuppressWarnings("unchecked")
   private void doMapTest(MarshallingMode marshallingMode) throws Exception
   {
      ClassLoader customClassLoader = getCollectionsClassLoader();
      Class mapClass = customClassLoader.loadClass(myMapClass);
      Map map = (Map) (marshallingMode.isUsingCustomClassLoader() ? mapClass.newInstance() : new MyMap());

      map.put("k", "v");

      configureCaches(customClassLoader, marshallingMode);

      cache1.start();
      cache2.start();

      if (marshallingMode == CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES)
         Thread.currentThread().setContextClassLoader(customClassLoader);
      cache1.put(fqn("/a"), "key", map);
      Object o = cache2.get(fqn("/a"), "key");
      if (marshallingMode == CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES) resetContextClassLoader();

      assertTrue(o instanceof Map);
      if (marshallingMode.isUsingCustomClassLoader())
      {
         assertNotSame(MyMap.class, o.getClass());
         assertSame(mapClass, o.getClass());
      }
      else
      {
         assertSame(MyMap.class, o.getClass());
         assertNotSame(mapClass, o.getClass());
      }
      assertEquals("v", ((Map) o).get("k"));
   }

   @SuppressWarnings("unchecked")
   private void doSetTest(MarshallingMode marshallingMode) throws Exception
   {
      ClassLoader customClassLoader = getCollectionsClassLoader();
      Class setClass = customClassLoader.loadClass(mySetClass);
      Set set = (Set) (marshallingMode.isUsingCustomClassLoader() ? setClass.newInstance() : new MySet());

      set.add("k");

      configureCaches(customClassLoader, marshallingMode);

      cache1.start();
      cache2.start();

      if (marshallingMode == CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES)
         Thread.currentThread().setContextClassLoader(customClassLoader);
      cache1.put(fqn("/a"), "key", set);
      Object o = cache2.get(fqn("/a"), "key");
      if (marshallingMode == CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES) resetContextClassLoader();

      assertTrue(o instanceof Set);
      if (marshallingMode.isUsingCustomClassLoader())
      {
         assertNotSame(MySet.class, o.getClass());
         assertSame(setClass, o.getClass());
      }
      else
      {
         assertSame(MySet.class, o.getClass());
         assertNotSame(setClass, o.getClass());
      }
      assertTrue(((Set) o).contains("k"));
   }

   @SuppressWarnings("unchecked")
   private void doListTest(MarshallingMode marshallingMode) throws Exception
   {

      ClassLoader customClassLoader = getCollectionsClassLoader();
      Class listClass = customClassLoader.loadClass(myListClass);

      List list = (List) (marshallingMode.isUsingCustomClassLoader() ? listClass.newInstance() : new MyList());

      list.add("k");

      configureCaches(customClassLoader, marshallingMode);

      cache1.start();
      cache2.start();

      if (marshallingMode == CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES)
         Thread.currentThread().setContextClassLoader(customClassLoader);
      cache1.put(fqn("/a"), "key", list);
      Object o = cache2.get(fqn("/a"), "key");
      if (marshallingMode == CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES) resetContextClassLoader();

      assertTrue(o instanceof List);
      if (marshallingMode.isUsingCustomClassLoader())
      {
         assertSame(listClass, o.getClass());
         assertNotSame(MyList.class, o.getClass());
      }
      else
      {
         assertSame(MyList.class, o.getClass());
         assertNotSame(listClass, o.getClass());
      }
      assertTrue(((List) o).contains("k"));
   }

   public void testHarnessClassLoader() throws Exception
   {
      Class myListFromCustomLoader = getCollectionsClassLoader().loadClass(myListClass);
      assertNotNull(myListFromCustomLoader);
      assertFalse(MyList.class.equals(myListFromCustomLoader));

      Object customLoaderMyList = myListFromCustomLoader.newInstance();
      try
      {
         @SuppressWarnings("unused")
         MyList m = (MyList) customLoaderMyList;
         fail("Should have barfed");
      }
      catch (ClassCastException cce)
      {
         // expected
      }
   }

   private void configureCaches(ClassLoader customClassLoader, MarshallingMode marshallingMode)
   {
      switch (marshallingMode)
      {
         case CUSTOM_CLASSLOADER_WITH_REGIONS:
            cache1.getConfiguration().setUseRegionBasedMarshalling(true);
            Region region1 = cache1.getRegion(fqn("/a"), true);
            region1.registerContextClassLoader(customClassLoader);
            cache2.getConfiguration().setUseRegionBasedMarshalling(true);
            Region region2 = cache2.getRegion(fqn("/a"), true);
            region2.registerContextClassLoader(customClassLoader);
            // and carry on to the same setting as DEFAULT_WITHOUT_MARSHALLED_VALUES
         case DEFAULT_WITHOUT_MARSHALLED_VALUES:
            cache1.getConfiguration().setUseLazyDeserialization(false);
            cache2.getConfiguration().setUseLazyDeserialization(false);
            break;
         case DEFAULT_WITH_MARSHALLED_VALUES:
         case CUSTOM_CLASSLOADER_WITH_MARSHALLEDVALUES:
            cache1.getConfiguration().setUseLazyDeserialization(true);
            cache2.getConfiguration().setUseLazyDeserialization(true);
            break;
      }
   }

   private ClassLoader getCollectionsClassLoader()
   {
      String[] includesClasses = {myListClass, mySetClass, myMapClass};
      String[] excludesClasses = {};
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      return new SelectedClassnameClassLoader(includesClasses, excludesClasses, cl);
   }

   private Cache<Object, Object> createCache()
   {
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      Cache<Object, Object> cache = new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      return cache;
   }

   private static Fqn fqn(String fqn)
   {
      return Fqn.fromString(fqn);
   }
}
