package org.jboss.cache.api.pfer;

import org.easymock.EasyMock;
import static org.easymock.EasyMock.*;
import org.jboss.cache.*;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.write.PutForExternalReadCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.commands.write.RemoveNodeCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.optimistic.TransactionWorkspace;
import org.jboss.cache.transaction.GlobalTransaction;
import org.jboss.cache.transaction.OptimisticTransactionContext;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.jgroups.Address;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import javax.transaction.SystemException;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.List;
import java.util.Vector;

public abstract class PutForExternalReadTestBase extends AbstractMultipleCachesTest
{
   protected Configuration.CacheMode cacheMode;
   protected NodeLockingScheme nodeLockingScheme;
   protected final Fqn fqn = Fqn.fromString("/one/two");
   protected final Fqn parentFqn = fqn.getParent();
   protected final String key = "k", value = "v", value2 = "v2";

   protected CacheSPI<String, String> cache1, cache2;

   ReplicationListener replListener1;
   ReplicationListener replListener2;

   protected TransactionManager tm1, tm2;

   protected boolean useTx;
   private RPCManager rpcManager1;

   protected void createCaches()
   {
      UnitTestCacheFactory<String, String> cf = new UnitTestCacheFactory<String, String>();

      cache1 = (CacheSPI<String, String>) cf.createCache(UnitTestConfigurationFactory.createConfiguration(cacheMode), false, getClass());
      cache1.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache1.getConfiguration().setSerializationExecutorPoolSize(0);//this is very important for async tests!
      cache1.getConfiguration().setNodeLockingScheme(nodeLockingScheme);

      cache1.start();
      tm1 = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();

      cache2 = (CacheSPI<String, String>) cf.createCache(UnitTestConfigurationFactory.createConfiguration(cacheMode), false, getClass());
      cache2.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache2.getConfiguration().setSerializationExecutorPoolSize(0); //this is very important for async tests!
      cache2.getConfiguration().setNodeLockingScheme(nodeLockingScheme);

      cache2.start();
      tm2 = cache2.getConfiguration().getRuntimeConfig().getTransactionManager();
      replListener1 = ReplicationListener.getReplicationListener(cache1);
      replListener2 = ReplicationListener.getReplicationListener(cache2);

      rpcManager1 = cache1.getRPCManager();

      TestingUtil.blockUntilViewsReceived(10000, cache1, cache2);
      registerCaches(cache1, cache2);
   }

   public void testNoOpWhenNodePresent()
   {
      replListener2.expect(PutForExternalReadCommand.class);
      cache1.putForExternalRead(fqn, key, value);
      replListener2.waitForReplicationToOccur();


      assertEquals("PFER should have succeeded", value, cache1.get(fqn, key));
      if (isUsingInvalidation())
         assertNull("PFER should not have effected cache2", cache2.get(fqn, key));
      else
         assertEquals("PFER should have replicated", value, cache2.get(fqn, key));

      // reset
      replListener2.expect(RemoveNodeCommand.class);
      cache1.removeNode(fqn);
      replListener2.waitForReplicationToOccur();

      assertFalse("Should have reset", cache1.getRoot().hasChild(fqn));
      assertFalse("Should have reset", cache2.getRoot().hasChild(fqn));

      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(fqn, key, value);
      replListener2.waitForReplicationToOccur();

      // now this pfer should be a no-op
      cache1.putForExternalRead(fqn, key, value2);

      assertEquals("PFER should have been a no-op", value, cache1.get(fqn, key));
      if (isUsingInvalidation())
         assertNull("PFER should have been a no-op", cache2.get(fqn, key));
      else
         assertEquals("PFER should have been a no-op", value, cache2.get(fqn, key));
   }

   private Vector<Address> anyAddresses()
   {
      anyObject();
      return null;
   }

