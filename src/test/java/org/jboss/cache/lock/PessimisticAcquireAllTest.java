package org.jboss.cache.lock;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * @author Bela Ban
 * @version $Id: PessimisticAcquireAllTest.java 7284 2008-12-12 05:00:02Z mircea.markus $
 */
@Test(groups = {"functional"}, testName = "lock.PessimisticAcquireAllTest")
public class PessimisticAcquireAllTest
{
   CacheSPI<Object, Object> cache = null, cache2;
   final Fqn FQN = Fqn.fromString("/myNode");
   final String KEY = "key";
   final String VALUE = "value";

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache, cache2);
      cache = null;
      cache2 = null;
   }


   public void testAcquireAll() throws Exception
   {
      NodeSPI root;
      Object owner = Thread.currentThread();

      cache = createCache(Configuration.CacheMode.LOCAL, IsolationLevel.SERIALIZABLE);
      cache.put("/a/b/c", null);
      cache.put("/1/2/3", null);

      root = cache.getRoot();
      NodeLock lock = root.getLock();

      lock.acquireAll(owner, 2000, LockType.READ);
      lock.releaseAll(owner);

      assertEquals(0, cache.getNumberOfLocksHeld());

      lock.acquireAll(owner, 2000, LockType.WRITE);
      lock.releaseAll(owner);

      assertEquals(0, cache.getNumberOfLocksHeld());
   }


   public void testAcquireAllReplicated() throws Exception
   {
      NodeSPI root;
      Object owner = Thread.currentThread();

      cache2 = createCache(Configuration.CacheMode.REPL_ASYNC, IsolationLevel.SERIALIZABLE);
      cache2.put("/a/b/c", null);
      cache2.put("/1/2/3", null);

      cache = createCache(Configuration.CacheMode.REPL_ASYNC, IsolationLevel.SERIALIZABLE);
      root = cache.getRoot();
      NodeLock lock = root.getLock();

      lock.acquireAll(owner, 2000, LockType.READ);
      lock.releaseAll(owner);

      assertEquals(0, cache.getNumberOfLocksHeld());

      lock.acquireAll(owner, 2000, LockType.WRITE);
      lock.releaseAll(owner);

      assertEquals(0, cache.getNumberOfLocksHeld());
   }


   private CacheSPI<Object, Object> createCache(Configuration.CacheMode mode, IsolationLevel level)
   {
      CacheSPI<Object, Object> c = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      c.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c.getConfiguration().setCacheMode(mode);
      c.getConfiguration().setIsolationLevel(level);
      c.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      c.create();
      c.start();
      return c;
   }
}
