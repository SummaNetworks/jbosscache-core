package org.jboss.cache.util.internals.replicationlisteners;

import org.jboss.cache.Cache;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.tx.PrepareCommand;
import org.jboss.cache.commands.tx.CommitCommand;

/**
 * Specialization of ReplicationListener for mvcc caches.
 *
 * @author Mircea.Markus@jboss.com
 */
public class MvccReplicationListener extends ReplicationListener
{
   public MvccReplicationListener(Cache cache)
   {
      super(cache);
   }

   /**
    * all the commands should be received at the other end.
    */
   public void expect(Class<? extends ReplicableCommand>... expectedCommands)
   {
      internalExpect(expectedCommands);
   }

   public void expectWithTx(Class<? extends ReplicableCommand>... writeCommands)
   {
      internalExpect(PrepareCommand.class);
      //this is because for async replication we have an 1pc transaction
      if (config.getCacheMode().isSynchronous()) internalExpect(CommitCommand.class);
   }
}