   public void testAsyncForce() throws Exception
   {
      RPCManager rpcManager = EasyMock.createNiceMock(RPCManager.class);

      List<Address> memberList = rpcManager1.getMembers();
      expect(rpcManager.getMembers()).andReturn(memberList).anyTimes();
      // inject a mock RPC manager so that we can test whether calls made are sync or async.
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache1);
      cr.registerComponent(rpcManager, RPCManager.class);
      cr.rewire();

      // invalidations will not trigger any rpc calls for PFER
      if (!isUsingInvalidation())
      {
         // specify what we expectWithTx called on the mock Rpc Manager.  For params we don't care about, just use ANYTHING.
         // setting the mock object to expectWithTx the "sync" param to be false.
         expect(rpcManager.callRemoteMethods(anyAddresses(), (ReplicableCommand) anyObject(), eq(false), anyLong(), anyBoolean())).andReturn(null);
      }

      replay(rpcManager);

      // now try a simple replication.  Since the RPCManager is a mock object it will not actually replicate anything.
      cache1.putForExternalRead(fqn, key, value);
      verify(rpcManager);

      // cleanup
      TestingUtil.extractComponentRegistry(cache1).registerComponent(rpcManager1, RPCManager.class);
      TestingUtil.extractComponentRegistry(cache1).rewire();

