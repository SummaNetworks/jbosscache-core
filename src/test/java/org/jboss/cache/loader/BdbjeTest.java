package org.jboss.cache.loader;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.Environment;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Modification;
import org.jboss.cache.loader.bdbje.BdbjeCacheLoaderConfig;
import org.jboss.cache.statetransfer.DefaultStateTransferManager;
import org.jboss.util.stream.MarshalledValueInputStream;
import org.jboss.util.stream.MarshalledValueOutputStream;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Tests BdbjeCacheLoader directly via the CacheLoader interface.
 * <p/>
 * <p>Run this test case with the current directory set to the JE environment
 * directory.  Any scratch directory will do, but beware that all files in
 * the directory will be deleted by setUp().</p>
 *
 * @version $Revision: 7332 $
 */
@Test(groups = {"functional"}, testName = "loader.BdbjeTest")
public class BdbjeTest
{
   private static final int STREAM_HEADER_LENGTH = 4;
   private static final String envHome = ".";
   private static final Fqn FQN = Fqn.fromString("/key");

   private CacheSPI cache;
   private CacheLoader loader;

   /**
    * Deletes all files in the environment directory.
    */
   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      File dir = new File(envHome);

      class MyFilter implements FileFilter
      {
         public boolean accept(File file)
         {
            return file.getName().endsWith(".jdb");
         }
      }

      File[] files = dir.listFiles(new MyFilter());

