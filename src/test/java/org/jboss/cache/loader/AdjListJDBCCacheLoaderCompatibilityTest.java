package org.jboss.cache.loader;

import org.jboss.cache.AbstractMultipleCachesTest;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.marshall.NodeData;
import org.jboss.cache.statetransfer.DefaultStateTransferManager;
import org.jboss.cache.util.UnitTestDatabaseManager;
import org.jboss.util.stream.MarshalledValueInputStream;
import org.jboss.util.stream.MarshalledValueOutputStream;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;

/**
 * Tests the compatibility between <tt>JDBCCacheLoader</tt> and <tt>JDBCCacheLoaderOld</tt>. More exactly,
 * it tests whether the new <tt>JDBCCacheLoader</tt> works fine on data previously created
 * <tt>JDBCCacheLoaderOld</tt>.
 *
 * @author Mircea.Markus@iquestint.com
 * @version 1.0
 */
@Test(groups = {"functional"}, testName = "loader.AdjListJDBCCacheLoaderCompatibilityTest")
@SuppressWarnings("deprecation")
public class AdjListJDBCCacheLoaderCompatibilityTest extends AbstractMultipleCachesTest
{

   @SuppressWarnings("deprecation")
   private JDBCCacheLoaderOld oldImpl;
   private JDBCCacheLoader newImpl;
   private CacheSPI cache, cache2;

   protected void createCaches() throws Throwable
   {
      newImpl = getNewCacheLoader((Properties) props.clone());
      oldImpl = getOldLoader((Properties) props.clone());
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(getClass());
      cache2 = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(getClass());
      newImpl.setCache(cache);//this is needed for marshaller
      oldImpl.setCache(cache2);
      registerCaches(cache, cache2);
      oldImpl.start();
      newImpl.start();
   }

   @BeforeMethod
   public void beforeMethod() throws Exception
   {
      oldImpl.remove(Fqn.ROOT);
   }

   private Properties props;

   @BeforeTest
   public void createDatabase()
   {
      props = UnitTestDatabaseManager.getTestDbProperties();
   }

   @AfterTest
   public void shutDownDatabase()
   {
      UnitTestDatabaseManager.shutdownInMemoryDatabase(props);
   }

   public void testCommonOperations() throws Exception
   {
      oldImpl.put(Fqn.fromString("/a/b/c"), "key1", "value1");
      oldImpl.put(Fqn.fromString("/a/b/d"), "key2", "value2");
      oldImpl.put(Fqn.fromString("/a/b/e"), "key3", "value3");
      assertTrue(newImpl.exists(Fqn.fromString("/a/b/c")));
      assertTrue(newImpl.exists(Fqn.fromString("/a/b/d")));
      assertTrue(newImpl.exists(Fqn.fromString("/a/b/e")));
      assertEquals("value1", newImpl.get(Fqn.fromString("/a/b/c")).get("key1"));
      assertEquals("value2", newImpl.get(Fqn.fromString("/a/b/d")).get("key2"));
      assertEquals("value3", newImpl.get(Fqn.fromString("/a/b/e")).get("key3"));
   }

   /**
    * Does the new implementation manage to successfully remove nodes created by old one?
    */
   public void testRemove() throws Exception
   {
      oldImpl.put(Fqn.fromString("/a/b/c"), "key1", "value1");
      oldImpl.put(Fqn.fromString("/a/b/d"), "key2", "value2");
      oldImpl.put(Fqn.fromString("/a/b/e"), "key3", "value3");
      oldImpl.put(Fqn.fromString("/a/f/e"), "key4", "value4");
      assertTrue(newImpl.exists(Fqn.fromString("/a/b/c")));
      assertTrue(newImpl.exists(Fqn.fromString("/a/b/d")));
      assertTrue(newImpl.exists(Fqn.fromString("/a/b/e")));

      newImpl.remove(Fqn.fromString("/a/b"));
      assertFalse(newImpl.exists(Fqn.fromString("/a/b/c")));
      assertFalse(newImpl.exists(Fqn.fromString("/a/b/d")));
      assertFalse(newImpl.exists(Fqn.fromString("/a/b/e")));
      assertTrue(newImpl.exists(Fqn.fromString("/a/f")));
      assertTrue(newImpl.exists(Fqn.fromString("/a/f/e")));
   }

