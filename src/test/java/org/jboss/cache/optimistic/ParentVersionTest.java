package org.jboss.cache.optimistic;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.ParentVersionTest")
public class ParentVersionTest extends AbstractOptimisticTestCase
{
   private Cache<Object, Object> cache;
   private TransactionManager tm;

   protected boolean lockParentForChildInsertRemove = false; // the default
   private Fqn parent = Fqn.fromString("/parent");
   private Fqn child1 = Fqn.fromString("/parent/child1");
   private Fqn child2 = Fqn.fromString("/parent/child2");
   private Fqn deepchild = Fqn.fromString("/parent/deep/child");

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      if (lockParentForChildInsertRemove)
      {
         cache = createCacheUnstarted();
         cache.getConfiguration().setLockParentForChildInsertRemove(true);
         cache.start();
      }
      else cache = createCache();

      tm = ((CacheSPI<Object, Object>) cache).getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
     TestingUtil.killCaches(cache);
     cache = null;
   }

   private long getVersion(Node n)
   {
      return ((DefaultDataVersion) ((NodeSPI) n).getVersion()).getRawVersion();
   }

   public void testSimpleAdd()
   {
      cache.put(parent, "k", "v");
      long parentVersion = getVersion(cache.getRoot().getChild(parent));
      cache.put(child1, "k", "v");

      if (lockParentForChildInsertRemove)
         assertEquals(parentVersion + 1, getVersion(cache.getRoot().getChild(parent)));
      else assertEquals(parentVersion, getVersion(cache.getRoot().getChild(parent)));

      assertTrue(cache.getRoot().hasChild(parent));
      assertTrue(cache.getRoot().hasChild(child1));
   }

   public void testSimpleRemove()
   {
      cache.put(parent, "k", "v");
      cache.put(child1, "k", "v");
      assertTrue(cache.getRoot().hasChild(parent));
      assertTrue(cache.getRoot().hasChild(child1));

      long parentVersion = getVersion(cache.getRoot().getChild(parent));

      cache.removeNode(child1);

      if (lockParentForChildInsertRemove)
         assertEquals(parentVersion + 1, getVersion(cache.getRoot().getChild(parent)));
      else assertEquals(parentVersion, getVersion(cache.getRoot().getChild(parent)));

      assertTrue(cache.getRoot().hasChild(parent));
      assertFalse("Should have removed child1", cache.getRoot().hasChild(child1));
   }

   public void testAddAndRemove() throws Exception
   {
      cache.put(parent, "k", "v");
      cache.put(child1, "k", "v");

      assertTrue(cache.getRoot().hasChild(parent));
      assertTrue(cache.getRoot().hasChild(child1));
      assertFalse(cache.getRoot().hasChild(child2));

      long parentVersion = getVersion(cache.getRoot().getChild(parent));

      tm.begin();
      cache.put(child2, "k", "v");
      cache.removeNode(child1);
      tm.commit();

      if (lockParentForChildInsertRemove)
         assertEquals(parentVersion + 1, getVersion(cache.getRoot().getChild(parent)));
      else assertEquals(parentVersion, getVersion(cache.getRoot().getChild(parent)));

      assertTrue(cache.getRoot().hasChild(parent));
      assertFalse("Should have removed child1", cache.getRoot().hasChild(child1));
      assertTrue(cache.getRoot().hasChild(child2));
   }

   public void testAddAndRemoveOverlap() throws Exception
   {
      cache.put(parent, "k", "v");
      cache.put(child1, "k", "v");

      assertTrue(cache.getRoot().hasChild(parent));
      assertTrue(cache.getRoot().hasChild(child1));
      assertFalse(cache.getRoot().hasChild(child2));

      long parentVersion = getVersion(cache.getRoot().getChild(parent));

      tm.begin();
      cache.put(child2, "k", "v");
      cache.removeNode(child1);
      cache.removeNode(child2);
      cache.removeNode(child1);
      cache.put(child1, "k", "v");
      cache.removeNode(child1);
      cache.removeNode(child2);
      cache.put(child2, "k", "v");
      tm.commit();

      if (lockParentForChildInsertRemove)
         assertEquals(parentVersion + 1, getVersion(cache.getRoot().getChild(parent)));
      else assertEquals(parentVersion, getVersion(cache.getRoot().getChild(parent)));

      assertTrue(cache.getRoot().hasChild(parent));
      assertFalse("Should have removed child1", cache.getRoot().hasChild(child1));
      assertTrue(cache.getRoot().hasChild(child2));
   }

   public void testRemoveAndAdd() throws Exception
   {
      cache.put(parent, "k", "v");
      cache.put(child1, "k", "v");

      assertTrue(cache.getRoot().hasChild(parent));
      assertTrue(cache.getRoot().hasChild(child1));
      assertFalse(cache.getRoot().hasChild(child2));

      long parentVersion = getVersion(cache.getRoot().getChild(parent));

      tm.begin();
      cache.removeNode(child1);
      cache.put(child2, "k", "v");
      tm.commit();

      if (lockParentForChildInsertRemove)
         assertEquals(parentVersion + 1, getVersion(cache.getRoot().getChild(parent)));
      else assertEquals(parentVersion, getVersion(cache.getRoot().getChild(parent)));

      assertTrue(cache.getRoot().hasChild(parent));
      assertFalse("Should have removed child1", cache.getRoot().hasChild(child1));
      assertTrue(cache.getRoot().hasChild(child2));
   }

   public void testDeepRemove()
   {
      cache.put(parent, "k", "v");
      cache.put(deepchild, "k", "v");

      assertTrue(cache.getRoot().hasChild(parent));
      assertTrue(cache.getRoot().hasChild(deepchild));

      long parentVersion = getVersion(cache.getRoot().getChild(parent));

      cache.removeNode(deepchild);

      assertEquals(parentVersion, getVersion(cache.getRoot().getChild(parent)));

      assertTrue(cache.getRoot().hasChild(parent));
      assertFalse("Should have removed deepchild", cache.getRoot().hasChild(deepchild));
   }

   public void testDeepAdd()
   {
      cache.put(parent, "k", "v");

      assertTrue(cache.getRoot().hasChild(parent));
      assertFalse(cache.getRoot().hasChild(deepchild));

      long parentVersion = getVersion(cache.getRoot().getChild(parent));

      cache.put(deepchild, "k", "v");

      if (lockParentForChildInsertRemove)
         assertEquals(parentVersion + 1, getVersion(cache.getRoot().getChild(parent)));
      else assertEquals(parentVersion, getVersion(cache.getRoot().getChild(parent)));

      assertTrue(cache.getRoot().hasChild(parent));
      assertTrue(cache.getRoot().hasChild(deepchild));
   }
}
