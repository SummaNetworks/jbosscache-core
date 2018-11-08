/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.replicated;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.Node;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.invocation.CacheInvocationDelegate;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "jgroups"}, testName = "replicated.SyncReplTest")
public class SyncReplTest
{
   private ThreadLocal<Cache<Object, Object>[]> cachesTL = new ThreadLocal<Cache<Object, Object>[]>();

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      Cache<Object, Object>[] caches = new Cache[2];
      caches[0] = new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), getClass());
      caches[1] = new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), getClass());
      cachesTL.set(caches);
      TestingUtil.blockUntilViewsReceived(caches, 5000);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      Cache<Object, Object>[] caches = cachesTL.get();
      if (caches != null) TestingUtil.killCaches(caches);
      cachesTL.set(null);
   }

   public void testBasicOperation()
   {
      Cache<Object, Object>[] caches = cachesTL.get();
      assertClusterSize("Should only be 2  caches in the cluster!!!", 2);
      assertInvocationContextInitState();

      Fqn f = Fqn.fromString("/test/data");
      String k = "key", v = "value";

      assertNull("Should be null", caches[0].getRoot().getChild(f));
      assertNull("Should be null", caches[1].getRoot().getChild(f));

      Node<Object, Object> node = caches[0].getRoot().addChild(f);

      assert ((NodeSPI) node).getCache() instanceof CacheInvocationDelegate;

      assertNotNull("Should not be null", node);

      node.put(k, v);

      assertEquals(v, node.get(k));
      assertEquals(v, caches[0].get(f, k));
      assertEquals("Should have replicated", v, caches[1].get(f, k));
   }

   @SuppressWarnings("unchecked")
   public void testSyncRepl()
   {
      Cache<Object, Object>[] caches = cachesTL.get();
      assertClusterSize("Should only be 2  caches in the cluster!!!", 2);
      assertInvocationContextInitState();

      Fqn fqn = Fqn.fromString("/JSESSIONID/1010.10.5:3000/1234567890/1");
      caches[0].getConfiguration().setSyncCommitPhase(true);
      caches[1].getConfiguration().setSyncCommitPhase(true);

      caches[0].put(fqn, "age", 38);
      assertEquals("Value should be set", 38, caches[0].get(fqn, "age"));
      assertEquals("Value should have replicated", 38, caches[1].get(fqn, "age"));
   }

   /**
    * Checks for expected propagation of removeNode calls.
    */
   @SuppressWarnings("unchecked")
   public void testNodeConvenienceNodeRemoval()
   {
      Cache<Object, Object>[] caches = cachesTL.get();
      // this fqn is relative, but since it is from the root it may as well be absolute
      Fqn fqn = Fqn.fromString("/test/fqn");
      caches[0].getRoot().addChild(fqn);
      assertTrue(caches[0].getRoot().hasChild(fqn));
      assertTrue(caches[1].getRoot().hasChild(fqn));

      assertEquals(true, caches[0].removeNode(fqn));
      assertFalse(caches[0].getRoot().hasChild(fqn));
      assertFalse(caches[1].getRoot().hasChild(fqn));
      assertEquals(false, caches[0].removeNode(fqn));

      // Confirm it works as expected if the removed node has a child
      Fqn child = Fqn.fromString("/test/fqn/child");
      caches[0].getRoot().addChild(child);
      assertTrue(caches[0].getRoot().hasChild(child));
      assertTrue(caches[1].getRoot().hasChild(child));

      assertEquals(true, caches[0].removeNode(fqn));
      assertFalse(caches[0].getRoot().hasChild(fqn));
      assertFalse(caches[1].getRoot().hasChild(fqn));
      assertEquals(false, caches[0].removeNode(fqn));
   }

   private void assertClusterSize(String message, int size)
   {
      Cache<Object, Object>[] caches = cachesTL.get();
      for (Cache c : caches)
      {
         assertClusterSize(message, size, c);
      }
   }

   private void assertClusterSize(String message, int size, Cache c)
   {
      assertEquals(message, size, c.getMembers().size());
   }

   private void assertInvocationContextInitState()
   {
      Cache<Object, Object>[] caches = cachesTL.get();
      for (Cache c : caches)
      {
         assertInvocationContextInitState(c);
      }
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
