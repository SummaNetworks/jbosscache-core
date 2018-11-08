package org.jboss.cache.util.internals.replicationlisteners;

import org.jboss.cache.Cache;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.read.GravitateDataCommand;
import org.jboss.cache.commands.remote.DataGravitationCleanupCommand;
import org.jboss.cache.commands.tx.PrepareCommand;
import org.jboss.cache.commands.tx.CommitCommand;
import org.jboss.cache.commands.legacy.write.*;
import org.jboss.cache.commands.legacy.read.LegacyGravitateDataCommand;
import org.jboss.cache.commands.write.*;

import java.util.Map;
import java.util.HashMap;

/**
 * Specialization of ReplicationListener for optimistic caches.
 *
 * @author Mircea.Markus@jboss.com
 */
public class PessReplicationListener extends ReplicationListener
{
   private static Map<Class<? extends ReplicableCommand>, Class<? extends ReplicableCommand>> mvcc2PessMap =
         new HashMap<Class<? extends ReplicableCommand>, Class<? extends ReplicableCommand>>();

   static
   {
      mvcc2PessMap.put(ClearDataCommand.class, PessClearDataCommand.class);
      mvcc2PessMap.put(MoveCommand.class, PessMoveCommand.class);
      mvcc2PessMap.put(PutDataMapCommand.class, PessPutDataMapCommand.class);
      mvcc2PessMap.put(PutForExternalReadCommand.class, PessPutForExternalReadCommand.class);
      mvcc2PessMap.put(PutKeyValueCommand.class, PessPutKeyValueCommand.class);
      mvcc2PessMap.put(RemoveKeyCommand.class, PessRemoveKeyCommand.class);
      mvcc2PessMap.put(RemoveNodeCommand.class, PessRemoveNodeCommand.class);
      mvcc2PessMap.put(DataGravitationCleanupCommand.class, DataGravitationCleanupCommand.class);
      mvcc2PessMap.put(GravitateDataCommand.class, LegacyGravitateDataCommand.class);
   }


   public PessReplicationListener(Cache cache)
   {
      super(cache);
   }

   /**
    * In this scenario, all the commands shold be replaced wiht their pessimistic cunterparts.
    */
   public void expect(Class<? extends ReplicableCommand>... expectedCommands)
   {
      for (Class<? extends ReplicableCommand> command: expectedCommands)
      {
         super.internalExpect(getPessCommand(command));
      }
   }

   public void expectWithTx(Class<? extends ReplicableCommand>... commands)
   {
      for (Class<? extends ReplicableCommand> command : commands)
      {
         if (command.equals(PessPutForExternalReadCommand.class))
         {
            throw new IllegalArgumentException("PFER are not part of tx, use no-tx .expect()");
         }
      }
      internalExpect(PrepareCommand.class);
      if (config.getCacheMode().isSynchronous())
      {
         internalExpect(CommitCommand.class);
      }
   }

   private Class<? extends ReplicableCommand> getPessCommand(Class<? extends ReplicableCommand> command)
   {
      Class<? extends ReplicableCommand> result = mvcc2PessMap.get((Class<? extends ReplicableCommand>) command);
      if (result == null) throw new IllegalStateException("Unknown command: " + command);
      return result;
   }
}
