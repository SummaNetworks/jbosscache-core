package org.jboss.cache.api;

import org.jboss.cache.AbstractSingleCacheTest;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

@Test(groups = {"functional"}, sequential = true, testName = "api.ReAddDeletedNodeTest")
public class ReAddDeletedNodeTest extends AbstractSingleCacheTest
{
   private CacheSPI<String, String> cache;

   public CacheSPI createCache()
   {
      // start a single cache instance
      UnitTestCacheFactory<String, String> cf = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) cf.createCache("configs/local-tx.xml", false, getClass());
      cache.getConfiguration().setEvictionConfig(null);
      cache.start();
      return cache;
   }

   public void testReAdd() throws Exception
   {
      TransactionManager tm = cache.getTransactionManager();
      Fqn<String> testFqn = Fqn.fromElements("a", "a", "a");

      tm.begin();
      cache.put(testFqn, "x", "x");
      cache.removeNode(testFqn.getParent());
      cache.put(testFqn, "x", "x");
      assert cache.getNode(testFqn) != null : testFqn + " should not be null (before commit)";
      assert cache.getNode(testFqn.getParent()) != null : testFqn.getParent() + " should not be null (before commit)";
      assert cache.getNode(testFqn.getParent().getParent()) != null : testFqn.getParent().getParent() + " should not be null (before commit)";
      tm.commit();
      assert cache.getNode(testFqn) != null : testFqn + " should not be null (after commit)";
      assert cache.getNode(testFqn.getParent()) != null : testFqn.getParent() + " should not be null (after commit)";
      assert cache.getNode(testFqn.getParent().getParent()) != null : testFqn.getParent().getParent() + " should not be null (after commit)";
   }
}
