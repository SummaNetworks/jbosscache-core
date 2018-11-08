package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Cache;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * Tests the hasChild() API
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.HasChildTest")
public class HasChildTest extends AbstractOptimisticTestCase
{
   private CacheSPI<Object, Object> cache;
   private TransactionManager txMgr;
   private Fqn f = Fqn.fromString("/a");

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = createCache();
      txMgr = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      super.tearDown();
     TestingUtil.killCaches((Cache<Object, Object>) cache);
     cache = null;
   }

   public void testExists() throws Exception
   {
      cache.put(f, "k", "v");

      assertTrue(cache.getRoot().hasChild(f));

      cache.removeNode(f);

      assertFalse(cache.getRoot().hasChild(f));

      txMgr.begin();

      cache.put(f, "k", "v");
      assertTrue(cache.getRoot().hasChild(f));

      Transaction t = txMgr.suspend();
      assertFalse(cache.getRoot().hasChild(f));

      txMgr.resume(t);
      assertTrue(cache.getRoot().hasChild(f));
      txMgr.commit();

      assertTrue(cache.getRoot().hasChild(f));

      txMgr.begin();
      assertTrue(cache.getRoot().hasChild(f));
      cache.removeNode(f);
      assertFalse(cache.getRoot().hasChild(f));

      t = txMgr.suspend();
      assertTrue(cache.getRoot().hasChild(f));
      txMgr.resume(t);
      assertFalse(cache.getRoot().hasChild(f));
      txMgr.commit();

      assertFalse(cache.getRoot().hasChild(f));
   }
}
