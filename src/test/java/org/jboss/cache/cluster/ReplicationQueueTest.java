package org.jboss.cache.cluster;

import org.easymock.EasyMock;
import static org.easymock.EasyMock.*;
import org.jboss.cache.AbstractMultipleCachesTest;
import org.jboss.cache.Cache;
import org.jboss.cache.RPCManager;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.ReplicationQueueNotifier;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.jgroups.Address;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;

@Test(groups = "functional", testName = "cluster.ReplicationQueueTest")
public class ReplicationQueueTest extends AbstractMultipleCachesTest
{
   private static final int COUNT = 10;
   Cache cache, cache2;
   ReplicationQueue replQ;
   ComponentRegistry registry;
   RPCManager originalRpcManager;
   private ReplicationListener replicationListener;

   protected void createCaches() throws Throwable
   {
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_ASYNC);
      c.setUseReplQueue(true);
      c.setReplQueueMaxElements(COUNT);
      c.setReplQueueInterval(-1);
      cache = new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      cache.start();
      registry = TestingUtil.extractComponentRegistry(cache);
      replQ = registry.getComponent(ReplicationQueue.class);
      originalRpcManager = cache.getConfiguration().getRuntimeConfig().getRPCManager();
      cache2 = new UnitTestCacheFactory<Object, Object>().createCache(cache.getConfiguration().clone(), getClass());
      registerCaches(cache, cache2);
      TestingUtil.blockUntilViewsReceived(60000, cache, cache2);
      replicationListener = ReplicationListener.getReplicationListener(cache2);
   }

   @AfterMethod
   public void tearDown()
   {
      // reset the original RPCManager
      injectRpcManager(originalRpcManager);
      registry.rewire();
   }

   private void injectRpcManager(RPCManager manager)
   {
      registry.registerComponent(manager, RPCManager.class);
   }

   public void testQueueHoldAndFlush() throws Exception
   {
      assert replQ != null;
      assert replQ.elements.size() == 0 : "expected 0, recieved " + replQ.elements.size();

      // mock the RPCManager used in the cache
      RPCManager mockRpcManager = EasyMock.createStrictMock(RPCManager.class);
      injectRpcManager(mockRpcManager);

      // expect basic cluster related calls
      expect(mockRpcManager.getMembers()).andReturn(originalRpcManager.getMembers()).anyTimes();
      replay(mockRpcManager);

      // check that nothing on the RPCManager will be called until we hit the replication queue threshold.
      for (int i = 0; i < COUNT - 1; i++) cache.put("/a/b/c/" + i, "k", "v");
      assertEquals(replQ.elements.size(), COUNT - 1);

      // verify that no calls have been made on the mockRpcManager
      verify(mockRpcManager);

      // reset the mock
      reset(mockRpcManager);

      // now try the last PUT which should result in the queue being flushed.
      expect(mockRpcManager.getMembers()).andReturn(originalRpcManager.getMembers()).anyTimes();
      expect(mockRpcManager.callRemoteMethods((Vector<Address>) anyObject(), (ReplicableCommand) anyObject(), anyBoolean(), anyLong(), anyBoolean())).andReturn(Collections.emptyList()).anyTimes();
      replay(mockRpcManager);

      cache.put("/a/b/c/LAST", "k", "v");
      assert replQ.elements.size() == 0;

      // verify that the rpc call was only made once.
      verify(mockRpcManager);
   }

   public void testFailure() throws InterruptedException
   {
      for (int i = 0; i < COUNT; i++)
      {
         cache.put("/a/b/c" + i, "key", "value");
         assertNotNull(cache.get("/a/b/c" + i, "key"));
         replicationListener.expect(PutKeyValueCommand.class);
      }
      replicationListener.waitForReplicationToOccur();

      for (int i = 0; i < COUNT; i++) assertNotNull("on get i = " + i, cache2.get("/a/b/c" + i, "key"));
   }

   @Test(dependsOnMethods = "testFailure") //if this method will run first then PutKeyValues might still be on wire and influence the other method
   public void testFlushConcurrency() throws Exception
   {
      // will create multiple threads to constantly perform a cache update, and measure the number of expected invocations on the RPC manager.
      final int numThreads = 5;
      final int numLoopsPerThread = 200;

      int totalInvocations = numThreads * numLoopsPerThread;

      assert totalInvocations % COUNT == 0 : "NumThreads and NumLoopsPerThread must multiply to be a multiple of COUNT";

      final CountDownLatch latch = new CountDownLatch(1);

      // mock the RPCManager used in the cache
      RPCManager mockRpcManager = EasyMock.createStrictMock(RPCManager.class);
      injectRpcManager(mockRpcManager);

      // expect basic cluster related calls
      expect(mockRpcManager.getMembers()).andReturn(originalRpcManager.getMembers()).anyTimes();
      expect(mockRpcManager.callRemoteMethods((Vector<Address>) anyObject(), (ReplicableCommand) anyObject(), anyBoolean(), anyLong(), anyBoolean())).andReturn(Collections.emptyList()).anyTimes();
      replay(mockRpcManager);

      Thread[] threads = new Thread[numThreads];

      for (int i = 0; i < numThreads; i++)
      {
         threads[i] = new Thread()
         {
            public void run()
            {
               try
               {
                  latch.await();
               }
               catch (InterruptedException e)
               {
                  // do nothing
               }
               for (int j = 0; j < numLoopsPerThread; j++)
               {
                  cache.put("/a/b/c/" + getName() + "/" + j, "k", "v");
               }
            }
         };
         threads[i].start();
      }

      // start the threads
      latch.countDown();

      // wait for threads to join
      for (Thread t : threads) t.join();

      // now test results
      verify(mockRpcManager);

      ReplicationQueueNotifier notifier = new ReplicationQueueNotifier(cache);
      notifier.waitUntillAllReplicated(250);

      assert replQ.elements.size() == 0;
   }
}
