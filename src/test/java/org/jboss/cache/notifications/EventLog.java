package org.jboss.cache.notifications;

import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeActivated;
import org.jboss.cache.notifications.annotation.NodeCreated;
import org.jboss.cache.notifications.annotation.NodeEvicted;
import org.jboss.cache.notifications.annotation.NodeInvalidated;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.annotation.NodeMoved;
import org.jboss.cache.notifications.annotation.NodePassivated;
import org.jboss.cache.notifications.annotation.NodeRemoved;
import org.jboss.cache.notifications.annotation.NodeVisited;
import org.jboss.cache.notifications.annotation.TransactionCompleted;
import org.jboss.cache.notifications.annotation.TransactionRegistered;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.notifications.event.EventImpl;

import java.util.ArrayList;
import java.util.List;

@CacheListener
public class EventLog
{
   public final List<Event> events = new ArrayList<Event>();

   @NodeCreated
   @NodeRemoved
   @NodeModified
   @NodeVisited
   @NodeMoved
   @TransactionCompleted
   @TransactionRegistered
   @NodeEvicted
   @NodePassivated
   @NodeActivated
   @NodeInvalidated
   public void callback(Event e)
   {
      events.add(e);
   }

   public String toString()
   {
      return "EventLog{" +
            "events=" + events +
            '}';
   }

   /**
    * Done when we don't have a Transaction reference to compare with, e.g., when using implicit transactions in
    * opt locking.
    */
   public void scrubImplicitTransactions()
   {
      for (Event e : events) ((EventImpl) e).setTransaction(null);
   }
}