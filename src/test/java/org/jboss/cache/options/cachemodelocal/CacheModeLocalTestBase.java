/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options.cachemodelocal;

import org.jboss.cache.*;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.write.PutDataMapCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.commands.write.RemoveKeyCommand;
import org.jboss.cache.commands.write.RemoveNodeCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests the cache mode local override in various scenarios.  To be subclassed to test REPL_SYNC, REPL_ASYNC, INVALIDATION_SYNC, INVALIDATION_ASYNC for Opt and Pess locking.
 * <p/>
 * Option.setCacheModeLocal() only applies to put() and remove() methods.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, testName = "cachemodelocal.CacheModeLocalTestBase")
public abstract class CacheModeLocalTestBase
{
   // to be subclassed.
   protected Configuration.CacheMode cacheMode;
   protected String nodeLockingScheme;
   /**
    * set this to true if the implementing class plans to use an invalidating cache mode *
    */
   protected boolean isInvalidation;
   CacheSPI<String, String> cache1;
   CacheSPI<String, String> cache2;

   NodeSPI<String, String> root1;
   NodeSPI<String, String> root2;

   ReplicationListener replListener1;
   ReplicationListener replListener2;

   private final Fqn fqn = Fqn.fromString("/a");
   private final String key = "key";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {

      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();

      Configuration c = new Configuration();
      c.setClusterName("test");
      c.setStateRetrievalTimeout(10000);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      c.setNodeLockingScheme(nodeLockingScheme);
      c.setCacheMode(cacheMode);
      c.setSerializationExecutorPoolSize(0);

      cache1 = (CacheSPI<String, String>) instance.createCache(c, false, getClass());
      cache1.start();

      c = new Configuration();
      c.setClusterName("test");
      c.setStateRetrievalTimeout(10000);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      c.setNodeLockingScheme(nodeLockingScheme);
      c.setCacheMode(cacheMode);
      c.setSerializationExecutorPoolSize(0);

      cache2 = (CacheSPI<String, String>) instance.createCache(c, false, getClass());
      cache2.start();


      root1 = cache1.getRoot();
      root2 = cache2.getRoot();

      replListener1 = ReplicationListener.getReplicationListener(cache1);
      replListener2 = ReplicationListener.getReplicationListener(cache2);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache1, cache2);
   }

   public void testPutKeyValue() throws Exception
   {
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put(fqn, key, "value");
      Thread.sleep(500);

      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));

      // cache 2 should not
      assertNull("Should be null", cache2.get(fqn, key));

      // now try again with passing the default options
      replListener2.expect(PutKeyValueCommand.class);
      cache1.getInvocationContext().getOptionOverrides().reset();
      cache1.put(fqn, key, "value");
      replListener2.waitForReplicationToOccur();
      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));

      // cache 2 should as well
      if (!isInvalidation)
      {
         assertEquals("value", cache2.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache2.get(fqn, key));
      }

      // now cache2
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache2.put(fqn, key, "value2");
      Thread.sleep(500);
      assertEquals("value2", cache2.get(fqn, key));
      assertEquals("value", cache1.get(fqn, key));

      replListener1.expect(PutKeyValueCommand.class);
      cache2.getInvocationContext().getOptionOverrides().reset();
      cache2.put(fqn, key, "value2");
      replListener1.waitForReplicationToOccur();
      assertEquals("value2", cache2.get(fqn, key));
      if (!isInvalidation)
      {
         assertEquals("value2", cache1.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache1.get(fqn, key));
      }
   }

   public void testPutKeyValueViaNodeAPI() throws Exception
   {
      Node node1 = root1.addChild(fqn);
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      node1.put(key, "value");
      Thread.sleep(500);
      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));

      // cache 2 should not
      assertNull("Should be null", cache2.get(fqn, key));

      // now try again with passing the default options
      replListener2.expect(PutKeyValueCommand.class);
      cache1.getInvocationContext().getOptionOverrides().reset();
      node1.put(key, "value");
      replListener2.waitForReplicationToOccur();
      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));

      // cache 2 should as well
      if (!isInvalidation)
      {
         assertEquals("value", cache2.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache2.get(fqn, key));
      }

      // now cache2
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      Node node2 = root2.addChild(fqn);
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      node2.put(key, "value2");
      Thread.sleep(500);

      assertEquals("value2", cache2.get(fqn, key));
      assertEquals("value", cache1.get(fqn, key));

      replListener1.expect(PutKeyValueCommand.class);
      cache2.getInvocationContext().getOptionOverrides().reset();
      node2.put(key, "value2");
      replListener1.waitForReplicationToOccur();
      assertEquals("value2", cache2.get(fqn, key));
      if (!isInvalidation)
      {
         assertEquals("value2", cache1.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache1.get(fqn, key));
      }
   }

   public void testPutData() throws Exception
   {
      Map<String, String> map = new HashMap<String, String>();
      map.put(key, "value");

      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put(fqn, map);
      Thread.sleep(500);
      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));
      // cache 2 should not
      assertNull("Should be null", cache2.get(fqn, key));

      // now try again with passing the default options
      replListener2.expect(PutDataMapCommand.class);
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(false);
      cache1.put(fqn, map);
      replListener2.waitForReplicationToOccur();
      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));
      // cache 2 should as well
      if (!isInvalidation)
      {
         assertEquals("value", cache2.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache2.get(fqn, key));
      }

      // now cache2
      map.put(key, "value2");
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache2.put(fqn, map);
      Thread.sleep(500);

      assertEquals("value2", cache2.get(fqn, key));
      assertEquals("value", cache1.get(fqn, key));

      replListener1.expect(PutKeyValueCommand.class);
      cache2.getInvocationContext().getOptionOverrides().reset();
      cache2.put(fqn, key, "value2");
      replListener1.waitForReplicationToOccur();
      assertEquals("value2", cache2.get(fqn, key));
      if (!isInvalidation)
      {
         assertEquals("value2", cache1.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache1.get(fqn, key));
      }
   }

   public void testPutDataViaNodeAPI() throws Exception
   {
      Map<String, String> map = new HashMap<String, String>();
      map.put(key, "value");

      Node node1 = root1.addChild(fqn);
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      node1.putAll(map);
      Thread.sleep(500);
      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));
      // cache 2 should not
      assertNull("Should be null", cache2.get(fqn, key));

      // now try again with passing the default options
      replListener2.expect(PutDataMapCommand.class);
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(false);
      node1.putAll(map);
      replListener2.waitForReplicationToOccur();
      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));
      // cache 2 should as well
      if (!isInvalidation)
      {
         assertEquals("value", cache2.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache2.get(fqn, key));
      }

      // now cache2
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      Node node2 = root2.addChild(fqn);
      map.put(key, "value2");
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      node2.putAll(map);
      Thread.sleep(500);

      assertEquals("value2", cache2.get(fqn, key));
      assertEquals("value", cache1.get(fqn, key));

      replListener1.expect(PutKeyValueCommand.class);
      cache2.getInvocationContext().getOptionOverrides().reset();
      node2.put(key, "value2");
      replListener1.waitForReplicationToOccur();
      assertEquals("value2", cache2.get(fqn, key));
      if (!isInvalidation)
      {
         assertEquals("value2", cache1.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache1.get(fqn, key));
      }
   }

   public void testRemoveNode() throws Exception
   {
      // put some stuff in the cache first
      // make sure we cleanup thread local vars.
      replListener2.expect(PutKeyValueCommand.class);
      cache1.getInvocationContext().setOptionOverrides(null);
      cache1.put(fqn, key, "value");
      replListener2.waitForReplicationToOccur();
      assertEquals("value", cache1.get(fqn, key));
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.removeNode(fqn);
      Thread.sleep(500);

      // should be removed in cache1
      assertNull("should be null", cache1.get(fqn, key));
      // Not in cache2
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      // replace cache entries
      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(fqn, key, "value");
      replListener2.waitForReplicationToOccur();
      assertEquals("value", cache1.get(fqn, key));
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      // now try again with passing the default options
      replListener2.expect(RemoveNodeCommand.class);
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(false);
      cache1.removeNode(fqn);
      replListener2.waitForReplicationToOccur();

      // both should be null
      assertNull("should be null", cache1.get(fqn, key));
      assertNull("should be null", cache2.get(fqn, key));
   }

   public void testRemoveNodeViaNodeAPI() throws Exception
   {

      // put some stuff in the cache first
      // make sure we cleanup thread local vars.
      replListener2.expect(PutKeyValueCommand.class);
      cache1.getInvocationContext().setOptionOverrides(null);
      cache1.put(fqn, key, "value");
      assertEquals("value", cache1.get(fqn, key));
      replListener2.waitForReplicationToOccur();
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      root1.removeChild(fqn);
      Thread.sleep(500);

      // should be removed in cache1
      assertNull("should be null", cache1.get(fqn, key));
      // Not in cache2
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      // replace cache entries
      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(fqn, key, "value");
      replListener2.waitForReplicationToOccur();
      assertEquals("value", cache1.get(fqn, key));
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      replListener2.expect(RemoveNodeCommand.class);
      // now try again with passing the default options
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(false);
      root1.removeChild(fqn);
      replListener2.waitForReplicationToOccur();

      // both should be null
      assertNull("should be null", cache1.get(fqn, key));
      assertNull("should be null", cache2.get(fqn, key));
   }

   public void testRemoveKey() throws Exception
   {
      replListener2.expect((Class<? extends ReplicableCommand>) PutKeyValueCommand.class);
      // put some stuff in the cache first
      cache1.getInvocationContext().setOptionOverrides(null);
      cache1.put(fqn, key, "value");
      replListener2.waitForReplicationToOccur();
      assertEquals("value", cache1.get(fqn, key));
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.remove(fqn, key);
      Thread.sleep(500);

      // should be removed in cache1
      assertNull("should be null", cache1.get(fqn, key));
      // Not in cache2
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      replListener2.expect(PutKeyValueCommand.class);
      // replace cache entries
      cache1.put(fqn, key, "value");
      replListener2.waitForReplicationToOccur();
      assertEquals("value", cache1.get(fqn, key));
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      replListener2.expect(RemoveKeyCommand.class);
      // now try again with passing the default options
      cache1.getInvocationContext().getOptionOverrides().reset();
      cache1.remove(fqn, key);
      replListener2.waitForReplicationToOccur();

      // both should be null
      assertNull("should be null", cache1.get(fqn, key));
      assertNull("should be null", cache2.get(fqn, key));
   }

   public void testRemoveKeyViaNodeAPI() throws Exception
   {
      // put some stuff in the cache first
      replListener2.expect(PutDataMapCommand.class, PutKeyValueCommand.class);
      Node node1 = root1.addChild(fqn);
      cache1.getInvocationContext().setOptionOverrides(null);
      node1.put(key, "value");
      replListener2.waitForReplicationToOccur();
      assertEquals("value", cache1.get(fqn, key));
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      node1.remove(key);
      Thread.sleep(500);

      // should be removed in cache1
      assertNull("should be null", cache1.get(fqn, key));
      // Not in cache2
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      // replace cache entries
      replListener2.expect(PutKeyValueCommand.class);
      node1.put(key, "value");
      replListener2.waitForReplicationToOccur();
      assertEquals("value", cache1.get(fqn, key));
      if (isInvalidation)
      {
         assertNull("Should be null", cache2.get(fqn, key));
      }
      else
      {
         assertEquals("value", cache2.get(fqn, key));
      }

      // now try again with passing the default options
      replListener2.expect(RemoveKeyCommand.class);
      cache1.getInvocationContext().getOptionOverrides().reset();
      node1.remove(key);
      replListener2.waitForReplicationToOccur();

      // both should be null
      assertNull("should be null", cache1.get(fqn, key));
      assertNull("should be null", cache2.get(fqn, key));
   }

   public void testTransactionalBehaviourCommit() throws Exception
   {
      TransactionManager mgr = cache1.getTransactionManager();
      replListener2.expectWithTx(PutKeyValueCommand.class);
      mgr.begin();
      cache1.getInvocationContext().getOptionOverrides().reset();
      cache1.put(fqn, key, "value1");
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put(fqn, key, "value2");
      mgr.commit();
      replListener2.waitForReplicationToOccur();
      // cache1 should still have this
      assertEquals("value2", cache1.get(fqn, key));

      if (!isInvalidation)
      {
         assertEquals("value1", cache2.get(fqn, key));
      }
      else
      {
         assertNull(cache2.get(fqn, key));
      }

      replListener2.expectWithTx(PutKeyValueCommand.class);
      // now try again with passing the default options
      mgr.begin();
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put(fqn, key, "value3");
      cache1.getInvocationContext().getOptionOverrides().reset();
      cache1.put(fqn, key, "value");
      mgr.commit();
      replListener2.waitForReplicationToOccur();
      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));

      // cache 2 should as well
      if (!isInvalidation)
      {
         assertEquals("value", cache2.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache2.get(fqn, key));
      }

      // now cache2
      replListener1.expect(PutKeyValueCommand.class);
      mgr = cache2.getTransactionManager();
      mgr.begin();
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(false);
      cache2.put(fqn, key, "value3");
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache2.put(fqn, key, "value2");
      mgr.commit();
      replListener1.waitForReplicationToOccur();

      assertEquals("value2", cache2.get(fqn, key));

      if (!isInvalidation)
      {
         assertEquals("value3", cache1.get(fqn, key));
      }
      else
      {
         assertNull(cache1.get(fqn, key));
      }

      replListener1.expectWithTx(PutKeyValueCommand.class);
      mgr.begin();
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache2.put(fqn, key, "value2");
      cache2.getInvocationContext().getOptionOverrides().reset();
      cache2.put(fqn, key, "value4");
      mgr.commit();
      replListener1.waitForReplicationToOccur();
      assertEquals("value4", cache2.get(fqn, key));
      if (!isInvalidation)
      {
         assertEquals("value4", cache1.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache1.get(fqn, key));
      }

   }

   public void testTransactionalBehaviourRollback() throws Exception
   {
      TransactionManager mgr = cache1.getTransactionManager();

      replListener2.expect(PutKeyValueCommand.class, PutKeyValueCommand.class);
      cache1.put("/a", key, "old");
      cache1.put("/b", key, "old");
      replListener2.waitForReplicationToOccur();


      mgr.begin();
      cache1.getInvocationContext().getOptionOverrides().reset();
      cache1.put("/a", key, "value1");
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put("/b", key, "value2");
      mgr.rollback();
      Thread.sleep(500);
      // cache1 should NOT have this
      assert cache1.get("/a", key).equals("old");
      assert cache1.get("/b", key).equals("old");

      if (isInvalidation)
      {
         assert cache2.get("/a", key) == null;
         assert cache2.get("/b", key) == null;
      }
      else
      {
         assert cache2.get("/a", key).equals("old");
         assert cache2.get("/b", key).equals("old");
      }
   }

   public void testTransactionalBehaviourViaNodeAPI() throws Exception
   {
      replListener2.expect(PutDataMapCommand.class);
      Node node1 = root1.addChild(fqn);
      replListener2.waitForReplicationToOccur();      

      replListener2.expectWithTx(PutKeyValueCommand.class);
      TransactionManager mgr = cache1.getTransactionManager();
      mgr.begin();
      cache1.getInvocationContext().getOptionOverrides().reset();
      node1.put(key, "value1");
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      node1.put(key, "value2");
      mgr.commit();
      replListener2.waitForReplicationToOccur();

      // cache1 should still have this
      assertEquals("value2", cache1.get(fqn, key));

      if (!isInvalidation)
      {
         assertEquals("value1", cache2.get(fqn, key));
      }
      else
      {
         assertNull(cache2.get(fqn, key));
      }

      // now try again with passing the default options
      replListener2.expectWithTx(PutKeyValueCommand.class);
      mgr.begin();
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      node1.put(key, "value3");
      cache1.getInvocationContext().getOptionOverrides().reset();
      node1.put(key, "value");
      mgr.commit();
      replListener2.waitForReplicationToOccur();
      // cache1 should still have this
      assertEquals("value", cache1.get(fqn, key));

      // cache 2 should as well
      if (!isInvalidation)
      {
         assertEquals("value", cache2.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache2.get(fqn, key));
      }

      //do not expect replication for this one as the node is already thre
      Node node2 = root2.addChild(fqn);

      mgr = cache2.getTransactionManager();
      replListener1.expectWithTx(PutKeyValueCommand.class);
      mgr.begin();
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(false);
      node2.put(key, "value3");
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      node2.put(key, "value2");
      mgr.commit();
      replListener1.waitForReplicationToOccur();

      assertEquals("value2", cache2.get(fqn, key));

      if (!isInvalidation)
      {
         assertEquals("value3", cache1.get(fqn, key));
      }
      else
      {
         assertNull(cache1.get(fqn, key));
      }

      replListener1.expectWithTx(PutKeyValueCommand.class);
      mgr.begin();
      cache2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      node2.put(key, "value2");
      cache2.getInvocationContext().getOptionOverrides().reset();
      node2.put(key, "value4");
      mgr.commit();
      replListener1.waitForReplicationToOccur();
      assertEquals("value4", cache2.get(fqn, key));
      if (!isInvalidation)
      {
         assertEquals("value4", cache1.get(fqn, key));
      }
      else
      {
         assertNull("should be invalidated", cache1.get(fqn, key));
      }

   }

   public void testAddChild() throws Exception
   {
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      root1.addChild(fqn);

      // cache1 should still have this
      assertTrue(root1.hasChild(fqn));
      // cache 2 should not
      Node node2 = root2.getChild(fqn);
      assertTrue("Should be null", node2 == null || (isInvalidation && !node2.isValid()));

      // now try again with passing the default options
      replListener2.expect(RemoveNodeCommand.class);
      root1.removeChild(fqn);
      replListener2.waitForReplicationToOccur();

      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(false);

      replListener2.expect(PutDataMapCommand.class);
      root1.addChild(fqn);
      replListener2.waitForReplicationToOccur();

      // cache1 should still have this
      assertTrue(root1.hasChild(fqn));
      // cache 2 should as well
      if (!isInvalidation)
      {
         assertTrue(root2.hasChild(fqn));
      }
      else
      {
         assertTrue("Should be null", node2 == null || !node2.isValid());
      }
   }
}