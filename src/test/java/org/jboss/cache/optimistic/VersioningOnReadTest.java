package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Cache;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.VersioningOnReadTest")
public class VersioningOnReadTest extends AbstractOptimisticTestCase
{
   CacheSPI cache;
   Fqn fqn = Fqn.fromString("/a");
   TransactionManager tm;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = createCache();
      tm = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      super.tearDown();
     TestingUtil.killCaches((Cache<Object, Object>) cache);
     cache = null;
   }

   public void testUpdateOnWrite() throws Exception
   {
      cache.put(fqn, "k", "v");

      assertEquals("v", cache.get(fqn, "k"));

      // now start a tx to mod the node
      tm.begin();
      cache.put(fqn, "k", "v2");

      // suspend the tx
      Transaction tx = tm.suspend();

      // now modify the node
      cache.put(fqn, "k", "v3");

      // resume the tx
      tm.resume(tx);

      try
      {
         tm.commit();
         fail("Should have failed with a data version mismatch");
      }
      catch (Exception e)
      {
         // do nothing
      }
   }

   public void testUpdateOnRemove() throws Exception
   {
      cache.put(fqn, "k", "v");

      assertEquals("v", cache.get(fqn, "k"));

      // now start a tx to mod the node
      tm.begin();
      cache.remove(fqn, "k");

      // suspend the tx
      Transaction tx = tm.suspend();

      // now modify the node
      cache.put(fqn, "k", "v3");

      // resume the tx
      tm.resume(tx);

      try
      {
         tm.commit();
         fail("Should have failed with a data version mismatch");
      }
      catch (Exception e)
      {
         // do nothing
      }
   }

   public void testUpdateOnRemoveNode() throws Exception
   {
      cache.put(fqn, "k", "v");

      assertEquals("v", cache.get(fqn, "k"));

      // now start a tx to mod the node
      tm.begin();
      cache.removeNode(fqn);

      // suspend the tx
      Transaction tx = tm.suspend();

      // now modify the node
      cache.put(fqn, "k", "v3");

      // resume the tx
      tm.resume(tx);

      try
      {
         tm.commit();
         fail("Should have failed with a data version mismatch");
      }
      catch (Exception e)
      {
         // do nothing
      }
   }


   public void testUpdateOnRead() throws Exception
   {
      cache.put(fqn, "k", "v");

      assertEquals("v", cache.get(fqn, "k"));

      // now start a tx to mod the node
      tm.begin();
      cache.get(fqn, "k");

      // suspend the tx
      Transaction tx = tm.suspend();

      // now modify the node
      cache.put(fqn, "k", "v3");

      // resume the tx
      tm.resume(tx);

      // now put some other stuff elsewhere
      cache.put(Fqn.fromString("/b"), "k", "v");

      // this should succeed since there is no contention on writing to /a
      tm.commit();
   }

}
