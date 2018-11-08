/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.demo;

import org.jboss.cache.Cache;

/**
 * Delegate that hides cache model details for the demo GUI
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
public interface CacheModelDelegate
{
   /**
    * Sets the cache instance that will be used by users to interact with the real cache via the beanshell console.
    *
    * @param cache either PojoCache or Cache instance
    */
   void setCacheShellVariable(Object cache);

   /**
    * Gets the cache instance that will be used by users to interact with the real cache via the beanshell console.
    *
    * @return either PojoCache or Cache instance
    */
   Object getCacheShellVariable();

   /**
    * Gets the Cache instance that the GUI will use to populate the fiels in the GUI.
    *
    * @return returns an instance of Cache
    */
   Cache getGenericCache();
}
