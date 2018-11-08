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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mock version of a JBoss AS clustered session.
 * 
 * @author Brian Stansberry
 */
public class Session
{
   private volatile SessionMetadata metadata = new SessionMetadata();
   private final AtomicInteger version = new AtomicInteger();
   private final AtomicLong timestamp = new AtomicLong(metadata.creationTime);
   private final Map<String, Object> attributes = new ConcurrentHashMap<String, Object>();
   private final Set<String> modifiedKeys = new HashSet<String>();
   private final Set<String> removedKeys = new HashSet<String>();
   private final SessionManager manager;
   private boolean outdated;
   private boolean metadataDirty = true;
   
   public Session(SessionManager manager)
   {
      this.manager = manager;
   }
   
   public String getId()
   {
      return metadata.id;
   }
   
   public long getCreationTime()
   {
      return metadata.creationTime;
   }
   
   public boolean isValid()
   {
      return metadata.valid;
   }
   
   public void invalidate()
   {
      this.metadata.valid = false;
      manager.removeSession(getId(), false, true);
   }
   
   public Object setAttribute(String key, Object value)
   {
      modifiedKeys.add(key);
      removedKeys.remove(key);
      return attributes.put(key, value);
   }
   
   public Object removeAttribute(String key)
   {
      removedKeys.add(key);
      modifiedKeys.remove(key);
      return attributes.remove(key);
   }
   
   public Object getAttribute(String key)
   {
      return attributes.get(key);
   }

   public AtomicLong getTimestamp()
   {
      return timestamp;
   }
   
   public void setMetadataDirty()
   {
      this.metadataDirty = true;
   }

   public void access()
   {
      this.timestamp.set(System.currentTimeMillis());      
   } 
   
   public void store()
   {
      version.incrementAndGet();
      
      switch (manager.getGranularity())
      {
         case SESSION:
            manager.storeSession(getId(), version, timestamp, getMetadataForReplication(), getAttributesForReplication());
            break;
         case ATTRIBUTE:
            Map<String, Object> modified = new HashMap<String, Object>();
            for (String key : modifiedKeys)
            {
               modified.put(key, attributes.get(key));
            }
            manager.storeSession(getId(), version, timestamp, getMetadataForReplication(), modified, removedKeys);
            break;
         case FIELD:
            throw new UnsupportedOperationException("implement me");            
      }
      this.metadataDirty = false;
   }

   public SessionManager getManager()
   {
      return manager;
   }

   protected Map<String, Object> getAttributes()
   {
      return attributes;
   }

   protected SessionMetadata getMetadata()
   {
      return metadata;
   }

   protected AtomicInteger getVersion()
   {
      return version;
   }

   protected Map<String, Object> getAttributesForReplication()
   {
      return attributes;
   }
   
   private SessionMetadata getMetadataForReplication()
   {
      return metadataDirty ? metadata : null;
   }
   
   protected void replicateAttributeChanges()
   {
      // no-op
   }

   protected boolean isOutdated()
   {
      return outdated;
   }

   protected void setOutdated(boolean outdated)
   {
      this.outdated = outdated;
   }

   protected void update(AtomicInteger version, AtomicLong timestamp, SessionMetadata metadata,
         Map<String, Object> attributes)
   {
      if (version == null)
         throw new IllegalArgumentException("version is null");
      if (timestamp == null)
         throw new IllegalArgumentException("timestamp is null");
      if (metadata == null)
         throw new IllegalArgumentException("metadata is null");
      if (attributes == null)
         throw new IllegalArgumentException("attributes is null");
      
      this.version.set(version.get());
      this.timestamp.set(version.get());
      this.metadata = metadata;
      
      this.attributes.clear();
      this.attributes.putAll(attributes);
      
      this.outdated = false;
   }  
   
}
