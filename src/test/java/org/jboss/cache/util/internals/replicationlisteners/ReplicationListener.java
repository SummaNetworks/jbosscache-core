package org.jboss.cache.util.internals.replicationlisteners;

import org.jboss.cache.Cache;
import org.jboss.cache.RPCManager;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.CacheStatus;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.remote.*;
import org.jboss.cache.commands.tx.PrepareCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.marshall.CommandAwareRpcDispatcher;
import org.jboss.cache.marshall.ReplicationObserver;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.Address;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Utility class that notifies when certain commands were asynchronously replicated on secondary cache.
 * Especially useful for avaoiding Thread.sleep() statements.
 * <p/>
 * Usage:
 * <pre>
 *   no tx:
 *       Cache c1, c2; //these being two async caches
 *       ReplicationListener listener2 = ReplicationListener.getReplicationListener(c2);
 *       listener2.expect(PutKeyValueCommand.class);
 *       c1.put(fqn, key, value);
 *       listener2.waitForReplicationToOccur(1000); // -this will block here untill c2 recieves the PutKeyValueCommand command
 *   with tx: (difference is that expectWithTx is used insted of expect
 *       Cache c1, c2; //these being two async caches
 *       ReplicationListener listener2 = ReplicationListener.getReplicationListener(c2);
 *       listener2.expectWithTx(PutKeyValueCommand.class);
 *       txManager.begin();
 *       c1.put(fqn, key, value);
 *       txManager.commit();
 *       listener2.waitForReplicationToOccur(1000); // -this will block here untill c2 recieves the PutKeyValueCommand command
 * </pre>
 * <p/>
 * <p/>
 * Lifecycle - after being used (i.e. waitForReplicationToOccur returns sucessfully) the object returns to the
 * non-initialized state and *can* be reused through expect-wait cycle.
 * <b>Note</b>:  this class might be used aswell for sync caches, e.g. a test could have subclasses which use sync and
 * async replication
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
abstract public class ReplicationListener implements ReplicationObserver
{
   public static final long DEFAULT_TIMEOUT = 10000;
   private CountDownLatch latch = new CountDownLatch(1);
   protected List<Class<? extends ReplicableCommand>> expectedCommands;
   protected Configuration config;
   protected final Address localAddress;
   private Cache cache;

   /**
    * Builds a listener that will observe the given cache for recieving replication commands.
    */
   protected ReplicationListener(Cache cache)
   {
      ComponentRegistry componentRegistry = TestingUtil.extractComponentRegistry(cache);
      RPCManager rpcManager = componentRegistry.getComponent(RPCManager.class);
      CommandAwareRpcDispatcher realDispatcher = (CommandAwareRpcDispatcher) TestingUtil.extractField(rpcManager, "rpcDispatcher");
      if (realDispatcher.setReplicationObserver(this) != null)
      {
         throw new RuntimeException("Replication listener already present");
      }
      this.localAddress = cache.getLocalAddress();
      this.config = cache.getConfiguration();
      this.cache = cache;
   }

   protected ReplicationListener()
   {
      localAddress = null;
   }

   abstract public void expect(Class<? extends ReplicableCommand>... expectedCommands);

   /**
    * Based on cache's configuration, will know for what specific commands to expect to be replicated.
    * E.g. async replication with a tx, would expect only a PrepareCommand (async is 1PC). sync repl with tx would expect
    * a prepare and a commit (sync is 2pc).
    */
   abstract public void expectWithTx(Class<? extends ReplicableCommand>... writeCommands);

   /**
    * Factory method, to be used in order to obtain a replication listener based on a cache config.
    */
   public static ReplicationListener getReplicationListener(Cache cache)
   {
      if (cache.getConfiguration().getCacheMode().isInvalidation())
      {
         return new InvalidationReplicationListener(cache);
      }
      if (cache.getConfiguration().getNodeLockingScheme().equals(Configuration.NodeLockingScheme.OPTIMISTIC))
      {
         return new OptimisticReplicationListener(cache);
      } else if (cache.getConfiguration().getNodeLockingScheme().equals(Configuration.NodeLockingScheme.PESSIMISTIC))
      {
         return new PessReplicationListener(cache);
      } else
      {
         return new MvccReplicationListener(cache);
      }
   }

   public void afterExecutingCommand(ReplicableCommand realOne)
   {
      if (expectedCommands == null)
      {
         log("skipping command " + realOne);
         return;
      }
      log("Processed command: " + realOne);

      if (realOne instanceof ReplicateCommand)
      {
         postReplicateExecution((ReplicateCommand) realOne);
      } else
      {
         postNonVisitableExecution(realOne);
      }
      if (expectedCommands.isEmpty())
      {
         latch.countDown();
      }

   }

   private void log(String s)
   {
      System.out.println("[" + localAddress + "] " + s);
   }

   protected void postNonVisitableExecution(ReplicableCommand realOne)
   {
      if (!expectedCommands.remove(realOne.getClass()))
      {
         log("not expecting command " + realOne + " ");
      }
   }

   protected void postReplicateExecution(ReplicateCommand realOne)
   {
      if (!realOne.removeCommands(expectedCommands))
      {
         if (realOne.getSingleModification() instanceof PrepareCommand)
         {
            Iterator<Class<? extends ReplicableCommand>> it = expectedCommands.iterator();
            while (it.hasNext())
            {
               Class<? extends ReplicableCommand> replicableCommandClass = it.next();
               PrepareCommand prepareCommand = (PrepareCommand) realOne.getSingleModification();
               if (prepareCommand.containsModificationType(replicableCommandClass))
               {
                  it.remove();
                  break;//only remove once
               }
            }
         }
      }
   }


   /**
    * Blocks for the elements specified through {@link #expect(Class[])} invocations to be replicated in this cache.
    * if replication does not occur in the give timeout then an exception is being thrown.
    */
   public void waitForReplicationToOccur(long timeoutMillis)
   {
      CacheStatus state = ((CacheSPI) cache).getComponentRegistry().getState();
      if (!state.equals(CacheStatus.STARTED))
      {
         throw new IllegalStateException("Cannot invoke on an cache that is not started: current cache status is " + state);
      }
//      log("enter... ReplicationListener.waitForReplicationToOccur");
      waitForReplicationToOccur(timeoutMillis, TimeUnit.MILLISECONDS);
//      log("exit... ReplicationListener.waitForReplicationToOccur");
   }

   /**
    * same as {@link #waitForReplicationToOccur(long)}, just that it uses the {@link #DEFAULT_TIMEOUT} for timeout.
    */
   public void waitForReplicationToOccur()
   {
      waitForReplicationToOccur(DEFAULT_TIMEOUT);
   }

   /**
    * Similar to {@link #waitForReplicationToOccur(long)} except that this method provides more flexibility in time units.
    *
    * @param timeout  the maximum time to wait
    * @param timeUnit the time unit of the <tt>timeout</tt> argument.
    */
   public void waitForReplicationToOccur(long timeout, TimeUnit timeUnit)
   {
      assert expectedCommands != null : "there are no replication expectations; please use AsyncReplicationListener.expectWithTx(...) before calling this method";
      try
      {
         if (!expectedCommands.isEmpty() && !latch.await(timeout, timeUnit))
         {
            assert false : "[" + localAddress + "] waiting for more than " + timeout + " " + timeUnit + " and following commands did not replicate: " + expectedCommands;
         }
      }
      catch (InterruptedException e)
      {
         throw new IllegalStateException("unexpected", e);
      }
      finally
      {
         expectedCommands = null;
         latch = new CountDownLatch(1);
      }
   }

   /**
    * {@link #waitForReplicationToOccur(long)} will block untill all the commands specified here are being replicated
    * to this cache. The method can be called several times with various arguments.
    */
   protected void internalExpect(Class<? extends ReplicableCommand>... expectedCommands)
   {
      if (this.expectedCommands == null)
      {

         this.expectedCommands = new ArrayList<Class<? extends ReplicableCommand>>();
      }
      this.expectedCommands.addAll(Arrays.asList(expectedCommands));
   }

   public void reset()
   {
      if (expectedCommands != null) expectedCommands.clear();
   }

   protected boolean isRemoteCommand(Class clazz)
   {
      return clazz.equals(AnnounceBuddyPoolNameCommand.class) || clazz.equals(AssignToBuddyGroupCommand.class) ||
            clazz.equals(ClusteredGetCommand.class) || clazz.equals(DataGravitationCleanupCommand.class) ||
            clazz.equals(RemoveFromBuddyGroupCommand.class) || clazz.equals(ReplicateCommand.class);
   }

   public Cache getCache()
   {
      return cache;
   }
}
//[127.0.0.1:7900] Processed command: ReplicateCommand{cmds=PrepareCommand{globalTransaction=GlobalTransaction:<127.0.0.1:7903>:481, modifications=[PutDataMapCommand{fqn=/_BUDDY_BACKUP_/127.0.0.1_7903/JSESSION/BuddyReplicationFailoverTest54_localhost/160, dataVersion=null, data={2=org.jboss.cache.integration.websession.util.SessionMetadata@93a0d8, 1=1233588409000, 3={key=2}, 0=3}, globalTransaction=null, erase=false}], localAddress=127.0.0.1:7903, onePhaseCommit=true}}
//[127.0.0.1:7900] skipping  command  ReplicateCommand{cmds=PrepareCommand{globalTransaction=GlobalTransaction:<127.0.0.1:7903>:482, modifications=[PutDataMapCommand{fqn=/_BUDDY_BACKUP_/127.0.0.1_7903/JSESSION/BuddyReplicationFailoverTest54_localhost/160, dataVersion=null, data={1=1233588409031, 3={key=3}, 0=4}, globalTransaction=null, erase=false}], localAddress=127.0.0.1:7903, onePhaseCommit=true}}
