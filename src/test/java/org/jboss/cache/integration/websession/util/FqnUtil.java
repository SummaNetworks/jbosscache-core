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

import org.jboss.cache.Fqn;
import org.jboss.cache.buddyreplication.BuddyManager;

/**
 * @author Brian Stansberry
 *
 */
public class FqnUtil
{
   public static final String JSESSION = "JSESSION";
   public static final String ATTRIBUTE = "ATTRIBUTE";
   private static final int JSESSION_FQN_INDEX = 0;
   private static final int WEBAPP_FQN_INDEX = 1;
   private static final int SESSION_ID_FQN_INDEX = 2;
   private static final int SESSION_FQN_SIZE = SESSION_ID_FQN_INDEX + 1;
   private static final int BUDDY_BACKUP_ROOT_OWNER_INDEX = BuddyManager.BUDDY_BACKUP_SUBTREE_FQN.size();
   private static final int BUDDY_BACKUP_ROOT_OWNER_SIZE = BUDDY_BACKUP_ROOT_OWNER_INDEX + 1;
   // Element within an FQN that is the root of a Pojo attribute map
   private static final int POJO_ATTRIBUTE_FQN_INDEX = SESSION_ID_FQN_INDEX + 1;
   // Element within an FQN that is the root of an individual Pojo attribute
   private static final int POJO_KEY_FQN_INDEX = POJO_ATTRIBUTE_FQN_INDEX + 1;
   // Element within an FQN that is the root of a session's internal pojo storage area
   private static final int POJO_INTERNAL_FQN_INDEX = SESSION_ID_FQN_INDEX + 1;
   // Minimum size of an FQN that is below the root of a session's internal pojo storage area
   private static final int POJO_INTERNAL_FQN_SIZE = POJO_INTERNAL_FQN_INDEX + 1;
   
   public static boolean isBuddyFqn(Fqn<String> fqn)
   {
      try
      {
         return BuddyManager.BUDDY_BACKUP_SUBTREE.equals(fqn.get(0));
      }
      catch (IndexOutOfBoundsException e)
      {
         // Can only happen if fqn is ROOT, and we shouldn't get
         // notifications for ROOT.
         // If it does, just means it's not a buddy
         return false;
      }      
   }

   public static boolean isFqnSessionRootSized(Fqn<String> fqn, boolean isBuddy)
   {
      return fqn.size() == (isBuddy ? BUDDY_BACKUP_ROOT_OWNER_SIZE + SESSION_FQN_SIZE : SESSION_FQN_SIZE);
   }
   
   public static String getPojoKeyFromFqn(Fqn<String> fqn, boolean isBuddy)
   {
      return (String) fqn.get(isBuddy ? BUDDY_BACKUP_ROOT_OWNER_SIZE + POJO_KEY_FQN_INDEX: POJO_KEY_FQN_INDEX);
   }
   
   /**
    * Check if the fqn is big enough to be in the internal pojo area but
    * isn't in the regular attribute area.
    * 
    * Structure in the cache is:
    * 
    * /JSESSION
    * ++ /contextPath_hostname
    * ++++ /sessionid
    * ++++++ /ATTRIBUTE
    * ++++++ /_JBossInternal_
    * ++++++++ etc etc
    * 
    * If the Fqn size is big enough to be "etc etc" or lower, but the 4th
    * level is not "ATTRIBUTE", it must be under _JBossInternal_. We discriminate
    * based on != ATTRIBUTE to avoid having to code to the internal PojoCache
    * _JBossInternal_ name.
    * 
    * @param fqn
    * @return
    */
   public static boolean isPossibleInternalPojoFqn(Fqn<String> fqn)
   {      
      return (fqn.size() > POJO_INTERNAL_FQN_SIZE 
            && ATTRIBUTE.equals(fqn.get(POJO_INTERNAL_FQN_INDEX)) == false);
   }
   
   public static String getIdFromFqn(Fqn<String> fqn, boolean isBuddy)
   {
      return (String)fqn.get(isBuddy ? BUDDY_BACKUP_ROOT_OWNER_SIZE + SESSION_ID_FQN_INDEX : SESSION_ID_FQN_INDEX);
   }

   /**
    * Extracts the owner portion of an buddy subtree Fqn.
    * 
    * @param fqn An Fqn that is a child of the buddy backup root node.
    */
   public static String getBuddyOwner(Fqn<String> fqn)
   {
      return (String) fqn.get(BUDDY_BACKUP_ROOT_OWNER_INDEX);     
   }

   public static boolean isFqnForOurWebapp(Fqn<String> fqn, String contextHostPath, boolean isBuddy)
   {
      try
      {
         if (contextHostPath.equals(fqn.get(isBuddy ? BUDDY_BACKUP_ROOT_OWNER_SIZE + WEBAPP_FQN_INDEX : WEBAPP_FQN_INDEX))
               && JSESSION.equals(fqn.get(isBuddy ? BUDDY_BACKUP_ROOT_OWNER_SIZE + JSESSION_FQN_INDEX : JSESSION_FQN_INDEX)))
            return true;
      }
      catch (IndexOutOfBoundsException e)
      {
         // can't be ours; too small; just fall through
      }
   
      return false;
   }

   private FqnUtil() {}
}
