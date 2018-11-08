/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.cache.integration.websession.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.*;
import org.jboss.cache.loader.CacheLoaderManager;
import org.jboss.cache.buddyreplication.BuddyManager;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.integration.websession.util.WebAppMetadata.Granularity;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.annotation.NodeRemoved;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import org.jboss.cache.notifications.event.NodeRemovedEvent;

/**
 * Mock version of a JBoss AS web session manager.
 * 
 * @author Brian Stansberry
 */
@CacheListener
public class SessionManager
{
   
   private static final Integer VERSION = Integer.valueOf(0);
   private static final Integer TIMESTAMP = Integer.valueOf(1);
   private static final Integer METADATA = Integer.valueOf(2);
   private static final Integer ATTRIBUTES = Integer.valueOf(3);
   
   private final CacheManager cacheManager;
   private final WebAppMetadata appMetadata;
   private final String contextHostName;
   private final Fqn<String> baseFqn;
   private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>(); 
   private Cache<Object, Object> cache;
   private TransactionManager tm;
   private boolean buddyReplication;
   private boolean started;
   private final boolean fieldBased;
   private final boolean attributeBased;
   private final Log log = LogFactory.getLog(getClass());
   
   // ------------------------------------------------------------ Constructors
   
   public SessionManager(CacheManager cacheManager, WebAppMetadata metadata)
   {
      this.cacheManager = cacheManager;
      this.appMetadata = metadata;
      this.contextHostName = appMetadata.warName + "_localhost";
      this.baseFqn = Fqn.fromElements(FqnUtil.JSESSION, contextHostName);
      this.fieldBased = appMetadata.granularity == Granularity.FIELD;
      this.attributeBased = appMetadata.granularity == Granularity.ATTRIBUTE;
   }
   
   // --------------------------------------------------------- Test Driver API
   
   public boolean isStarted()
   {
      return started;
   }
   
