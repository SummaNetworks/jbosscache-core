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

import java.util.Map;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.CacheLoaderManager;
import org.jboss.cache.buddyreplication.BuddyFqnTransformer;
import org.jboss.cache.buddyreplication.BuddyManager;

/**
 * @author Brian Stansberry
 *
 */
public class BuddyReplicationAssertions
{
   public static void assertBuddyBackup(String contextHostName, String sessionId, Cache<Object, Object> owner, Cache<Object, Object> backup) throws Exception
   {
      Fqn<String> fqn = Fqn.fromElements(FqnUtil.JSESSION, contextHostName, sessionId);
      Map<Object, Object> owned = owner.getData(fqn);
      assert owned != null : "owned is null";
      
      Fqn bFqn = new BuddyFqnTransformer().getBackupFqn(owner.getLocalAddress(), fqn);
      Map<Object, Object> backed = owner.getData(fqn);
      assert backed != null : "backed is null";
      
      assert owned.size() == backed.size() : "sizes differ; owned = " + owned.size() + " backed = " + backed.size();
      
      for (Map.Entry<Object, Object> entry : owned.entrySet())
      {
         Object backVal = backed.get(entry.getKey());
         assert backVal != null : "null backVal for " + entry.getKey();
         assert backVal.equals(entry.getValue()) : "differing val for " + entry.getKey() + " " + entry.getValue() + " vs. " + backVal;
      }
      
      assertMainTreeClear(contextHostName, sessionId, backup);
      assertBuddyTreeClear(contextHostName, sessionId, owner);
   }
   
   public static void assertUnrelated(String contextHostName, String sessionId, Cache<Object, Object> cache) throws Exception
   {
      assertMainTreeClear(contextHostName, sessionId, cache);
      assertBuddyTreeClear(contextHostName, sessionId, cache);
   }
   
   public static void assertMainTreeClear(String contextHostName, String sessionId, Cache<Object, Object> cache) throws Exception
   {
      Fqn<String> fqn = Fqn.fromElements(FqnUtil.JSESSION, contextHostName, sessionId);
      verifyCacheLoader(cache, fqn);
      assert cache.getNode(fqn) == null : "found node for " + fqn + " on cache instance " + cache.getLocalAddress();
   }

   public static void assertBuddyTreeClear(String contextHostName, String sessionId, Cache<Object, Object> cache) throws Exception
   {
      Fqn<String> fqn = Fqn.fromElements(FqnUtil.JSESSION, contextHostName, sessionId);
      verifyCacheLoader(cache, fqn);
      Node<Object, Object> bbRoot = cache.getNode(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN);
      if (bbRoot != null)
      {
         for(Node<Object, Object> child : bbRoot.getChildren())
         {
            Node<Object, Object> bad = child.getChild(fqn);
            assert bad == null : "found bad node at " + Fqn.fromRelativeFqn(child.getFqn(), fqn);
         }
      }
   }

   private static void verifyCacheLoader(Cache<Object, Object> cache, Fqn<String> fqn)
         throws Exception
   {
      CacheLoaderManager loaderManager = ((CacheSPI) cache).getCacheLoaderManager();
      if (loaderManager != null && loaderManager.getCacheLoader() != null)
      {
         CacheLoader cl = loaderManager.getCacheLoader();
         assert !cl.exists(fqn) : "found node for " + fqn + " on cache loader of cache " + cache.getLocalAddress();
      }
   }

   private BuddyReplicationAssertions() {}
}