      replListener2.expect(RemoveNodeCommand.class);
      cache1.removeNode(fqn);
      replListener2.waitForReplicationToOccur();
   }

   public void testTxSuspension() throws Exception
   {
      // create parent node first
      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(parentFqn, key, value);
      replListener2.waitForReplicationToOccur();

      // start a tx and do some stuff.
      replListener2.expect(PutForExternalReadCommand.class);
      tm1.begin();
      cache1.get(parentFqn, key);
      cache1.putForExternalRead(fqn, key, value); // should have happened in a separate tx and have committed already.
      Transaction t = tm1.suspend();

      replListener2.waitForReplicationToOccur();

      assertLocked(parentFqn, cache1, false);

      assertEquals("PFER should have completed", value, cache1.get(fqn, key));
      if (isUsingInvalidation())
         assertNull("PFER should not have effected cache2", cache2.get(fqn, key));
      else
         assertEquals("PFER should have completed", value, cache2.get(fqn, key));

      tm1.resume(t);
      tm1.commit();

      assertEquals("parent fqn tx should have completed", value, cache1.get(parentFqn, key));
      if (isUsingInvalidation())
         assertNull("parent fqn tx should have invalidated cache2", cache2.get(parentFqn, key));
      else
         assertEquals("parent fqn tx should have completed", value, cache2.get(parentFqn, key));
   }

   public void testExceptionSuppression() throws Exception
   {
      RPCManager barfingRpcManager = EasyMock.createNiceMock(RPCManager.class);
      RPCManager originalRpcManager = cache1.getConfiguration().getRuntimeConfig().getRPCManager();
      try
      {
         List<Address> memberList = originalRpcManager.getMembers();
         expect(barfingRpcManager.getMembers()).andReturn(memberList).anyTimes();
         expect(barfingRpcManager.getLocalAddress()).andReturn(originalRpcManager.getLocalAddress()).anyTimes();
         expect(barfingRpcManager.callRemoteMethods(anyAddresses(), (ReplicableCommand) anyObject(), anyBoolean(), anyLong(), anyBoolean())).andThrow(new RuntimeException("Barf!")).anyTimes();
         replay(barfingRpcManager);

         TestingUtil.extractComponentRegistry(cache1).registerComponent(barfingRpcManager, RPCManager.class);
         cache1.getConfiguration().getRuntimeConfig().setRPCManager(barfingRpcManager);
         TestingUtil.extractComponentRegistry(cache1).rewire();

         try
         {
            cache1.put(fqn, key, value);
            if (!isOptimistic()) fail("Should have barfed");
         }
         catch (RuntimeException re)
         {
         }

         if (isOptimistic() && !isUsingInvalidation())
         {
            // proves that the put did, in fact, barf.  Doesn't work for invalidations since the inability to invalidate will not cause a rollback.
            assertNull(cache1.get(fqn, key));
         } else
         {
            // clean up any indeterminate state left over
            try
            {
               cache1.removeNode(fqn);
               // as above, the inability to invalidate will not cause an exception
               if (!isUsingInvalidation()) fail("Should have barfed");
            }
            catch (RuntimeException re)
            {
            }
         }

         assertNull("Should have cleaned up", cache1.get(fqn, key));

         // should not barf
         cache1.putForExternalRead(fqn, key, value);
      }
      finally
      {
         TestingUtil.extractComponentRegistry(cache1).registerComponent(rpcManager1, RPCManager.class);
         TestingUtil.extractComponentRegistry(cache1).rewire();
      }
   }

   public void testBasicPropagation() throws Exception
   {
      assert !cache1.exists(fqn);
      assert !cache2.exists(fqn);

      replListener2.expect(PutForExternalReadCommand.class);
      cache1.putForExternalRead(fqn, key, value);
      replListener2.waitForReplicationToOccur();

      assertEquals("PFER updated cache1", value, cache1.get(fqn, key));
      Object expected = isUsingInvalidation() ? null : value;
      assertEquals("PFER propagated to cache2 as expected", expected, cache2.get(fqn, key));

      // replication to cache 1 should NOT happen.
      cache2.putForExternalRead(fqn, key, value);

      assertEquals("PFER updated cache2", value, cache2.get(fqn, key));
      assertEquals("Cache1 should be unaffected", value, cache1.get(fqn, key));
   }

   /**
    * Tests that setting a cacheModeLocal=true Option prevents propagation
    * of the putForExternalRead().
    *
    * @throws Exception
    */
   public void testSimpleCacheModeLocal() throws Exception
   {
      cacheModeLocalTest(false);
   }

   /**
    * Tests that setting a cacheModeLocal=true Option prevents propagation
    * of the putForExternalRead() when the call occurs inside a transaction.
    *
    * @throws Exception
    */
   public void testCacheModeLocalInTx() throws Exception
   {
      cacheModeLocalTest(true);
   }

   /**
    * Tests that suspended transactions do not leak.  See JBCACHE-1246.
    */
   public void testMemLeakOnSuspendedTransactions() throws Exception
   {
      Fqn fqn2 = Fqn.fromString("/fqn/two");

      replListener2.expect(PutForExternalReadCommand.class);
      tm1.begin();
      cache1.putForExternalRead(fqn, key, value);
      tm1.commit();
      replListener2.waitForReplicationToOccur();


      assert cache1.getTransactionTable().getNumGlobalTransactions() == 0 : "Cache 1 should have no stale global TXs";
      assert cache1.getTransactionTable().getNumLocalTransactions() == 0 : "Cache 1 should have no stale local TXs";
      assert cache2.getTransactionTable().getNumGlobalTransactions() == 0 : "Cache 2 should have no stale global TXs";
      assert cache2.getTransactionTable().getNumLocalTransactions() == 0 : "Cache 2 should have no stale local TXs";

      //do not expectWithTx a PFER replication, as the node already exists so this is a no-op
      replListener2.expectWithTx(PutKeyValueCommand.class);
      tm1.begin();
      cache1.putForExternalRead(fqn, key, value);
      cache1.put(fqn2, key, value);
      tm1.commit();
      replListener2.waitForReplicationToOccur();

      assert cache1.getTransactionTable().getNumGlobalTransactions() == 0 : "Cache 1 should have no stale global TXs";
      assert cache1.getTransactionTable().getNumLocalTransactions() == 0 : "Cache 1 should have no stale local TXs";
      assert cache2.getTransactionTable().getNumGlobalTransactions() == 0 : "Cache 2 should have no stale global TXs";
      assert cache2.getTransactionTable().getNumLocalTransactions() == 0 : "Cache 2 should have no stale local TXs";

      replListener2.expectWithTx(PutKeyValueCommand.class);
      tm1.begin();
      cache1.put(fqn2, key, value);
      cache1.putForExternalRead(fqn, key, value);
      tm1.commit();
      replListener2.waitForReplicationToOccur(10000);

      assert cache1.getTransactionTable().getNumGlobalTransactions() == 0 : "Cache 1 should have no stale global TXs";
      assert cache1.getTransactionTable().getNumLocalTransactions() == 0 : "Cache 1 should have no stale local TXs";
      assert cache2.getTransactionTable().getNumGlobalTransactions() == 0 : "Cache 2 should have no stale global TXs";
      assert cache2.getTransactionTable().getNumLocalTransactions() == 0 : "Cache 2 should have no stale local TXs";

      //do not expectWithTx a PFER replication, as the node already exists so this is a no-op
      replListener2.expectWithTx(PutKeyValueCommand.class);
      tm1.begin();
      cache1.put(fqn2, key, value);
      cache1.putForExternalRead(fqn, key, value);
      cache1.put(fqn2, key, value);
      tm1.commit();
      replListener2.waitForReplicationToOccur(10000);

      assert cache1.getTransactionTable().getNumGlobalTransactions() == 0 : "Cache 1 should have no stale global TXs";
      assert cache1.getTransactionTable().getNumLocalTransactions() == 0 : "Cache 1 should have no stale local TXs";
      assert cache2.getTransactionTable().getNumGlobalTransactions() == 0 : "Cache 2 should have no stale global TXs";
      assert cache2.getTransactionTable().getNumLocalTransactions() == 0 : "Cache 2 should have no stale local TXs";
   }

   /**
    * Tests that setting a cacheModeLocal=true Option prevents propagation
    * of the putForExternalRead().
    *
    * @throws Exception
    */
   private void cacheModeLocalTest(boolean transactional) throws Exception
   {
      RPCManager rpcManager = EasyMock.createMock(RPCManager.class);
      RPCManager originalRpcManager = cache1.getConfiguration().getRuntimeConfig().getRPCManager();

      // inject a mock RPC manager so that we can test whether calls made are sync or async.
      cache1.getConfiguration().getRuntimeConfig().setRPCManager(rpcManager);

      // specify that we expectWithTx nothing will be called on the mock Rpc Manager.
      replay(rpcManager);

      // now try a simple replication.  Since the RPCManager is a mock object it will not actually replicate anything.
      if (transactional)
         tm1.begin();

      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.putForExternalRead(fqn, key, value);

      if (transactional)
         tm1.commit();

      verify(rpcManager);
      // cleanup
      cache1.getConfiguration().getRuntimeConfig().setRPCManager(originalRpcManager);

      replListener2.expect(RemoveNodeCommand.class);
      cache1.removeNode(fqn);
      replListener2.waitForReplicationToOccur();
   }

   protected abstract void assertLocked(Fqn fqn, CacheSPI cache, boolean writeLocked);

   protected TransactionWorkspace extractTransactionWorkspace(Cache c)
   {
      CacheSPI cs = (CacheSPI) c;
      try
      {
         GlobalTransaction gtx = cs.getTransactionTable().get(cs.getTransactionManager().getTransaction());
         OptimisticTransactionContext entry = (OptimisticTransactionContext) cs.getTransactionTable().get(gtx);
         return entry.getTransactionWorkSpace();
      }
      catch (SystemException e)
      {
         e.printStackTrace();
         fail("Unable to extract transaction workspace from cache");
      }
      return null;
   }

   protected boolean isUsingInvalidation()
   {
      return cacheMode.isInvalidation();
   }

   protected boolean isAsync()
   {
      return !cacheMode.isSynchronous();
   }

   protected boolean isOptimistic()
   {
      return nodeLockingScheme == NodeLockingScheme.OPTIMISTIC;
   }
}
