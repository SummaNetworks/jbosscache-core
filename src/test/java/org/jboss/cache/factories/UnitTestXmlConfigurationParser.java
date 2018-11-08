package org.jboss.cache.factories;

import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.util.FileLookup;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;

/**
 * The purpose of this class is to make sure that the parsing of the test files is only performed once.
 *
 * @author Mircea.Markus@jboss.com
 */
class UnitTestXmlConfigurationParser
{
   private static Log log = LogFactory.getLog(UnitTestConfigurationFactory.class);
   public static final String DEFAULT_CONFIGURATION_FILE = "unit-test-cache-service.xml";
   public static final String JGROUPS_CHANNEL;
   public static final String JGROUPS_STACK_TYPE = "jgroups.stack";

   static
   {
      JGROUPS_CHANNEL = System.getProperty(JGROUPS_STACK_TYPE);
   }


   private volatile static UnitTestXmlConfigurationParser instance;
   private volatile Configuration confCache;
   private volatile Map<String, Element> elementCache = new HashMap<String, Element>();


   public static Configuration getConfiguration(Configuration.CacheMode cacheMode, boolean useTcp)
   {
      if (instance == null)
      {
         synchronized (UnitTestXmlConfigurationParser.class)
         {
            if (instance == null)
            {
               instance = new UnitTestXmlConfigurationParser();
               instance.parseFile();
            }
         }
      }
      if (instance == null)
      {
         log.error("instance is null after creating node!!!");
         throw new IllegalStateException();
      }
      //if there is an enforced jgroups stack, then only consider that one
      if (JGROUPS_CHANNEL != null)
      {
         useTcp = JGROUPS_CHANNEL.trim().equals("tcp");
      }
      String resultKey = useTcp ? "tcp-" : "udp-";
      resultKey += cacheMode.isSynchronous() ? "sync" : "async";
      Configuration resultConf;
      try
      {
         resultConf = instance.confCache.clone();
      } catch (CloneNotSupportedException e)
      {
         log.error("Could not clone:", e);
         throw new IllegalStateException(e);
      }
      Element stack = instance.elementCache.get(resultKey);
      if (stack == null)
      {
         log.error("stack is null!!!");
         throw new NullPointerException();
      }
      resultConf.setClusterConfig(stack);
      if (resultConf.getClusterConfig() == null)
      {
         log.error("Null cluster config");
         throw new IllegalStateException();
      }
      resultConf.setCacheMode(cacheMode);
      return resultConf;
   }

   private void parseFile()
   {
      parseStream(new FileLookup().lookupFile(DEFAULT_CONFIGURATION_FILE));
   }

   private void parseStream(InputStream stream)
   {
      try
      {
// loop through all elements in XML.
         if (stream == null) throw new ConfigurationException("Input stream for configuration xml is null!");

         Element root = XmlConfigHelper.getDocumentRoot(stream);
         XmlConfigurationParser parser = new XmlConfigurationParser();
         confCache = parser.parseElement(root);
         if (confCache == null) throw new NullPointerException("Null conf cache!!");

         Element list = (Element) root.getElementsByTagNameNS("*", "protocol_stacks").item(0);
         NodeList stacks = list.getElementsByTagNameNS("*", "stack");

         for (int i = 0; i < stacks.getLength(); i++)
         {
            Element stack = (Element) stacks.item(i);
            String stackName = stack.getAttribute("name");
            Element jgroupsStack = (Element) stack.getElementsByTagNameNS("*", "config").item(0);
            elementCache.put(stackName, jgroupsStack);
         }
      }
      catch (Exception e)
      {
         log.error(e);
         throw new IllegalStateException(e);
      }
   }
}
