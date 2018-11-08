package org.jboss.cache.api.mvcc;

import org.jboss.cache.AbstractSingleCacheTest;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.UnitTestCacheFactory;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.Collections;
import java.util.Set;

@Test(groups = {"functional", "mvcc"}, sequential = true, testName = "api.mvcc.GetChildrenNamesAfterRemoveTest")
public class GetChildrenNamesAfterRemoveTest extends AbstractSingleCacheTest
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

   public void testRemove() throws Exception
   {
      TransactionManager tm = cache.getTransactionManager();
      Fqn<String> testFqn = Fqn.fromElements("test1");

      tm.begin();
      assertEmpty(testFqn);
      cache.put(testFqn, "x", "x");
      assertNotEmpty(testFqn);
      cache.removeNode(testFqn);
      assertEmpty(testFqn);
      tm.commit();
      assertEmpty(testFqn);
   }

   private void assertNotEmpty(Fqn<String> testFqn)
   {
      Set<Node<String, String>> children = cache.getNode(testFqn.getParent()).getChildren();
      assert !children.isEmpty() : "Node " + testFqn + " should not be a leaf, but getChildren() returns: " + children;
      Set<Object> childrenNames = cache.getNode(testFqn.getParent()).getChildrenNames();
      assert childrenNames.equals(Collections.singleton(testFqn.getLastElement())) : "Retrieving children names on " + testFqn + " should return test1 but is: " + childrenNames;
   }

   private void assertEmpty(Fqn<String> testFqn)
   {
      Set<Node<String, String>> children = cache.getNode(testFqn.getParent()).getChildren();
      assert children.isEmpty() : "Children should be empty but is " + children;
      Set<Object> childrenNames = cache.getNode(testFqn.getParent()).getChildrenNames();
      assert childrenNames.isEmpty() : "Children names should be empty but is " + childrenNames;
   }
}
