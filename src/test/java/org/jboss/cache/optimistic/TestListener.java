/*
 * Created on 23-Feb-2005
 *
 *
 *
 */
package org.jboss.cache.optimistic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeCreated;
import org.jboss.cache.notifications.event.NodeEvent;

/**
 * @author swoodcock
 */
@CacheListener
public class TestListener
{

   Log log = LogFactory.getLog(getClass());
   private int nodesAdded = 0;

   /* (non-Javadoc)
   * @see org.jboss.cache.TreeCacheListener#nodeCreated(org.jboss.cache.Fqn)
   */
   @NodeCreated
   public synchronized void nodeCreated(NodeEvent e)
   {
      if (e.isPre())
      {
         nodesAdded++;
         log.info("DataNode created " + e.getFqn());
      }
   }

   /**
    * @return Returns the nodesAdded.
    */
   public int getNodesAdded()
   {
      return nodesAdded;
   }

   /**
    * @param nodesAdded The nodesAdded to set.
    */
   public void setNodesAdded(int nodesAdded)
   {
      this.nodesAdded = nodesAdded;
   }
}