      if (files != null)
      {
         for (int i = 0; i < files.length; i += 1)
         {
            File file = files[i];
            if (file.isFile())
            {
               if (!file.delete())
               {
                  System.err.println("Unable to delete: " + file);
               }
            }
         }
      }
   }

   /**
    * Release all resources and ignore exceptions, to shutdown gracefully
    * when an assertion fires.
    */
   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      if (loader != null)
      {
         try
         {
            loader.stop();
         }
         catch (Exception ignored)
         {
         }
         loader = null;
      }
      if (cache != null)
      {
         try
         {
            TestingUtil.killCaches(cache);
         }
         catch (Exception ignored)
         {
         }
         cache = null;
      }
   }

   /**
    * Creates and starts a loader.
    *
    * @param transactional whether to set the TransactionManagerLookupClass
    *                      property.
    * @param dbName        a database name, or null to default to the cluster name.
    */
   private void startLoader(boolean transactional, String dbName)
         throws Exception
   {

      /*
       * Create a dummy CacheSPI object.  This is used for setting the cluster
       * name and TransactionManagerLookupClass (transactional) propertes only.
       * the CacheSPI object is not used otherwise during testing.
       */
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      cache.getConfiguration().setClusterName("myCluster");
      if (transactional)
      {
         cache.getConfiguration().setTransactionManagerLookupClass(
               "org.jboss.cache.transaction.DummyTransactionManagerLookup");
      }
      cache.start();

      /* Derive the config string. */
      String configStr;
      if (dbName != null)
      {
         configStr = envHome + '#' + dbName;
      }
      else
      {
         configStr = envHome;
         dbName = "myCluster";
      }

      instantiateLoader();

      /* Initialize and start the loader. */
      loader.setCache(cache);
      BdbjeCacheLoaderConfig config = new BdbjeCacheLoaderConfig();
      config.setLocation(configStr);
      loader.setConfig(config);
      loader.create();
      loader.start();

      /* Verify the database name by trying to open it. */
      Environment env = new Environment(new File(envHome), null);
      DatabaseConfig dbConfig = new DatabaseConfig();
      dbConfig.setTransactional(transactional);
      Database db = env.openDatabase(null, dbName, dbConfig);
      db.close();
      env.close();
   }

   /**
    * Creates the loader instance.
    */
   private void instantiateLoader()
         throws Exception
   {

      /* Create the cache loader as CacheSPI would. */
      Class cls =
            Class.forName("org.jboss.cache.loader.bdbje.BdbjeCacheLoader");
      loader = (CacheLoader) cls.newInstance();
   }

   /**
    * Stops and destroys the loader.
    */
   private void stopLoader()
   {

      loader.stop();
      loader.destroy();
   }

   /**
    * Tests basic operations without a transaction.
    */
   public void testBasicOperations()
         throws Exception
   {

      doTestBasicOperations(false);
   }

   /**
    * Tests basic operations with a transaction.
    */
   public void testBasicOperationsTransactional()
         throws Exception
   {

      doTestBasicOperations(true);
   }

   /**
    * Tests basic operations.
    */
   private void doTestBasicOperations(boolean transactional)
         throws Exception
   {

      startLoader(transactional, null);

      /* One FQN only. */
      doPutTests(FQN);
      doRemoveTests(FQN);

      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      os.close();

      /* Add three FQNs, middle FQN last. */
      Fqn k1 = Fqn.fromString("/key1");
      Fqn k2 = Fqn.fromString("/key2");
      Fqn k3 = Fqn.fromString("/key3");

      doPutTests(k1);
      doPutTests(k3);
      doPutTests(k2);
      assertEquals(4, loader.get(k1).size());
      assertEquals(4, loader.get(k2).size());
      assertEquals(4, loader.get(k3).size());

      /* Remove middle FQN first, then the others. */
      doRemoveTests(k2);
      doRemoveTests(k3);
      doRemoveTests(k1);
      assertEquals(null, loader.get(k1));
      assertEquals(null, loader.get(k2));
      assertEquals(null, loader.get(k3));

      baos = new ByteArrayOutputStream(1024);
      os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      os.close();

      stopLoader();
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
      oldVal = loader.put(fqn, "three", "four");
      assertNull(oldVal);
      assertEquals("two", loader.get(fqn).get("one"));
      assertEquals("four", loader.get(fqn).get("three"));
      oldVal = loader.put(fqn, "one", "xxx");
      assertEquals("two", oldVal);
      oldVal = loader.put(fqn, "one", "two");
      assertEquals("xxx", oldVal);

      /* get(Fqn) */
      Map<Object, Object> map = loader.get(fqn);
      assertEquals(2, map.size());
      assertEquals("two", map.get("one"));
      assertEquals("four", map.get("three"));

      /* put(Fqn,Map) */
      map.put("five", "six");
      map.put("seven", "eight");
      loader.put(fqn, map);
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
      oldVal = loader.remove(fqn, "five");
      assertEquals("six", oldVal);
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
      assertNull("Null expected", loader.get(fqn));
      assertTrue(!loader.exists(fqn));
   }

   /**
    * Tests creating implicit intermediate nodes when a leaf node is created,
    * and tests removing subtrees.
    */
   public void testMultiLevelTree()
         throws Exception
   {

      startLoader(false, null);

      /* Create top level node implicitly. */
      Fqn k0 = Fqn.fromString("/key0");
      Fqn k1 = Fqn.fromString("/key1");
      Fqn k2 = Fqn.fromString("/key2");
      Fqn k3 = Fqn.fromString("/key3");

      assertTrue(!loader.exists(k0));
      loader.put(Fqn.fromString("/key0/level1/level2"), null);
      assertTrue(loader.exists(Fqn.fromString("/key0/level1/level2")));
      assertTrue(loader.exists(Fqn.fromString("/key0/level1")));
      assertTrue(loader.exists(k0));

      /* Remove leaf, leaving implicitly created middle level. */
      loader.put(Fqn.fromString("/key0/x/y"), null);
      assertTrue(loader.exists(Fqn.fromString("/key0/x/y")));
      assertTrue(loader.exists(Fqn.fromString("/key0/x")));
      loader.remove(Fqn.fromString("/key0/x/y"));
      assertTrue(!loader.exists(Fqn.fromString("/key0/x/y")));
      assertTrue(loader.exists(Fqn.fromString("/key0/x")));

      /* Delete top level to delete everything. */
      loader.remove(k0);
      assertTrue(!loader.exists(k0));
      assertTrue(!loader.exists(Fqn.fromString("/key0/level1/level2")));
      assertTrue(!loader.exists(Fqn.fromString("/key0/level1")));
      assertTrue(!loader.exists(Fqn.fromString("/key0/x")));

      /* Add three top level nodes as context. */
      loader.put(k1, null);
      loader.put(k2, null);
      loader.put(k3, null);
      assertTrue(loader.exists(k1));
      assertTrue(loader.exists(k2));
      assertTrue(loader.exists(k3));

      /* Put /key3/level1/level2.  level1 should be implicitly created. */
      assertTrue(!loader.exists(Fqn.fromString("/key3/level1")));
      assertTrue(!loader.exists(Fqn.fromString("/key3/level1/level2")));
      loader.put(Fqn.fromString("/key3/level1/level2"), null);
      assertTrue(loader.exists(Fqn.fromString("/key3/level1/level2")));
      assertTrue(loader.exists(Fqn.fromString("/key3/level1")));

      /* Context nodes should still be intact. */
      assertTrue(loader.exists(k1));
      assertTrue(loader.exists(k2));
      assertTrue(loader.exists(k3));

      /* Remove middle level only. */
      loader.remove(Fqn.fromString("/key3/level1"));
      assertTrue(!loader.exists(Fqn.fromString("/key3/level1/level2")));
      assertTrue(!loader.exists(Fqn.fromString("/key3/level1")));

      /* Context nodes should still be intact. */
      assertTrue(loader.exists(k1));
      assertTrue(loader.exists(k2));
      assertTrue(loader.exists(k3));

      /* Delete first root, leaving other roots. */
      loader.remove(k1);
      assertTrue(!loader.exists(k1));
      assertTrue(loader.exists(k2));
      assertTrue(loader.exists(k3));

      /* Delete last root, leaving other roots. */
      loader.remove(k3);
      assertTrue(loader.exists(k2));
      assertTrue(!loader.exists(k3));

      /* Delete final root, leaving none. */
      loader.remove(k2);
      assertTrue(!loader.exists(k0));
      assertTrue(!loader.exists(k1));
      assertTrue(!loader.exists(k2));
      assertTrue(!loader.exists(k3));

      /* Repeat all tests above using put(Fqn,Object,Object) and get(Fqn) */

      assertNull(loader.get(k0));
      loader.put(Fqn.fromString("/key0/level1/level2"), "a", "b");
      assertNotNull(loader.get(Fqn.fromString("/key0/level1/level2")));
      assertNotNull(loader.get(Fqn.fromString("/key0/level1")));
      assertNotNull(loader.get(k0));
      assertEquals(0, loader.get(Fqn.fromString("/key0/level1")).size());
      assertEquals(0, loader.get(k0).size());


      loader.put(Fqn.fromString("/key0/x/y"), "a", "b");
      assertNotNull(loader.get(Fqn.fromString("/key0/x/y")));
      assertNotNull(loader.get(Fqn.fromString("/key0/x")));
      assertEquals(0, loader.get(Fqn.fromString("/key0/x")).size());
      loader.remove(Fqn.fromString("/key0/x/y"));
      assertNull(loader.get(Fqn.fromString("/key0/x/y")));
      assertNotNull(loader.get(Fqn.fromString("/key0/x")));
      assertEquals(0, loader.get(Fqn.fromString("/key0/x")).size());

      loader.remove(k0);
      assertNull(loader.get(k0));
      assertNull(loader.get(Fqn.fromString("/key0/level1/level2")));
      assertNull(loader.get(Fqn.fromString("/key0/level1")));
      assertNull(loader.get(Fqn.fromString("/key0/x")));

      loader.put(k1, "a", "b");
      loader.put(k2, "a", "b");
      loader.put(k3, "a", "b");
      assertNotNull(loader.get(k1));
      assertNotNull(loader.get(k2));
      assertNotNull(loader.get(k3));

      assertNull(loader.get(Fqn.fromString("/key3/level1")));
      assertNull(loader.get(Fqn.fromString("/key3/level1/level2")));
      loader.put(Fqn.fromString("/key3/level1/level2"), "a", "b");
      assertNotNull(loader.get(Fqn.fromString("/key3/level1/level2")));
      assertNotNull(loader.get(Fqn.fromString("/key3/level1")));
      assertEquals(0, loader.get(Fqn.fromString("/key3/level1")).size());

      assertNotNull(loader.get(k1));
      assertNotNull(loader.get(k2));
      assertNotNull(loader.get(k3));

      loader.remove(Fqn.fromString("/key3/level1"));
      assertNull(loader.get(Fqn.fromString("/key3/level1/level2")));
      assertNull(loader.get(Fqn.fromString("/key3/level1")));

      assertNotNull(loader.get(k1));
      assertNotNull(loader.get(k2));
      assertNotNull(loader.get(k3));

      loader.remove(k1);
      assertNull(loader.get(k1));
      assertNotNull(loader.get(k2));
      assertNotNull(loader.get(k3));

      loader.remove(k3);
      assertNotNull(loader.get(k2));
      assertNull(loader.get(k3));

      loader.remove(k2);
      assertNull(loader.get(k0));
      assertNull(loader.get(k1));
      assertNull(loader.get(k2));
      assertNull(loader.get(k3));

      stopLoader();
   }

   /**
    * Tests the getChildrenNames() method.
    */
   public void testGetChildrenNames()
         throws Exception
   {

      startLoader(false, null);

      checkChildren(Fqn.ROOT, null);
      checkChildren(Fqn.fromString("/key0"), null);

      loader.put(Fqn.fromString("/key0"), null);
      checkChildren(Fqn.ROOT, new String[]{"key0"});

      loader.put(Fqn.fromString("/key1/x"), null);
      checkChildren(Fqn.ROOT, new String[]{"key0", "key1"});
      checkChildren(Fqn.fromString("/key1"), new String[]{"x"});

      loader.remove(Fqn.fromString("/key1/x"));
      checkChildren(Fqn.ROOT, new String[]{"key0", "key1"});
      checkChildren(Fqn.fromString("/key0"), null);
      checkChildren(Fqn.fromString("/key1"), null);

      loader.put(Fqn.fromString("/key0/a"), null);
      loader.put(Fqn.fromString("/key0/ab"), null);
      loader.put(Fqn.fromString("/key0/abc"), null);
      checkChildren(Fqn.fromString("/key0"),
            new String[]{"a", "ab", "abc"});

      loader.put(Fqn.fromString("/key0/xxx"), null);
      loader.put(Fqn.fromString("/key0/xx"), null);
      loader.put(Fqn.fromString("/key0/x"), null);
      checkChildren(Fqn.fromString("/key0"),
            new String[]{"a", "ab", "abc", "x", "xx", "xxx"});

      loader.put(Fqn.fromString("/key0/a/1"), null);
      loader.put(Fqn.fromString("/key0/a/2"), null);
      loader.put(Fqn.fromString("/key0/a/2/1"), null);
      checkChildren(Fqn.fromString("/key0/a/2"), new String[]{"1"});
      checkChildren(Fqn.fromString("/key0/a"), new String[]{"1", "2"});
      checkChildren(Fqn.fromString("/key0"),
            new String[]{"a", "ab", "abc", "x", "xx", "xxx"});

      loader.put(Fqn.fromString("/key0/\u0000"), null);
      loader.put(Fqn.fromString("/key0/\u0001"), null);
      checkChildren(Fqn.fromString("/key0"),
            new String[]{"a", "ab", "abc", "x", "xx", "xxx",
                         "\u0000", "\u0001"});

      loader.put(Fqn.fromString("/\u0001"), null);
      checkChildren(Fqn.ROOT, new String[]{"key0", "key1", "\u0001"});

      loader.put(Fqn.fromString("/\u0001/\u0001"), null);
      checkChildren(Fqn.fromString("/\u0001"), new String[]{"\u0001"});

      loader.put(Fqn.fromString("/\u0001/\uFFFF"), null);
      checkChildren(Fqn.fromString("/\u0001"),
            new String[]{"\u0001", "\uFFFF"});

      loader.put(Fqn.fromString("/\u0001/\uFFFF/\u0001"), null);
      checkChildren(Fqn.fromString("/\u0001/\uFFFF"),
            new String[]{"\u0001"});

      stopLoader();
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

      doTestModifications(false);
   }

   /**
    * Tests basic operations with a transaction.
    */
   public void testModificationsTransactional()
         throws Exception
   {

      doTestModifications(true);
   }

   /**
    * Tests modifications.
    */
   private void doTestModifications(boolean transactional)
         throws Exception
   {

      startLoader(transactional, null);

      /* PUT_KEY_VALUE, PUT_DATA */
      List<Modification> list = createUpdates();
      loader.put(list);
      checkModifications(list);

      /* REMOVE_KEY_VALUE */
      list = new ArrayList<Modification>();
      Modification mod = new Modification();
      mod.setType(Modification.ModificationType.REMOVE_KEY_VALUE);
      mod.setFqn(FQN);
      mod.setKey("one");
      list.add(mod);
      loader.put(list);
      checkModifications(list);

      /* REMOVE_NODE */
      list = new ArrayList<Modification>();
      mod = new Modification();
      mod.setType(Modification.ModificationType.REMOVE_NODE);
      mod.setFqn(FQN);
      list.add(mod);
      loader.put(list);
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
      checkModifications(list);
      assertNotNull(loader.get(FQN));
      assertEquals(0, loader.get(FQN).size());

      stopLoader();
   }

   /**
    * Tests a one-phase transaction.
    */
   public void testOnePhaseTransaction()
         throws Exception
   {

      startLoader(true, null);

      List<Modification> mods = createUpdates();
      loader.prepare(null, mods, true);
      checkModifications(mods);

      stopLoader();
   }

   /**
    * Tests a two-phase transaction.
    */
   public void testTwoPhaseTransaction() throws Exception
   {

      startLoader(true, null);

      Object txnKey = new Object();
      List<Modification> mods = createUpdates();
      loader.prepare(txnKey, mods, false);
      long start = System.currentTimeMillis();
      loader.commit(txnKey);
      checkModifications(mods);

      stopLoader();
   }

   /**
    * Tests rollback of a two-phase transaction.
    */
   public void testTransactionRollback()
         throws Exception
   {

      startLoader(true, null);

      Object txnKey = new Object();
      List<Modification> mods = createUpdates();
      loader.prepare(txnKey, mods, false);
      loader.rollback(txnKey);
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      os.close();

      stopLoader();
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

      /*
      Map<String, String> map = new HashMap<String, String>();
      map.put("five", "six");
      map.put("seven", "eight");
      mod = new Modification();
      mod.setType(Modification.ModificationType.PUT_DATA);
      mod.setFqn(FQN);
      mod.setData(map);
      list.add(mod);
      */

      return list;
   }

   /**
    * Checks that a list of modifications was applied.
    */
   private void checkModifications(List<Modification> list)
         throws Exception
   {

      for (int i = 0; i < list.size(); i += 1)
      {
         Modification mod = list.get(i);
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
               assertEquals(null, loader.get(fqn).get(mod.getKey()));
               break;
            case REMOVE_DATA:
               assertTrue(loader.exists(fqn));
               assertNotNull(loader.get(fqn));
               assertEquals(0, loader.get(fqn).size());
               break;
            case REMOVE_NODE:
               assertTrue(!loader.exists(fqn));
               assertEquals(null, loader.get(fqn));
               break;
            default:
               fail("unknown type: " + mod);
               break;
         }
      }
   }

   /**
    * Tests a non-transactional prepare.
    */
   public void testTransactionExceptions()
         throws Exception
   {

      List<Modification> mods = createUpdates();

      /* A non-transactional cache loader should not allow prepare(). */
      startLoader(false, null);
      try
      {
         loader.prepare(new Object(), mods, false);
         fail();
      }
      catch (UnsupportedOperationException expected)
      {
      }
      stopLoader();

      startLoader(true, null);

      /* Commit and rollback a non-prepared transaction. */
      try
      {
         loader.commit(new Object());
         fail();
      }
      catch (IllegalArgumentException expected)
      {
      }
      try
      {
         loader.rollback(new Object());
         fail();
      }
      catch (IllegalArgumentException expected)
      {
      }

      /* Commit and rollback after commit. */

      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      os.close();

      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      MarshalledValueInputStream is = new MarshalledValueInputStream(bais);
      loader.storeEntireState(is);
      is.close();

      Object txnKey = new Object();
      loader.prepare(txnKey, mods, false);
      loader.commit(txnKey);
      try
      {
         loader.commit(txnKey);
         fail();
      }
      catch (IllegalArgumentException expected)
      {
      }
      try
      {
         loader.rollback(txnKey);
         fail();
      }
      catch (IllegalArgumentException expected)
      {
      }

      /* Commit and rollback after rollback. */
      bais = new ByteArrayInputStream(baos.toByteArray());
      is = new MarshalledValueInputStream(bais);
      loader.storeEntireState(is);
      is.close();
      txnKey = new Object();
      loader.prepare(txnKey, mods, false);
      loader.rollback(txnKey);
      try
      {
         loader.rollback(txnKey);
         fail();
      }
      catch (IllegalArgumentException expected)
      {
      }
      try
      {
         loader.rollback(txnKey);
         fail();
      }
      catch (IllegalArgumentException expected)
      {
      }

      stopLoader();
   }

   /**
    * Tests that null keys and values work as for a standard Java Map.
    */
   public void testNullKeysAndValues()
         throws Exception
   {

      startLoader(false, null);

      loader.put(FQN, null, "x");
      assertEquals("x", loader.get(FQN).get(null));
      Map<Object, Object> map = loader.get(FQN);
      assertEquals(1, map.size());
      assertEquals("x", map.get(null));

      loader.put(FQN, "y", null);
      assertEquals(null, loader.get(FQN).get("y"));
      map = loader.get(FQN);
      assertEquals(2, map.size());
      assertEquals("x", map.get(null));
      assertEquals(null, map.get("y"));

      loader.remove(FQN, null);
      assertEquals(null, loader.get(FQN).get(null));
      assertEquals(1, loader.get(FQN).size());

      loader.remove(FQN, "y");
      assertNotNull(loader.get(FQN));
      assertEquals(0, loader.get(FQN).size());

      map = new HashMap<Object, Object>();
      map.put(null, null);
      loader.put(FQN, map);
      assertEquals(map, loader.get(FQN));

      loader.remove(FQN);
      assertNull("Should be null", loader.get(FQN));

      map = new HashMap<Object, Object>();
      map.put("xyz", null);
      map.put(null, "abc");
      loader.put(FQN, map);
      assertEquals(map, loader.get(FQN));

      loader.remove(FQN);
      assertEquals(null, loader.get(FQN));

      stopLoader();
   }

   /**
    * Test non-default database name.
    */
   public void testDatabaseName()
         throws Exception
   {

      startLoader(false, "nonDefaultDbName");
      loader.put(FQN, "one", "two");
      assertEquals("two", loader.get(FQN).get("one"));
      stopLoader();
   }

   /**
    * Test load/store state.
    */
   public void testLoadAndStore() throws Exception
   {
      startLoader(false, null);

      /* Empty state. */
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      MarshalledValueOutputStream os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      os.close();

      ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
      MarshalledValueInputStream is = new MarshalledValueInputStream(bais);
      loader.storeEntireState(is);
      is.close();

      baos = new ByteArrayOutputStream(1024);
      os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      os.close();

      bais = new ByteArrayInputStream(baos.toByteArray());
      is = new MarshalledValueInputStream(bais);
      loader.storeEntireState(is);
      is.close();

      baos = new ByteArrayOutputStream(1024);
      os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      os.close();

      assertEquals(null, loader.get(FQN));

      /* Use a complex object to ensure that the class catalog is used. */
      Complex c1 = new Complex();
      Complex c2 = new Complex(c1);

      /* Add objects. */
      loader.put(FQN, 1, c1);
      loader.put(FQN, 2, c2);
      assertEquals(c1, loader.get(FQN).get(1));
      assertEquals(c2, loader.get(FQN).get(2));
      assertEquals(2, loader.get(FQN).size());

      /* Save state. */
      baos = new ByteArrayOutputStream(1024);
      os = new MarshalledValueOutputStream(baos);
      loader.loadEntireState(os);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      assertTrue(baos.size() > STREAM_HEADER_LENGTH);
      os.close();

      byte[] savedState = baos.toByteArray();

      /* Clear state. */
      baos = new ByteArrayOutputStream(1024);
      os = new MarshalledValueOutputStream(baos);
      cache.getMarshaller().objectToObjectStream(DefaultStateTransferManager.STREAMING_DELIMITER_NODE, os);
      os.close();
      bais = new ByteArrayInputStream(baos.toByteArray());
      is = new MarshalledValueInputStream(bais);
      loader.storeEntireState(is);
      is.close();

      assertEquals(null, loader.get(FQN));

      /* Restore state. */
      bais = new ByteArrayInputStream(savedState);
      is = new MarshalledValueInputStream(bais);
      loader.storeEntireState(is);
      is.close();
      assertEquals(c1, loader.get(FQN).get(1));
      assertEquals(c2, loader.get(FQN).get(2));
      assertEquals(2, loader.get(FQN).size());

      stopLoader();
   }

   /**
    * Complex object whose class description is stored in the class catalog.
    */
   private static class Complex implements Serializable
   {
      /**
       * The serialVersionUID
       */
      private static final long serialVersionUID = -1259096627833244770L;

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
   }
}
