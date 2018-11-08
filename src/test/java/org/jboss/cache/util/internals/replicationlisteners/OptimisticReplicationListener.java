package org.jboss.cache.util.internals.replicationlisteners;

import org.jboss.cache.Cache;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.remote.ReplicateCommand;
import org.jboss.cache.commands.tx.CommitCommand;
import org.jboss.cache.commands.tx.OptimisticPrepareCommand;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Specialization of ReplicationListener for optimistic caches.
 *
 * @author Mircea.Markus@jboss.com
 */
public class OptimisticReplicationListener extends ReplicationListener
{
   public OptimisticReplicationListener(Cache cache)
   {
      super(cache);
   }

   /**
    * For each command, expect an OptimisticPrepare and an commit.
    */
   public void expect(Class<? extends ReplicableCommand>... expectedCommands)
   {
      //in the case of optimistic replication, an prepare and an commit is expected for each node
      for (Class<? extends ReplicableCommand> command : expectedCommands)
      {
         if (isRemoteCommand(command))
         {
            internalExpect(command);
         }
         else
         {
            internalExpect(OptimisticPrepareCommand.class, CommitCommand.class);
         }
      }
   }

   /**
    * For all given commands expect a single prepare, and a single commit.
    */
   public void expectWithTx(Class<? extends ReplicableCommand>... writeCommands)
   {
      internalExpect(OptimisticPrepareCommand.class, CommitCommand.class);
   }

   protected void postReplicateExecution(ReplicateCommand realOne)
   {
      List<ReplicableCommand> mods = getAllModifications(realOne);
      for (Iterator<Class<? extends ReplicableCommand>> typeIt = expectedCommands.iterator(); typeIt.hasNext();)
      {
         Class<? extends ReplicableCommand> commadType = typeIt.next();
         Iterator<ReplicableCommand> instanceIt = mods.iterator();
         while (instanceIt.hasNext())
         {
            ReplicableCommand replicableCommand = instanceIt.next();
            if (replicableCommand.getClass().equals(commadType))
            {
               instanceIt.remove();
               typeIt.remove();
            }
         }
      }
   }

   private List<ReplicableCommand> getAllModifications(ReplicateCommand realOne)
   {
      List<ReplicableCommand> result = new ArrayList<ReplicableCommand>();
      if (realOne.isSingleCommand())
      {
         result.add(realOne.getSingleModification());
      }
      else
      {
         result.addAll(realOne.getModifications());
      }
      return result;
   }
}