   public void testLoadEntireState() throws Exception
   {
      oldImpl.put(Fqn.fromString("/a/b/c"), "key1", "value1");
      oldImpl.put(Fqn.fromString("/a/b/d"), "key2", "value2");
      oldImpl.put(Fqn.fromString("/a/b/e"), "key3", "value3");
      oldImpl.put(Fqn.fromString("/a/f/e"), "key4", "value4");
      oldImpl.put(Fqn.ROOT, "root_key", "root_value");

      ByteArrayOutputStream newBaos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream newOs = new MarshalledValueOutputStream(newBaos);
      newImpl.start();
      newImpl.loadEntireState(newOs);
      newImpl.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, newOs);
      newOs.close();
      newImpl.remove(Fqn.ROOT);
      assertNull(newImpl.get(Fqn.fromString("/a/b/c")));
      assertNull(newImpl.get(Fqn.fromString("/a/b/d")));
      assertNull(newImpl.get(Fqn.fromString("/a/b/e")));
      assertNull(newImpl.get(Fqn.fromString("/a/f/e")));
      assertNull(newImpl.get(Fqn.ROOT));
      ByteArrayInputStream bais = new ByteArrayInputStream(newBaos.toByteArray());
      MarshalledValueInputStream is = new MarshalledValueInputStream(bais);
      newImpl.storeEntireState(is);
      assertEquals(newImpl.get(Fqn.fromString("/a/b/c")).get("key1"), "value1");
      assertEquals(newImpl.get(Fqn.fromString("/a/b/d")).get("key2"), "value2");
      assertEquals(newImpl.get(Fqn.fromString("/a/b/e")).get("key3"), "value3");
      assertEquals(newImpl.get(Fqn.fromString("/a/f/e")).get("key4"), "value4");
      assertEquals("root_value", newImpl.get(Fqn.ROOT).get("root_key"));
      assertEquals(newImpl.getNodeCount(), 8);
   }

   public void testLoadNodeState() throws Exception
   {
      oldImpl.put(Fqn.fromString("/a/b/c"), "key1", "value1");
      oldImpl.put(Fqn.fromString("/a/b/d"), "key2", "value2");
      oldImpl.put(Fqn.fromString("/a/b/e"), "key3", "value3");
      oldImpl.put(Fqn.fromString("/a/f/e"), "key4", "value4");
      oldImpl.put(Fqn.ROOT, "root_key", "root_value");

      ByteArrayOutputStream newBaos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream newOs = new MarshalledValueOutputStream(newBaos);
      newImpl.start();
      newImpl.loadState(Fqn.fromString("/a/b"), newOs);
      newImpl.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, newOs);
      newOs.close();

      newImpl.remove(Fqn.fromString("/a/b"));
      assertNull(newImpl.get(Fqn.fromString("/a/b/c")));
      assertNull(newImpl.get(Fqn.fromString("/a/b/d")));
      assertNull(newImpl.get(Fqn.fromString("/a/b/e")));
      assertNull(newImpl.get(Fqn.fromString("/a/b")));

      ByteArrayInputStream bais = new ByteArrayInputStream(newBaos.toByteArray());
      MarshalledValueInputStream is = new MarshalledValueInputStream(bais);
      newImpl.storeState(Fqn.fromString("/a/b"), is);

      assertEquals(newImpl.get(Fqn.fromString("/a/b/c")).get("key1"), "value1");
      assertEquals(newImpl.get(Fqn.fromString("/a/b/d")).get("key2"), "value2");
      assertEquals(newImpl.get(Fqn.fromString("/a/b/e")).get("key3"), "value3");
      assertEquals(newImpl.get(Fqn.fromString("/a/f/e")).get("key4"), "value4");
      assertEquals(newImpl.get(Fqn.ROOT).get("root_key"), "root_value");
      assertEquals(newImpl.getNodeCount(), 8);
   }

   /**
    * getNodeDataList is a template method on which the serialisation process relies. We check here that the new
    * implementation works exactelly as the old one.
    */
   public void testGetNodeData() throws Exception
   {
      oldImpl.put(Fqn.fromString("/a/b/c"), "key1", "value1");
      oldImpl.put(Fqn.fromString("/a/b/d"), "key2", "value2");
      oldImpl.put(Fqn.fromString("/a/b/e"), "key3", "value3");
      oldImpl.put(Fqn.fromString("/a/f/e"), "key4", "value4");
      oldImpl.put(Fqn.ROOT, "root_key", "root_value");

      ArrayList<NodeData> oldList = new ArrayList<NodeData>();
      oldImpl.getNodeDataList(Fqn.ROOT, oldList);
      ArrayList<NodeData> newList = new ArrayList<NodeData>();
      newImpl.getNodeDataList(Fqn.ROOT, newList);
      assertEquals(new HashSet<NodeData>(oldList), new HashSet<NodeData>(newList));
   }

   /**
    * Tests performs some backward copatibility work. See {@link JDBCCacheLoader#start()} for details.
    */
   public void testStartWork() throws Exception
   {
      oldImpl.put(Fqn.fromString("/a/b/c"), "key1", "value1");
      oldImpl.put(Fqn.fromString("/a/b/d"), "key2", "value2");
      assertNull(oldImpl.get(Fqn.ROOT));
      newImpl.start();
      assertNotNull(newImpl.get(Fqn.ROOT));
   }


   protected Properties getProperties() throws Exception
   {
      return UnitTestDatabaseManager.getTestDbProperties();
   }

   private JDBCCacheLoader getNewCacheLoader(Properties prop) throws Exception
   {
      String tablePkPrefix = prop.getProperty("cache.jdbc.table.primarykey", "jbosscache_pk");
      prop.setProperty("cache.jdbc.table.primarykey", (tablePkPrefix + 1));

      CacheLoaderConfig.IndividualCacheLoaderConfig base =
            UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", JDBCCacheLoader.class.getName(),
                  prop, false, true, false, false, false).getFirstCacheLoaderConfig();
      JDBCCacheLoader jdbcCacheLoader = new JDBCCacheLoader();
      jdbcCacheLoader.setConfig(base);
      return jdbcCacheLoader;
   }


   private JDBCCacheLoaderOld getOldLoader(Properties prop) throws Exception
   {
      CacheLoaderConfig.IndividualCacheLoaderConfig base =
            UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "",
                  "org.jboss.cache.loader.JDBCCacheLoader", prop, false, true, false, false, false).getFirstCacheLoaderConfig();
      JDBCCacheLoaderOld jdbcCacheLoader = new JDBCCacheLoaderOld();
      jdbcCacheLoader.setConfig(base);
      return jdbcCacheLoader;
   }

}