   public void start()
   {
      this.started = true;
      
      try
      {
         this.cache = cacheManager.getCache(appMetadata.cacheConfigName, false);
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
      
      cache.addCacheListener(this);
      
      if (cache.getCacheStatus() != CacheStatus.STARTED)
      {
         cache.start();
      }
      this.tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      if (tm == null)
      {
         throw new IllegalStateException("tm is null");
      }
      BuddyReplicationConfig brc = cache.getConfiguration().getBuddyReplicationConfig();
      this.buddyReplication = brc != null && brc.isEnabled();      
   }
   
   public void stop() throws Exception
   {     
      if (started)
      {
         if (cache != null)
         {
            cache.removeCacheListener(this);
            
            // FIXME see if we need more sophisticated cache cleanup
            removeInMemoryData((CacheSPI)cache);
            clearCacheLoader((CacheSPI)cache);
            cacheManager.releaseCache(appMetadata.cacheConfigName);
            cache = null;
         }
      }
   }

   private void clearCacheLoader(CacheSPI cache)
   {
      CacheLoaderManager cacheLoaderManager = cache.getCacheLoaderManager();
      if (cacheLoaderManager != null && cacheLoaderManager.getCacheLoader() != null)
      {
         try
         {
            cacheLoaderManager.getCacheLoader().remove(Fqn.ROOT);
         } catch (Exception e)
         {
            throw new RuntimeException(e);
         }
      }
   }

   private void removeInMemoryData(CacheSPI cache)
   {
      if (cache.getRoot() != null)
      {
         cache.getRoot().clearDataDirect();
         cache.getRoot().removeChildrenDirect();
      }
   }

   
   /**
    * Allows test driver to mock Tomcat background processes' expiration
    * of an overage session.
    * 
    * @param id the session id
    */
   public void expireSession(String id)
   {
      Session session = removeSession(id, true, true);
      if (buddyReplication)
      {
         cleanBuddyBackupTree(id, false);
      }
      
      if (session != null)
         session.getMetadata().valid = false;
   }
   
   /**
    * Allows test driver to mock Tomcat background processes' passivation
    * of a session.
    * 
    * @param id the session id
    */
   public void passivate(String id)
   {
      if (!appMetadata.passivation)
         throw new IllegalStateException("passivation not supported");
      
      sessions.remove(id);
      
      cache.evict(getSessionFqn(id), true);
      
      if (buddyReplication)
      {
         cleanBuddyBackupTree(id, true);
      }
   }
   
   public Cache<Object, Object> getCache()
   {
      return cache;
   }
   
   public String getContextHostName()
   {
      return contextHostName;
   }
   
   
   
   // ----------------------------------------------------------- CacheListener
   

   @NodeRemoved
   public void nodeRemoved(NodeRemovedEvent event)
   {      
      if (event.isPre())
         return;
      
      boolean local = event.isOriginLocal();
      if (!fieldBased && local)
         return;
      
      @SuppressWarnings("unchecked")
      Fqn<String> fqn = event.getFqn();
      boolean isBuddy = FqnUtil.isBuddyFqn(fqn);
      
      if (!local 
            && FqnUtil.isFqnSessionRootSized(fqn, isBuddy) 
            && FqnUtil.isFqnForOurWebapp(fqn, contextHostName, isBuddy))
      {
         // A session has been invalidated from another node;
         // need to inform manager
         String sessId = FqnUtil.getIdFromFqn(fqn, isBuddy);
         removeSession(sessId, true, false);
      }
      else if (local && !isBuddy
                  && FqnUtil.isPossibleInternalPojoFqn(fqn) 
                  && FqnUtil.isFqnForOurWebapp(fqn, contextHostName, isBuddy))
      {
         // One of our sessions' pojos is modified; need to inform
         // the manager so it can mark the session dirty
         String sessId = FqnUtil.getIdFromFqn(fqn, isBuddy);
         notifyLocalAttributeModification(sessId);
      }
   }

   @NodeModified
   public void nodeModified(NodeModifiedEvent event)
   {      
      if (event.isPre())
         return;
      
      boolean local = event.isOriginLocal();
      if (!fieldBased && local)
         return;
      
      @SuppressWarnings("unchecked")
      Fqn<String> fqn = event.getFqn();
      boolean isBuddy = FqnUtil.isBuddyFqn(fqn);      
      
      if (!local 
             && FqnUtil.isFqnSessionRootSized(fqn, isBuddy)
             && FqnUtil.isFqnForOurWebapp(fqn, contextHostName, isBuddy))
      {
         // Query if we have version value in the distributed cache. 
         // If we have a version value, compare the version and invalidate if necessary.
         @SuppressWarnings("unchecked")
         Map<Object, Object> data = event.getData();
         AtomicInteger version = (AtomicInteger) data.get(VERSION);
         if(version != null)
         {
            String realId = FqnUtil.getIdFromFqn(fqn, isBuddy);
            String owner = isBuddy ? FqnUtil.getBuddyOwner(fqn) : null;
            AtomicLong timestamp = (AtomicLong) data.get(TIMESTAMP);
            if (timestamp == null)
            {
               log.warn("No timestamp attribute found in " + fqn);
            }
            else
            {
               // Notify the manager that a session has been updated
               boolean updated = sessionChangedInDistributedCache(realId, owner, 
                                                  version.get());
               if (!updated && !isBuddy)
               {
                  log.warn("Possible concurrency problem: Replicated version id " + 
                            version + " is less than or equal to in-memory version for session " + realId); 
               }
               /*else 
               {
                  We have a local session but got a modification for the buddy tree.
                  This means another node is in the process of taking over the session;
                  we don't worry about it
               }
                */
            }
         }
         else if (!attributeBased) // other granularities can modify attributes only
         {
            log.warn("No version attribute found in " + fqn);
         }
      }
      else if (local && !isBuddy
            && FqnUtil.isPossibleInternalPojoFqn(fqn) 
            && FqnUtil.isFqnForOurWebapp(fqn, contextHostName, isBuddy))
      {
         // One of our sessions' pojos is modified; need to inform
         // the manager so it can mark the session dirty
         String sessId = FqnUtil.getIdFromFqn(fqn, isBuddy);
         notifyLocalAttributeModification(sessId);
      }
   }

   // ------------------------------------------------------------ Internal API
   
   protected boolean isBatchStarted()
   {
      try
      {
         return tm.getTransaction() != null;
      }
      catch (SystemException e)
      {
         throw new RuntimeException("failed checking for tx", e);
      }
   }
   
   protected void startBatch()
   {
      try
      {
         tm.begin();
      }
      catch (Exception e)
      {
         throw new RuntimeException("failed starting tx", e);
      }
   }
   
   protected void endBatch()
   {
      try
      {
         tm.commit();
      }
      catch (Exception e)
      {
         throw new RuntimeException("failed committing tx", e);
      }
   }
   
   protected Session findSession(String id)
   {
      Session session = sessions.get(id);
      if (session == null || session.isOutdated())
      {
         session = loadSession(id);
         if (session != null)
         {
            sessions.put(id, session);
         }
      }
      return session;
   }

   protected Session createSession()
   {
      Session session = createEmptySession();
      sessions.put(session.getId(), session);
      return session;
   }
   
   protected Session removeSession(String id, boolean localOnly, boolean localCall)
   {
      Session session = sessions.remove(id);
      if (localCall)
      {
         // TODO mock the bit where each individual attribute is removed first
         
         // Remove the session node
         if (localOnly)
         {
            cache.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
         }
         log("cache.removeNode(" + getSessionFqn(id) + ") locally? " + localOnly);
         cache.removeNode(getSessionFqn(id));
      }
      
      return session;
   }
   
   protected Granularity getGranularity()
   {
      return appMetadata.granularity;
   }
   
   @SuppressWarnings("unchecked")
   protected Map<String, Object> loadSessionAttributes(String id, Map<Object, Object> sessionNodeData)
   {
      Map<String, Object> result = new HashMap<String, Object>();
      switch (appMetadata.granularity)
      {
         case SESSION:
            result.putAll((Map<String, Object>) sessionNodeData.get(ATTRIBUTES));
            break;
         case ATTRIBUTE:
            for (Map.Entry<Object, Object> entry : sessionNodeData.entrySet())
            {
               if (entry.getKey() instanceof String)
               {
                  result.put((String) entry.getKey(), entry.getValue());
               }
            }
            break;
         case FIELD:
            throw new IllegalStateException("implement");
      }
      
      return result;
   }
   
   /**
    * JBC write for granularity SESSION.
    */
   protected void storeSession(String id, AtomicInteger version, AtomicLong timestamp, SessionMetadata metadata, Map<String, Object> attributes)
   {
      if (version == null)
         throw new IllegalArgumentException("version is null");
      if (timestamp == null)
         throw new IllegalArgumentException("timestamp is null");
      
      Fqn<String> fqn = getSessionFqn(id);
      
      Map<Object, Object> data = new HashMap<Object, Object>();
      data.put(VERSION, version);
      data.put(TIMESTAMP, timestamp);
      if (metadata != null)
      {
         data.put(METADATA, metadata);
      }
      if (attributes != null)
      {
         data.put(ATTRIBUTES, attributes);         
      }

      log("cache.put(" + fqn +"," + data +")");
      cache.put(fqn, data);
   }
   
   /**
    * JBC write for granularity ATTRIBUTE.
    */
   protected void storeSession(String id, AtomicInteger version, AtomicLong timestamp, SessionMetadata metadata, Map<String, Object> modifiedAttributes, Set<String> removedAttributes)
   {
      storeSession(id, version, timestamp, metadata, null);
      
      Fqn<String> fqn = getSessionFqn(id);
      
      if (modifiedAttributes != null)
      {
         log("cache.put(" + fqn+"," + modifiedAttributes+ ")");
         cache.put(fqn, modifiedAttributes);
      }
      
      if (removedAttributes != null)
      {
         for (String key : removedAttributes)
         {
            log("cache.remove(" + fqn + "," + key + ")");
            cache.remove(fqn, key);
         }
      }
   }
   
   // ----------------------------------------------------------------- Private
   
   private Fqn<String> getSessionFqn(String id)
   {
      return Fqn.fromRelativeElements(baseFqn, id);
   }

   private Session createEmptySession()
   {
      Session session = null;
      switch (appMetadata.granularity)
      {
         case SESSION:
         case ATTRIBUTE:
            session = new Session(this);
            break;
         case FIELD:
            throw new IllegalStateException("implement");
      }
      return session;
   }
   
   /**
    * JBC read of a session.
    */
   private Session loadSession(String id)
   {
      Session session = null;
      
      boolean startTx = !isBatchStarted();      
      if (startTx)
         startBatch();
      Map<Object, Object> data = null;
      try
      {
         if (buddyReplication)
         {
            cache.getInvocationContext().getOptionOverrides().setForceDataGravitation(true);
         }
         log ("cache.getData(" + getSessionFqn(id) + ") with ForceDataGravitation(true)");
         data = cache.getData(getSessionFqn(id));
      }
      finally
      {
         if (startTx)
            endBatch();
      }
      
      if (data != null)
      {
         session = createEmptySession();
         AtomicInteger version = (AtomicInteger) data.get(VERSION);
         AtomicLong timestamp = (AtomicLong) data.get(TIMESTAMP);
         SessionMetadata metadata = (SessionMetadata) data.get(METADATA);
         Map<String, Object> attributes = loadSessionAttributes(id, data);
         session.update(version, timestamp, metadata, attributes);
      }
      return session;
   }
   
   private boolean sessionChangedInDistributedCache(String realId, String owner, int version)
   {
      Session session = sessions.get(realId);
      if (session != null)
      {
         session.setOutdated(true);
         if (session.getVersion().get() >= version)
            return false;
      }
      return true;
   }
   
   private void notifyLocalAttributeModification(String sessId)
   {
      Session session = sessions.get(sessId);
      if (session != null)
      {
         session.setOutdated(true);
      }      
   }
   
   private void cleanBuddyBackupTree(String id, boolean evict)
   {
      Fqn<String> mainFqn = getSessionFqn(id);
      Node<Object, Object> root = cache.getNode(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN);
      if (root != null)
      {
         Set<Node<Object, Object>> children = root.getChildren();
         for (Node<Object, Object> child : children)
         {            
            @SuppressWarnings("unchecked")
            Fqn<String> backupFqn = Fqn.fromRelativeFqn(child.getFqn(), mainFqn);
            if (evict)
            {
               log("cache.evict(" + backupFqn + ", true)");
               cache.evict(backupFqn, true);
            }
            else
            {
               cache.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
               cache.removeNode(backupFqn);
            }
         }
      }
      
   }

   private void log(String what)
   {
      System.out.println("[" + cache.getLocalAddress() + "] " + what);
   }

}
