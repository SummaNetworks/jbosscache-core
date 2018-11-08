/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.cache.multiplexer;

import org.jboss.cache.Cache;
import org.jgroups.ChannelFactory;
import org.jgroups.JChannel;
import org.jgroups.JChannelFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.factories.UnitTestConfigurationFactory;

/**
 * Utility class that can associate a cache with a multiplexer-enabled
 * JGroups ChannelFactory.
 *
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7422 $
 */
public class MultiplexerTestHelper
{
   public static final String MUX_STACK = "jbc-test";

   private final Set<JChannelFactory> factories = Collections.synchronizedSet(new HashSet<JChannelFactory>());
   private final Set<Cache> caches = Collections.synchronizedSet(new HashSet<Cache>());

   /**
    * Configures the given cache to get its JChannel from a
    * multiplexer-enabled JChannelFactory.  The JChannelFactory will
    * produce MuxChannels configured with the same protocol stack as
    * whatever the provided cache is configured with.
    *
    * @param cache the cache
    * @throws Exception
    */
   public void configureCacheForMux(Cache cache) throws Exception
   {
      synchronized (caches)
      {
         ChannelFactory factory = createMuxChannelFactory(cache);
         cache.getConfiguration().getRuntimeConfig().setMuxChannelFactory(factory);
         cache.getConfiguration().setMultiplexerStack(MUX_STACK + Thread.currentThread().getName());
      }
   }

   /**
    * Creates a JChannelFactory.  The JChannelFactory will
    * produce MuxChannels configured with the same protocol stack as
    * whatever the provided cache is configured with.
    *
    * @param cache the cache from which the protocol stack config should
    *              be obtained
    * @return the channel factory.
    * @throws Exception
    */
   public ChannelFactory createMuxChannelFactory(Cache cache) throws Exception
   {
      return createMuxChannelFactory(getChannelProperties(cache));
   }

   private String getChannelProperties(Cache cache)
   {
      String props = cache.getConfiguration().getClusterConfig();
      return (props == null ? UnitTestConfigurationFactory.getEmptyConfiguration().getClusterConfig() : props);
   }

   /**
    * Creates a JChannelFactory.  The JChannelFactory will
    * produce MuxChannels configured according to the given
    * protocol stack(s).
    *
    * @param muxConfig Element that looks like the root element
    *                  of a multiplexer stacks.xml file.
    * @return the channel factory.
    * @throws Exception
    */
   public ChannelFactory createMuxChannelFactory(String muxConfig) throws Exception
   {
      synchronized (factories)
      {
         muxConfig = new UnitTestCacheFactory().mangleClusterConfiguration(muxConfig);
         JChannelFactory factory = new JChannelFactory();
         factory.setDomain("jbc.mux.test");
         factory.setExposeChannels(false);
         factory.setMultiplexerConfig(getClusterConfigElement(muxConfig));

         factories.add(factory);

         return factory;
      }
   }

   /**
    * Converts an old-style JGroups protocol stack config string to an Element
    * that looks like the root element of a multiplexer stacks.xml file.
    *
    * @param clusterConfig
    * @return
    * @throws Exception
    */
   public static Element getClusterConfigElement(String clusterConfig) throws Exception
   {
      clusterConfig = clusterConfig.trim();
      DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document doc = db.newDocument();
      Element top = doc.createElement("protocol_stacks");
      doc.appendChild(top);
      Element stack = doc.createElement("stack");
      stack.setAttribute("name", MUX_STACK + Thread.currentThread().getName());
      top.appendChild(stack);
      Element config = doc.createElement("config");

      StringTokenizer outer = new StringTokenizer(clusterConfig, ":");
      while (outer.hasMoreTokens())
      {
         String protocol = outer.nextToken();
         String protName = protocol;
         String attribs = null;
         int nameEnd = protocol.indexOf('(');
         if (nameEnd > 0)
         {
            protName = protocol.substring(0, nameEnd);
            attribs = protocol.substring(nameEnd + 1, protocol.length() - 1);
         }
         Element element = doc.createElement(protName);
         if (attribs != null && attribs.length() > 0)
         {
            StringTokenizer inner = new StringTokenizer(attribs, ";");
            while (inner.hasMoreTokens())
            {
               String attrib = inner.nextToken();
               int eq = attrib.indexOf('=');
               String name = attrib.substring(0, eq);
               String value = attrib.substring(eq + 1);
               element.setAttribute(name, value);
            }
         }
         config.appendChild(element);
      }

      stack.appendChild(config);
      return top;
   }

   /**
    * Performs cleanup work.  Once this method is invoked, this
    * object should no longer be used.
    */
   public void tearDown()
   {
      factories.clear();
      caches.clear();
   }

   /**
    * Tests creation of a channel factory.
    *
    * @param args
    */
   public static void main(String[] args)
   {
      MultiplexerTestHelper helper = new MultiplexerTestHelper();
      try
      {
         helper.createMuxChannelFactory(JChannel.DEFAULT_PROTOCOL_STACK);
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
      finally
      {
         helper.tearDown();
      }
   }
}
