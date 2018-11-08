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

package org.jboss.cache.integration;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.util.FileLookup;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * JGroups-related utilities.
 * 
 * @author Brian Stansberry
 */
public class JGroupsUtil
{
   private final static String PROTOCOL_STACKS = "protocol_stacks";
   private final static String STACK = "stack";
   private static final String NAME = "name";
   private static final String CONFIG = "config";

   public static Map<String, Element> getStackConfigs(String stacksXmlResource) throws Exception
   {
      FileLookup fileLookup = new FileLookup();
      InputStream is = fileLookup.lookupFile(stacksXmlResource);
      if (is == null)
      {
         throw new ConfigurationException("Unable to find config file " + stacksXmlResource + " either in classpath or on the filesystem!");
      }
      
      return getStackConfigs(is);
   }
   
   /**
    * Parses a set if "config" elements out of a JGroups stacks.xml file.
    * 
    * @param input
    * @return
    * @throws Exception
    */
   public static Map<String, Element> getStackConfigs(InputStream input) throws Exception
   {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setValidating(false); //for now
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(input);

      // The root element of the document should be the "config" element,
      // but the parser(Element) method checks this so a check is not
      // needed here.
      Element configElement = document.getDocumentElement();
      return getStackConfigs(configElement);
   }

   private static Map<String, Element> getStackConfigs(Element root) throws Exception
   {
      Map<String, Element> result = new HashMap<String, Element>();

      String root_name = root.getNodeName();
      if (!PROTOCOL_STACKS.equals(root_name.trim().toLowerCase()))
      {
         String error = "XML protocol stack configuration does not start with a '<config>' element; "
               + "maybe the XML configuration needs to be converted to the new format ?\n"
               + "use 'java org.jgroups.conf.XmlConfigurator <old XML file> -new_format' to do so";
         throw new IOException("invalid XML configuration: " + error);
      }

      NodeList tmp_stacks = root.getChildNodes();
      for (int i = 0; i < tmp_stacks.getLength(); i++)
      {
         Node node = tmp_stacks.item(i);
         if (node.getNodeType() != Node.ELEMENT_NODE)
            continue;

         Element stack = (Element) node;
         String tmp = stack.getNodeName();
         if (!STACK.equals(tmp.trim().toLowerCase()))
         {
            throw new IOException("invalid configuration: didn't find a \"" + STACK + "\" element under \""
                  + PROTOCOL_STACKS + "\"");
         }

         NamedNodeMap attrs = stack.getAttributes();
         Node name = attrs.getNamedItem(NAME);
         String st_name = name.getNodeValue();
         
         NodeList configs = stack.getChildNodes();
         for (int j = 0; j < configs.getLength(); j++)
         {
            Node tmp_config = configs.item(j);
            if (tmp_config.getNodeType() != Node.ELEMENT_NODE)
               continue;
            Element cfg = (Element) tmp_config;
            tmp = cfg.getNodeName();
            if (!CONFIG.equals(tmp))
               throw new IOException("invalid configuration: didn't find a \"" + CONFIG + "\" element under \"" + STACK
                     + "\"");

            if (!result.containsKey(st_name))
            {
               result.put(st_name, cfg);
            }
            else
            {
               throw new IllegalStateException("didn't add config '" + st_name
                     + " because one of the same name already existed");
            }
         }
      }

      return result;
   }

   /**
    * Prevent instantiation.
    */
   private JGroupsUtil()
   {
      throw new UnsupportedOperationException("just a static util class");
   }

}
