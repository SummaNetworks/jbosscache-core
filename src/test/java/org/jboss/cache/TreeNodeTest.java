package org.jboss.cache;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests restart (stop-destroy-create-start) of CacheSPI
 *
 * @author Bela Ban
 * @version $Id: TreeNodeTest.java 7284 2008-12-12 05:00:02Z mircea.markus $
 */
@Test(groups = {"functional"}, sequential = true, testName = "TreeNodeTest")
public class TreeNodeTest
{
   CacheSPI<Object, Object> cache;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(getClass());
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      cache.stop();
      cache.destroy();
      cache = null;
   }

   public void testChildExist() throws Exception
   {
      Object key = 1;
      cache.put(Fqn.fromString("/a/b/c"), key, "test");
      Node<Object, Object> node = cache.getNode(Fqn.fromString("/a/b"));
      assertFalse(node.getChildren().isEmpty());
      assertTrue(node.getChild(Fqn.fromElements("c")) != null);

      Fqn fqn = Fqn.fromString("/e/f");
      cache.put(fqn, "1", "1");
      node = cache.getNode(Fqn.fromString("/e"));
      assertFalse(node.getChildren().isEmpty());
      assertTrue(node.getChild(Fqn.fromElements("f")) != null);
   }
}
