/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.demo;

import org.jboss.cache.Cache;

/**
 * Model delegate implementation for JBossCache demo
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
public class JBossCacheModelDelegate implements CacheModelDelegate
{
   private Cache<String, String> cache;

   public void setCacheShellVariable(Object cache)
   {
      this.cache = (Cache<String, String>) cache;
   }

   public Object getCacheShellVariable()
   {
      return cache;
   }

   public Cache getGenericCache()
   {
      return cache;
   }
}
