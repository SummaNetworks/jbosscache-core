/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.api;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "jgroups", "pessimistic"}, sequential = true, testName = "api.SyncReplTest")
public class SyncReplTest extends AbstractMultipleCachesTest
{
   private CacheSPI<Object, Object> cache1, cache2;
   protected NodeLockingScheme nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;


   protected void createCaches()
   {
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());

      cache1.getConfiguration().setNodeLockingScheme(nodeLockingScheme);
      cache2.getConfiguration().setNodeLockingScheme(nodeLockingScheme);

      configure(cache1.getConfiguration());
      configure(cache2.getConfiguration());

      cache1.start();
      cache2.start();
      TestingUtil.blockUntilViewsReceived(new Cache[]{cache1, cache2}, 5000);

      registerCaches(cache1, cache2);
   }

   protected void configure(Configuration c)
   {
      // to be overridden
   }

   public void testBasicOperation()
   {
      assertClusterSize("Should only be 2  caches in the cluster!!!", 2);
      assertInvocationContextInitState();

      Fqn f = Fqn.fromString("/test/data");
      String k = "key", v = "value";

      assertNull("Should be null", cache1.getRoot().getChild(f));
      assertNull("Should be null", cache2.getRoot().getChild(f));

      Node<Object, Object> node = cache1.getRoot().addChild(f);

      assertNotNull("Should not be null", node);

      node.put(k, v);

      assertEquals(v, node.get(k));
      assertEquals(v, cache1.get(f, k));
      assertEquals("Should have replicated", v, cache2.get(f, k));
   }

   public void testSyncRepl()
   {
      assertClusterSize("Should only be 2  caches in the cluster!!!", 2);
      assertInvocationContextInitState();

      Fqn fqn = Fqn.fromString("/JSESSIONID/1010.10.5:3000/1234567890/1");
      cache1.getConfiguration().setSyncCommitPhase(true);
      cache2.getConfiguration().setSyncCommitPhase(true);


      cache1.put(fqn, "age", 38);
      assertEquals("Value should be set", 38, cache1.get(fqn, "age"));
      assertEquals("Value should have replicated", 38, cache2.get(fqn, "age"));
   }

   public void testPutMap()
   {
      assertClusterSize("Should only be 2  caches in the cluster!!!", 2);
      assertInvocationContextInitState();

      Fqn fqn = Fqn.fromString("/JSESSIONID/10.10.10.5:3000/1234567890/1");
      Fqn fqn1 = Fqn.fromString("/JSESSIONID/10.10.10.5:3000/1234567890/2");

      Map<Object, Object> map = new HashMap<Object, Object>();
      map.put("1", "1");
      map.put("2", "2");
      cache1.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      cache1.getRoot().addChild(fqn).putAll(map);
      cache1.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      assertEquals("Value should be set", "1", cache1.get(fqn, "1"));

      map = new HashMap<Object, Object>();
      map.put("3", "3");
      map.put("4", "4");
      cache1.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      cache1.getRoot().addChild(fqn1).putAll(map);

      cache1.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      assertEquals("Value should be set", "2", cache1.get(fqn, "2"));
   }


   private void assertClusterSize(String message, int size)
   {
      assertClusterSize(message, size, cache1);
      assertClusterSize(message, size, cache2);
   }

   private void assertClusterSize(String message, int size, Cache c)
   {
      assertEquals(message, size, c.getMembers().size());
   }

   private void assertInvocationContextInitState()
   {
      assertInvocationContextInitState(cache1);
      assertInvocationContextInitState(cache2);
   }

   private void assertInvocationContextInitState(Cache c)
   {
      InvocationContext ctx = c.getInvocationContext();
      InvocationContext control;
      control = ctx.copy();

      control.reset();

      assertEquals("Should be equal", control, ctx);
   }
}
