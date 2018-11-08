/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2000 - 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.cache.integration;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.ConfigurationRegistry;
import org.jboss.cache.config.parsing.CacheConfigsXmlParser;
import org.w3c.dom.Element;

/**
 * XmlParsingConfigurationRegistry variant that replaces any MuxStackName in
 * configurations with a corresponding Element parsed out of the provided
 * JGroups stacks.xml file. UnitTestCacheFactory can then mangle the element.
 *
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7168 $
 */
public class UnitTestCacheFactoryConfigurationRegistry implements ConfigurationRegistry
{
   public static final String DEFAULT_STACKS_XML_RESOURCE = "configs/integration/jgroups-channelfactory-stacks.xml";
   
   private final CacheConfigsXmlParser parser;
   private final String configResource;
   private final Map<String, Element> stacks;
   private final Map<String, Configuration> configs = new Hashtable<String, Configuration>();
   private boolean started;

   public UnitTestCacheFactoryConfigurationRegistry(String cacheConfigResource) 
   {
      this(cacheConfigResource, DEFAULT_STACKS_XML_RESOURCE);
   }
   
   public UnitTestCacheFactoryConfigurationRegistry(String cacheConfigResource, String stacksXmlResource) 
   {
      parser = new CacheConfigsXmlParser();
      this.configResource = cacheConfigResource;
      try
      {
         this.stacks = JGroupsUtil.getStackConfigs(stacksXmlResource);
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RuntimeException("problem parsing JGroups stacks", e);
      }
   }

   public void start() throws Exception
   {
      if (!started)
      {
         if (configResource != null)
         {
            Map<String, Configuration> parsed = parser.parseConfigs(configResource);
            for (Map.Entry<String, Configuration> entry : parsed.entrySet())
            {
               registerConfiguration(entry.getKey(), entry.getValue());
            }
         }
         started = true;
      }
   }

   public void stop()
   {
      if (started)
      {
         synchronized (configs)
         {
            configs.clear();
         }
         started = false;
      }
   }

   public String getConfigResource()
   {
      return configResource;
   }

   public Set<String> getConfigurationNames()
   {
      return new HashSet<String>(configs.keySet());
   }

   public void registerConfiguration(String configName, Configuration config)
         throws CloneNotSupportedException
   {
      synchronized (configs)
      {
         if (configs.containsKey(configName))
            throw new IllegalStateException(configName + " already registered");
         Configuration clone = config.clone();
         fixJGroupsConfig(clone);
         configs.put(configName, clone);
      }
   }

   public void unregisterConfiguration(String configName)
   {
      synchronized (configs)
      {
         if (configs.remove(configName) == null)
            throw new IllegalStateException(configName + " not registered");
      }
   }

   public Configuration getConfiguration(String configName)
   {
      Configuration config;
      synchronized (configs)
      {
         config = configs.get(configName);
      }

      if (config == null)
         throw new IllegalArgumentException("unknown config " + configName);

      // Don't hand out a ref to our master copy
      try
      {
         return config.clone();
      }
      catch (CloneNotSupportedException e)
      {
         // This should not happen, as we already cloned the config
         throw new RuntimeException("Could not clone configuration " + configName, e);
      }
   }

   /** Replace a stack name with a stack element that UnitTestCacheFactory can mangle */
   private void fixJGroupsConfig(Configuration clone)
   {
      String stackName = clone.getMultiplexerStack();
      if (stackName != null)
      {
         clone.setMultiplexerStack(null);
         Element e = stacks.get(stackName);
         if (e == null)
         {
            throw new IllegalStateException("unknown stack " + stackName);            
         }
         
         clone.setClusterConfig(e);         
      }
      
   }
   
   
}
