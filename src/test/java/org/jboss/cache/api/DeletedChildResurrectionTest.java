package org.jboss.cache.api;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

import org.jboss.cache.*;

/**
 * Tests whether, in a single tx, deleting a parent node with an pre-existing
 * child and then re-adding a node with the parent Fqn results
 * in the pre-existing child remaining in the cache after tx commit.
 *
 * @author Brian Stansberry
 * @since 2.1.0
 */
@Test(groups = {"functional", "pessimistic"}, sequential = true, testName = "api.DeletedChildResurrectionTest")
public class DeletedChildResurrectionTest extends AbstractSingleCacheTest
{
   private CacheSPI<Object, Object> cache;
   private static final Fqn A_B = Fqn.fromString("/a/b");
   private static final Fqn A = Fqn.fromString("/a");
   private static final Fqn A_C = Fqn.fromString("/a/c");
   private static final String KEY = "key";
   private static final String VALUE = "value";
   private static final String K2 = "k2";
   private static final String V2 = "v2";
   protected NodeLockingScheme nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;
   protected NodeSPI root;
   protected TransactionManager txManager;

   public CacheSPI createCache()
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.LOCAL, true), false, getClass());
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setCacheLoaderConfig(null);
      cache.getConfiguration().setNodeLockingScheme(nodeLockingScheme);
      configure(cache.getConfiguration());
      cache.start();
      root = cache.getRoot();
      txManager = cache.getTransactionManager();
      return cache;
   }

   protected void configure(Configuration c)
   {
      // to be overridden
   }

   public void testDeletedChildResurrection1() throws Exception
   {
      root.addChild(A_B).put(KEY, VALUE);
      cache.put(A, "key", "value");
      txManager.begin();
      root.removeChild(A);
      root.addChild(A);
      txManager.commit();
      assert !root.hasChild(A_B);
      assert null == cache.get(A, "key");
      // do a peek to ensure the node really has been removed and not just marked for removal
      assert cache.peek(A_B, true, true) == null;
      assert root.hasChild(A);
   }

   /**
    * Tests whether, in a single tx, deleting a parent node with an pre-existing
    * child and then inserting a different child under the parent Fqn results
    * in the pre-existing child remaining in the cache after tx commit.
    */
   public void testDeletedChildResurrection2() throws Exception
   {
      root.addChild(A_B).put(KEY, VALUE);

      txManager.begin();
      root.removeChild(A);
      root.addChild(A_C).put(K2, V2);
      txManager.commit();

      assert !root.hasChild(A_B);
      assert root.hasChild(A_C);
      assert V2.equals(root.getChild(A_C).get(K2));
   }
}
