package org.jboss.cache.util;

import org.jboss.cache.Cache;
import org.jboss.cache.commands.read.GravitateDataCommand;
import org.jboss.cache.commands.remote.DataGravitationCleanupCommand;
import org.jboss.cache.commands.write.PutDataMapCommand;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;

import java.util.List;

/**
 * Helper class for monitoring replication between caches during data gravitation.
 * Usage:
 *  <pre>
 *   import static org.jboss.cache.util.SingleBuddyGravitationHelper
 *    ....
 *
 *    inCaches(cache1, cache2, cache3).dataWillGravitateFrom(0).to(1);
      assertEquals("value", caches.get(1).get(fqn, key)); //this call will cause data gravitation
      expectGravitation();   //here is where the failure will be if gravitation fails
 *
 *  </pre>
 *
 *
 * @author Mircea.Markus@jboss.com
 */
public class SingleBuddyGravitationHelper
{
   private Cache[] caches;
   private ReplicationListener[] replicationListeners;
   private int fromIndex = -1;
   private int toIndex = -1;
   static ThreadLocal<SingleBuddyGravitationHelper> perThread = new ThreadLocal<SingleBuddyGravitationHelper>();
   boolean strict = true;


   /**
    * Creates an <tt>SingleBuddyGravitationHelper</tt> based on the list of caches passed in.
    * The caches sequence is important, caches[i+1] must be a for caches[i].
    * Important: it is assumed  that caches do not have replication listeners already built. If so use
    * {@link SingleBuddyGravitationHelper#inReplicationListeners(java.util.List)} methods, as a cache cannot have 2
    * replication listener instances. 
    */
   public static SingleBuddyGravitationHelper inCaches(Cache... caches)
   {
      SingleBuddyGravitationHelper gravitationHelper = new SingleBuddyGravitationHelper(caches);
      perThread.set(gravitationHelper);
      return gravitationHelper;
   }

   /**
    * Transforms the List in an array and calls {@link SingleBuddyGravitationHelper#inCaches(org.jboss.cache.Cache[])}
    */
   public static SingleBuddyGravitationHelper inCaches(List caches)
   {
      Cache[] cachesArray = (Cache[]) caches.toArray(new Cache[caches.size()]);
      return inCaches(cachesArray);
   }

   /**
    * Insted of creating a replication listener for each cache, use the already build ReplicationListeners.
    */
   public static SingleBuddyGravitationHelper inReplicationListeners(ReplicationListener... caches)
   {
      SingleBuddyGravitationHelper gravitationHelper = new SingleBuddyGravitationHelper(caches);
      perThread.set(gravitationHelper);
      return gravitationHelper;
   }

   /**
    * Transforms the suplied list in an array and calls
    * {@link SingleBuddyGravitationHelper#inReplicationListeners(org.jboss.cache.util.internals.replicationlisteners.ReplicationListener[])}
    */
   public static SingleBuddyGravitationHelper inReplicationListeners(List replListeners)
   {
      ReplicationListener[] listeners = (ReplicationListener[]) replListeners.toArray(new ReplicationListener[replListeners.size()]);
      SingleBuddyGravitationHelper gravitationHelper = new SingleBuddyGravitationHelper(listeners);
      perThread.set(gravitationHelper);
      return gravitationHelper;
   }


   /**
    * After the {@link SingleBuddyGravitationHelper#dataWillGravitateFrom(int)} and {@link SingleBuddyGravitationHelper#to(int)}
    * are called, and after the gravitation call to the cache is done, here we wait for the gravitation commands to replicate.
    */
   public static void expectGravitation()
   {
      SingleBuddyGravitationHelper gravitationHelper = perThread.get();
      assert gravitationHelper != null : "replication helper should be created. Use inReplicationListeners before calling this";
      gravitationHelper.waitForReplication();
   }

   /**
    * You specify here the original data owner.
    */
   public SingleBuddyGravitationHelper dataWillGravitateFrom(int index)
   {
      assertValidIndex(index);
      this.fromIndex = index;
      return this;
   }

   /**
    * Here you specify where the data will migrate to.
    */
   public SingleBuddyGravitationHelper to(int index)
   {
      toIndex = index;
      assert fromIndex >= 0 : "Must call dataWillGravitateFrom before this one";

      //all other caches must receive an GravitateDataCommand
      expectGravitateData();

      //all other caches will receive an DataGravitationCleanup command
      expectGravitationCleanup();

      //buddy of the new data owner should also receive the data
      int cacheCount = caches.length;
      int newDataOwnerIndex = getIndex(caches[toIndex]);
      int newBuddyIndex = (newDataOwnerIndex == cacheCount - 1) ? 0 : newDataOwnerIndex + 1;
      replicationListeners[newBuddyIndex].expect(PutDataMapCommand.class);
      return this;
   }


   private SingleBuddyGravitationHelper(Cache[] caches)
   {
      this.caches = caches;
      replicationListeners = new ReplicationListener[caches.length];
      for (int i = 0; i < caches.length; i++)
      {
         replicationListeners[i] = ReplicationListener.getReplicationListener(caches[i]);
      }
   }

   private SingleBuddyGravitationHelper(ReplicationListener[] replicationListeners)
   {
      this.replicationListeners = replicationListeners;
      caches = new Cache[replicationListeners.length];
      for (int i = 0; i < replicationListeners.length; i++)
      {
         caches[i] = replicationListeners[i].getCache();
      }
   }

   private void assertValidIndex(int index)
   {
      assert index >= 0 && index < caches.length;
   }

   //disable for now
   private void strict()
   {
      this.strict = true;
   }

   private int getIndex(Cache cache)
   {
      for (int i = 0; i < caches.length; i++)
      {
         if (caches[i] == cache) return i;
      }
      throw new RuntimeException("cache not found withis cache instances");
   }

   private void expectGravitationCleanup()
   {
      if (!strict)
      {
         replicationListeners[fromIndex].expect(DataGravitationCleanupCommand.class);
         return;
      }
      for (int i = 0; i < caches.length; i++)
      {
         if (i != toIndex) replicationListeners[i].expect(DataGravitationCleanupCommand.class);
      }
   }

   private void expectGravitateData()
   {
      if (!strict) return;
      //this means that we are a buddy of the node we gravitate from, so local gravitation will take place and
      // no DataGravitation commands will be send accross
      if (newOwnerIsBuddyOfOldOwner())
      {
         return;
      }
      for (int i = 0; i < caches.length; i++)
      {
         if (caches[i] != caches[toIndex]) replicationListeners[i].expect(GravitateDataCommand.class);
      }
   }

   private boolean newOwnerIsBuddyOfOldOwner()
   {
      return (toIndex - 1 == fromIndex) || (toIndex == 0 && fromIndex == caches.length - 1);
   }

   private void waitForReplication()
   {
      for (ReplicationListener listener : replicationListeners)
      {
         if (listener.getCache() != caches[toIndex])
            listener.waitForReplicationToOccur();
      }
   }
}
