package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.Cache;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * Tests removal of a node before the node is even created.
 */
@Test(groups = {"functional", "optimistic"}, testName = "optimistic.RemoveBeforeCreateTest")
public class RemoveBeforeCreateTest extends AbstractOptimisticTestCase
{
   CacheSPI<Object, Object>[] c = null;
   ReplicationListener[] replListeners;
   TransactionManager t;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      c = new CacheSPI[2];
      replListeners = new ReplicationListener[2];
      c[0] = createReplicatedCache(Configuration.CacheMode.REPL_ASYNC);
      c[1] = createReplicatedCache(Configuration.CacheMode.REPL_ASYNC);

     TestingUtil.blockUntilViewsReceived(c, 20000);
     replListeners[0] = ReplicationListener.getReplicationListener(c[0]);
     replListeners[1] = ReplicationListener.getReplicationListener(c[1]);

      t = c[0].getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (c != null)
      {
        TestingUtil.killCaches((Cache<Object, Object>) c[0]);
        TestingUtil.killCaches((Cache<Object, Object>) c[1]);
        c = null;
      }
   }

   @SuppressWarnings("unchecked")
   public void testControl() throws Exception
   {
      replListeners[1].expectWithTx(PutKeyValueCommand.class);
      t.begin();
      c[0].put("/control", "key", "value");
      t.commit();
      replListeners[1].waitForReplicationToOccur();

      assertEquals("value", c[0].get("/control", "key"));
      assertEquals("value", c[1].get("/control", "key"));

      DefaultDataVersion v1 = (DefaultDataVersion) ((NodeSPI) c[0].getNode("/control")).getVersion();
      assertEquals(1, v1.getRawVersion());

      DefaultDataVersion v2 = (DefaultDataVersion) ((NodeSPI) c[1].getNode("/control")).getVersion();
      assertEquals(1, v2.getRawVersion());


   }

   @SuppressWarnings("unchecked")
   public void testRemoveBeforePut() throws Exception
   {
      Fqn f = Fqn.fromString("/test");
      assertNull(c[0].getNode(f));
      assertNull(c[1].getNode(f));

      replListeners[1].expectWithTx(PutKeyValueCommand.class);
      t.begin();
      c[0].removeNode(f);

      // should NOT barf!!!
      t.commit();
      replListeners[1].waitForReplicationToOccur();

      assertNull(c[0].getNode(f));
      assertNull(c[1].getNode(f));
   }

}
