package org.jboss.cache.notifications;

import static org.easymock.EasyMock.*;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.buddyreplication.BuddyGroup;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.invocation.MVCCInvocationContext;
import org.jboss.cache.notifications.annotation.*;
import org.jboss.cache.notifications.event.*;
import org.jgroups.View;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import java.util.HashMap;
import java.util.Map;

/**
 * Tester class for {@link org.jboss.cache.notifications.NotifierImpl}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "notifications.NotifierTest")
public class NotifierTest
{
   private NotifierImpl notifier;
   private InvocationContext ctx;
   private AllEventsListener allEventsListener;
   private Fqn fqn = Fqn.fromString("/a/b/c");

   @BeforeMethod
   public void setUp()
   {
      notifier = new NotifierImpl();
      CacheSPI cacheSPI = createNiceMock(CacheSPI.class);
      expect(cacheSPI.getInvocationContext()).andStubReturn(new MVCCInvocationContext());
      replay(cacheSPI);
      notifier.injectDependencies(cacheSPI, new Configuration());
      notifier.start();
      ctx = new MVCCInvocationContext();
      allEventsListener = new AllEventsListener();
      notifier.addCacheListener(allEventsListener);
   }

   @AfterMethod
   public void tearDown()
   {
      notifier.stop();
      notifier.destroy();
      notifier = null;
   }

   public void testNotifyNodeCreated()
   {
      assert allEventsListener.nodeCreatedEvent == null;
      notifier.notifyNodeCreated(fqn, true, ctx);
      assert allEventsListener.nodeCreatedEvent != null;
      assert allEventsListener.nodeCreatedEvent.getType() == Event.Type.NODE_CREATED;
   }


   public void testShouldNotifyOnNodeModified()
   {
      assert notifier.shouldNotifyOnNodeModified();
      notifier.destroy();
      assert !notifier.shouldNotifyOnNodeModified();
   }

   public void testNotifyNodeModified()
   {
      assert allEventsListener.nodeModifiedEvent == null;
      Map<String, String> expected = new HashMap<String, String>();
      expected.put("k", "v");
      notifier.notifyNodeModified(fqn, true, NodeModifiedEvent.ModificationType.PUT_DATA, expected, ctx);
      assert allEventsListener.nodeModifiedEvent != null;
      assert allEventsListener.nodeModifiedEvent.getData().equals(expected);
      assert allEventsListener.nodeModifiedEvent.getModificationType() == NodeModifiedEvent.ModificationType.PUT_DATA;
   }

   public void testNotifyNodeRemoved()
   {
      assert allEventsListener.nodeRemoveEvent == null;
      Map<String, String> data = new HashMap<String, String>();
      data.put("k", "v");
      notifier.notifyNodeRemoved(fqn, true, data, ctx);
      assert allEventsListener.nodeRemoveEvent != null;
      assert allEventsListener.nodeRemoveEvent.getData().equals(data);
      assert allEventsListener.nodeRemoveEvent.getType() == Event.Type.NODE_REMOVED;
   }

   public void testNotifyNodeVisited()
   {
      assert allEventsListener.nodeVisistedEvent == null;
      notifier.notifyNodeVisited(fqn, true, ctx);
      assert allEventsListener.nodeVisistedEvent != null;
      assert allEventsListener.nodeVisistedEvent.getType() == Event.Type.NODE_VISITED;
   }

   public void testNotifyNodeMoved()
   {
      assert allEventsListener.nodeMovedEvent == null;
      Fqn second = Fqn.fromString("/a/s/f");
      notifier.notifyNodeMoved(fqn, second, true, ctx);
      assert allEventsListener.nodeMovedEvent != null;
      assert allEventsListener.nodeMovedEvent.getFqn().equals(fqn);
      assert allEventsListener.nodeMovedEvent.getTargetFqn().equals(second);
      assert allEventsListener.nodeMovedEvent.getType() == Event.Type.NODE_MOVED;
   }

   public void testNotifyNodeEvicted()
   {
      assert allEventsListener.nodeEvictedEvent == null;
      notifier.notifyNodeEvicted(fqn, true, ctx);
      assert allEventsListener.nodeEvictedEvent != null;
      assert allEventsListener.nodeEvictedEvent.getFqn().equals(fqn);
      assert allEventsListener.nodeEvictedEvent.getType() == Event.Type.NODE_EVICTED;
   }

   public void testNotifyNodeLoaded()
   {
      assert allEventsListener.nodeLoadedEvent == null;
      Map<String, String> expected = new HashMap<String, String>();
      expected.put("key", "value");
      notifier.notifyNodeLoaded(fqn, true, expected, ctx);
      assert allEventsListener.nodeLoadedEvent != null;
      assert allEventsListener.nodeLoadedEvent.getFqn().equals(fqn);
      assert allEventsListener.nodeLoadedEvent.getData().equals(expected);
      assert allEventsListener.nodeLoadedEvent.getType() == Event.Type.NODE_LOADED;
   }

   public void testNotifyNodeActivated()
   {
      assert allEventsListener.nodeActivatedEvent == null;
      Map<String, String> expected = new HashMap<String, String>();
      expected.put("key", "value");
      notifier.notifyNodeActivated(fqn, true, expected, ctx);
      assert allEventsListener.nodeActivatedEvent != null;
      assert allEventsListener.nodeActivatedEvent.getFqn().equals(fqn);
      assert allEventsListener.nodeActivatedEvent.getData().equals(expected);
      assert allEventsListener.nodeActivatedEvent.getType() == Event.Type.NODE_ACTIVATED;
   }

   public void testNotifyNodePassivated()
   {
      assert allEventsListener.nodePassivatedEvent == null;
      Map<String, String> expected = new HashMap<String, String>();
      expected.put("key", "value");
      notifier.notifyNodePassivated(fqn, true, expected, ctx);
      assert allEventsListener.nodePassivatedEvent != null;
      assert allEventsListener.nodePassivatedEvent.getFqn().equals(fqn);
      assert allEventsListener.nodePassivatedEvent.getData().equals(expected);
      assert allEventsListener.nodePassivatedEvent.getType() == Event.Type.NODE_PASSIVATED;
   }

   public void testNotifyCacheStarted()
   {
      assert allEventsListener.cacheStartedEvent == null;
      notifier.notifyCacheStarted();
      assert allEventsListener.cacheStartedEvent != null;
      assert allEventsListener.cacheStartedEvent.getType() == Event.Type.CACHE_STARTED;
   }

   public void testNotifyCacheStopped()
   {
      assert allEventsListener.cacheStoppedEvent == null;
      notifier.notifyCacheStopped();
      assert allEventsListener.cacheStoppedEvent != null;
      assert allEventsListener.cacheStoppedEvent.getType() == Event.Type.CACHE_STOPPED;
   }

   public void testNotifyViewChange()
   {
      assert allEventsListener.viewChanged == null;
      View view = new View();
      notifier.notifyViewChange(view, ctx);
      assert allEventsListener.viewChanged != null;
      assert allEventsListener.viewChanged.getNewView().equals(view);
      assert allEventsListener.viewChanged.getType() == Event.Type.VIEW_CHANGED;
   }

   public void testNotifyBuddyGroupChange()
   {
      assert allEventsListener.buddyGroupChangedEvent == null;
      BuddyGroup buddyGroup = new BuddyGroup();
      notifier.notifyBuddyGroupChange(buddyGroup, true);
      assert allEventsListener.buddyGroupChangedEvent != null;
      assert allEventsListener.buddyGroupChangedEvent.getBuddyGroup().equals(buddyGroup);
      assert allEventsListener.buddyGroupChangedEvent.getType() == Event.Type.BUDDY_GROUP_CHANGED;
   }

   public void testNotifyTransactionCompleted()
   {
      assert allEventsListener.transactionCompleted == null;
      Transaction tx = createNiceMock(Transaction.class);
      notifier.notifyTransactionCompleted(tx, false, ctx);
      assert allEventsListener.transactionCompleted != null;
      assert allEventsListener.transactionCompleted.getTransaction() == tx;
      assert !allEventsListener.transactionCompleted.isSuccessful();
      assert allEventsListener.transactionCompleted.getType() == Event.Type.TRANSACTION_COMPLETED;
   }

   public void testNotifyTransactionRegistered()
   {
      assert allEventsListener.transactionRegistered == null;
      Transaction tx = createNiceMock(Transaction.class);
      notifier.notifyTransactionRegistered(tx, ctx);
      assert allEventsListener.transactionRegistered != null;
      assert allEventsListener.transactionRegistered.getTransaction() == tx;
      assert allEventsListener.transactionRegistered.getType() == Event.Type.TRANSACTION_REGISTERED;
   }

   public void testNotifyCacheBlocked()
   {
      assert allEventsListener.cacheBlockedEvent == null;
      notifier.notifyCacheBlocked(false);
      assert allEventsListener.cacheBlockedEvent != null;
      assert !allEventsListener.cacheBlockedEvent.isPre();
      assert allEventsListener.cacheBlockedEvent.getType() == Event.Type.CACHE_BLOCKED;
   }

   public void testNotifyCacheUnblocked()
   {
      assert allEventsListener.cacheUnblockedEvent == null;
      notifier.notifyCacheUnblocked(false);
      assert allEventsListener.cacheUnblockedEvent != null;
      assert !allEventsListener.cacheUnblockedEvent.isPre();
      assert allEventsListener.cacheUnblockedEvent.getType() == Event.Type.CACHE_UNBLOCKED;
   }

   @CacheListener
   public static class AllEventsListener
   {
      CacheStartedEvent cacheStartedEvent;
      CacheStoppedEvent cacheStoppedEvent;
      CacheBlockedEvent cacheBlockedEvent;
      CacheUnblockedEvent cacheUnblockedEvent;
      NodeCreatedEvent nodeCreatedEvent;
      NodeRemovedEvent nodeRemoveEvent;
      NodeVisitedEvent nodeVisistedEvent;
      NodeModifiedEvent nodeModifiedEvent;
      NodeMovedEvent nodeMovedEvent;
      NodeActivatedEvent nodeActivatedEvent;
      NodePassivatedEvent nodePassivatedEvent;
      NodeLoadedEvent nodeLoadedEvent;
      NodeEvictedEvent nodeEvictedEvent;
      TransactionRegisteredEvent transactionRegistered;
      TransactionCompletedEvent transactionCompleted;
      ViewChangedEvent viewChanged;
      BuddyGroupChangedEvent buddyGroupChangedEvent;

      @CacheStarted
      public void onCacheStarted(CacheStartedEvent event)
      {
         cacheStartedEvent = event;
      }

      @CacheStopped
      public void onCacheStopped(CacheStoppedEvent event)
      {
         cacheStoppedEvent = event;
      }

      @CacheBlocked
      public void onCacheBlocked(CacheBlockedEvent event)
      {
         cacheBlockedEvent = event;
      }

      @CacheUnblocked
      public void onCacheUnblocked(CacheUnblockedEvent event)
      {
         cacheUnblockedEvent = event;
      }

      @NodeCreated
      public void onNodeCreated(NodeCreatedEvent event)
      {
         nodeCreatedEvent = event;
      }

      @NodeRemoved
      public void onNodeRemoved(NodeRemovedEvent event)
      {
         nodeRemoveEvent = event;
      }

      @NodeVisited
      public void onNodeVisited(NodeVisitedEvent event)
      {
         nodeVisistedEvent = event;
      }

      @NodeModified
      public void onNodeModified(NodeModifiedEvent event)
      {
         nodeModifiedEvent = event;
      }

      @NodeMoved
      public void onNodeMoved(NodeMovedEvent event)
      {
         nodeMovedEvent = event;
      }

      @NodeActivated
      public void onNodeActivated(NodeActivatedEvent event)
      {
         nodeActivatedEvent = event;
      }

      @NodePassivated
      public void onNodePassivated(NodePassivatedEvent event)
      {
         nodePassivatedEvent = event;
      }

      @NodeLoaded
      public void onNodeLoaded(NodeLoadedEvent event)
      {
         nodeLoadedEvent = event;
      }

      @NodeEvicted
      public void onNodeEvicted(NodeEvictedEvent event)
      {
         nodeEvictedEvent = event;
      }

      @TransactionRegistered
      public void onTransactionRegistered(TransactionRegisteredEvent event)
      {
         transactionRegistered = event;
      }

      @TransactionCompleted
      public void onTransactionCompleted(TransactionCompletedEvent event)
      {
         transactionCompleted = event;
      }

      @ViewChanged
      public void onViewChanged(ViewChangedEvent event)
      {
         viewChanged = event;
      }

      @BuddyGroupChanged
      public void onBuddyGroupChanged(BuddyGroupChangedEvent event)
      {
         buddyGroupChangedEvent = event;
      }
   }
}
