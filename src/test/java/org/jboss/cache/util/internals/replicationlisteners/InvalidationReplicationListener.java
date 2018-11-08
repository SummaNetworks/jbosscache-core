package org.jboss.cache.util.internals.replicationlisteners;

import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.legacy.write.VersionedInvalidateCommand;
import org.jboss.cache.commands.write.InvalidateCommand;
import org.jboss.cache.commands.write.PutForExternalReadCommand;
import org.jboss.cache.Cache;
import org.jboss.cache.config.Configuration;

/**
 * Specialization of ReplicationListener for caches that use invalidation.
 *
 * @author Mircea.Markus@jboss.com
 */
public class InvalidationReplicationListener extends ReplicationListener
{

   Class<? extends InvalidateCommand> base;

   public InvalidationReplicationListener(Cache cache)
   {
      super(cache);
      if (cache.getConfiguration().getNodeLockingScheme().equals(Configuration.NodeLockingScheme.OPTIMISTIC))
      {
         base = VersionedInvalidateCommand.class;
      } else
      {
         base = InvalidateCommand.class;
      }
   }

   public void expect(Class<? extends ReplicableCommand>... expectedCommands)
   {
      expectInvalidations(expectedCommands);
   }

   public void expectWithTx(Class<? extends ReplicableCommand>... writeCommands)
   {
      expectInvalidations(writeCommands);
   }

   private void expectInvalidations(Class<? extends ReplicableCommand>... commands)
   {
      for (Class<? extends ReplicableCommand> command : commands)
      {
         if (command.equals(PutForExternalReadCommand.class))
         {
            internalExpect();//so that the map won't be empty
         }
         else 
         {
            internalExpect(base);
         }
      }
   }
}
