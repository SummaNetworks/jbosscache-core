package org.jboss.cache.transaction;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.RPCManager;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.RPCManagerImpl.FlushTracker;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.remote.ReplicateCommand;
import org.jboss.cache.commands.tx.CommitCommand;
import org.jboss.cache.commands.tx.PrepareCommand;
import static org.jboss.cache.config.Configuration.CacheMode.REPL_SYNC;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.Address;
import org.jgroups.Channel;
import org.jgroups.blocks.RspFilter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This is to test the scenario described in http://jira.jboss.org/jira/browse/JBCACHE-1270
 * <p/>
 * i) Node A sends prepare for GTX1; synchronous. Gets applied on Node B. Locks are held on B.
 * ii) Node A sends commit for GTX1; *asynchronous*.
 * iii) Node A sends lots of other messages related to other sessions.
 * iv) Node A sends prepare for GTX2; synchronous.
 * v) Node B is busy, and by luck the GTX2 prepare gets to UNICAST before the GTX1 commit.
 * vi) GTX2 prepare blocks due to locks from GTX1.
 * vii) GTX1 commit is blocked in UNICAST because another thread from Node A is executing.
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", sequential = true, testName = "transaction.PrepareCommitContentionTest")
public class PrepareCommitContentionTest
{
   CacheSPI<Object, Object> c1;

   @BeforeMethod
   public void setUp() throws CloneNotSupportedException
   {
      c1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(REPL_SYNC), getClass());
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(c1);
      c1 = null;
   }

   public void testOOBFlag() throws Exception
   {
      DelegatingRPCManager delegatingRPCManager = new DelegatingRPCManager();
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(c1);
      RPCManager origRpcManager = cr.getComponent(RPCManager.class);
      delegatingRPCManager.delegate = origRpcManager;
      cr.registerComponent(delegatingRPCManager, RPCManager.class);
      cr.rewire();

      c1.getTransactionManager().begin();
      c1.put("/a", "k", "v");
      c1.getTransactionManager().commit();

      // now check what we have gathered:

      assert delegatingRPCManager.log.get(CommitCommand.class) : "Commit commands should be sent using OOB!";
      assert !delegatingRPCManager.log.get(PrepareCommand.class) : "Prepare commands should NOT be sent using OOB!";
   }

   private static class DelegatingRPCManager implements RPCManager
   {
      RPCManager delegate;
      Map<Class<? extends ReplicableCommand>, Boolean> log = new HashMap<Class<? extends ReplicableCommand>, Boolean>();

      public void disconnect()
      {
         delegate.disconnect();
      }

      public void stop()
      {
         delegate.stop();
      }

      public void start()
      {
         delegate.start();
      }

      void logCall(ReplicableCommand command, boolean oob)
      {
         if (command instanceof ReplicateCommand)
         {
            ReplicableCommand cmd = ((ReplicateCommand) command).getSingleModification();
            log.put(cmd.getClass(), oob);
         }
      }

      public List<Object> callRemoteMethods(Vector<Address> recipients, ReplicableCommand cacheCommand, int mode, long timeout, RspFilter responseFilter, boolean useOutOfBandMessage) throws Exception
      {
         logCall(cacheCommand, useOutOfBandMessage);
         return delegate.callRemoteMethods(recipients, cacheCommand, mode, timeout, responseFilter, useOutOfBandMessage);
      }

      public List<Object> callRemoteMethods(Vector<Address> recipients, ReplicableCommand cacheCommand, int mode, long timeout, boolean useOutOfBandMessage) throws Exception
      {
         logCall(cacheCommand, useOutOfBandMessage);
         return delegate.callRemoteMethods(recipients, cacheCommand, mode, timeout, useOutOfBandMessage);
      }

      public List<Object> callRemoteMethods(Vector<Address> recipients, ReplicableCommand command, boolean synchronous, long timeout, boolean useOutOfBandMessage) throws Exception
      {
         logCall(command, useOutOfBandMessage);
         return delegate.callRemoteMethods(recipients, command, synchronous, timeout, useOutOfBandMessage);
      }

      public boolean isCoordinator()
      {
         return delegate.isCoordinator();
      }

      public Address getCoordinator()
      {
         return delegate.getCoordinator();
      }

      public Address getLocalAddress()
      {
         return delegate.getLocalAddress();
      }

      public List<Address> getMembers()
      {
         return delegate.getMembers();
      }

      public void fetchPartialState(List<Address> sources, Fqn sourceTarget, Fqn integrationTarget) throws Exception
      {
         delegate.fetchPartialState(sources, sourceTarget, integrationTarget);
      }

      public void fetchPartialState(List<Address> sources, Fqn subtree) throws Exception
      {
         delegate.fetchPartialState(sources, subtree);
      }

      public Channel getChannel()
      {
         return delegate.getChannel();
      }

      public Address getLastStateTransferSource()
      {
         return delegate.getLastStateTransferSource();
      }

      public FlushTracker getFlushTracker()
      {
         return delegate.getFlushTracker();
      }
   }
}
