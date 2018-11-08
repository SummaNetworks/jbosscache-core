/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.factories;

import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.transaction.TransactionSetup;
import org.jboss.cache.util.FileLookup;
import org.jboss.cache.UnitTestCacheFactory;
import org.jgroups.conf.XmlConfigurator;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.jgroups.conf.ConfiguratorFactory;

/**
 * Cache configuration factory used by unit tests.
 */
public class UnitTestConfigurationFactory
{
   public static final String JGROUPS_CHANNEL;
   public static final String JGROUPS_STACK_TYPE = "jgroups.stack";
   public static final String DEFAULT_CONFIGURATION_FILE = "unit-test-cache-service.xml";

   static
   {
      JGROUPS_CHANNEL = System.getProperty(JGROUPS_STACK_TYPE, "tcp");
      System.out.println("IN USE JGROUPS_CHANNEL = " + JGROUPS_CHANNEL);
   }

   public static Configuration createConfiguration(CacheMode mode) throws ConfigurationException
   {
      return createConfiguration(mode, false, false);
   }

   public static Configuration createConfiguration(CacheMode mode, boolean useEviction) throws ConfigurationException
   {
      return createConfiguration(mode, useEviction, false);
   }

   public static Configuration createConfiguration(CacheMode mode, boolean useEviction, boolean usePassivation) throws ConfigurationException
   {
      return createConfiguration(mode, useEviction, usePassivation, false);
   }

   public static Configuration createConfiguration(CacheMode mode, boolean useEviction, boolean usePassivation, boolean killable) throws ConfigurationException
   {
      UnitTestXmlConfigurationParser parser = new UnitTestXmlConfigurationParser();
      Configuration c = parser.parseFile(DEFAULT_CONFIGURATION_FILE, mode);

      if (!useEviction)
      {
         c.setEvictionConfig(null);
      }

      if (!usePassivation)
      {
         c.setCacheLoaderConfig(null);
      }

      c.setTransactionManagerLookupClass(TransactionSetup.getManagerLookup());

      if (mode != CacheMode.LOCAL && killable)
      {
         String clusterConfig = c.getClusterConfig();
         c.setClusterConfig(injectDiscard(clusterConfig, 0, 0));
      }

      return c;
   }

   public static CacheLoaderConfig buildSingleCacheLoaderConfig(boolean passivation, String preload, String cacheloaderClass,
                                                                String properties, boolean async, boolean fetchPersistentState,
                                                                boolean shared, boolean purgeOnStartup, boolean ignoreModifications) throws Exception
   {
      CacheLoaderConfig clc = new CacheLoaderConfig();
      IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
      iclc.setClassName(cacheloaderClass);
      iclc.setAsync(async);
      iclc.setFetchPersistentState(fetchPersistentState);
      iclc.setPurgeOnStartup(purgeOnStartup);
      iclc.setIgnoreModifications(ignoreModifications);
      iclc.setProperties(properties);
      clc.addIndividualCacheLoaderConfig(iclc);
      clc.setPassivation(passivation);
      clc.setShared(shared);
      clc.setPreload(preload);
      return clc;
   }
   public static CacheLoaderConfig buildSingleCacheLoaderConfig(boolean passivation, String preload, String cacheloaderClass,
                                                                Properties properties, boolean async, boolean fetchPersistentState,
                                                                boolean shared, boolean purgeOnStartup, boolean ignoreModifications) throws Exception
   {
      CacheLoaderConfig clc = new CacheLoaderConfig();
      IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
      iclc.setClassName(cacheloaderClass);
      iclc.setAsync(async);
      iclc.setFetchPersistentState(fetchPersistentState);
      iclc.setPurgeOnStartup(purgeOnStartup);
      iclc.setIgnoreModifications(ignoreModifications);
      iclc.setProperties(properties);
      clc.addIndividualCacheLoaderConfig(iclc);
      clc.setPassivation(passivation);
      clc.setShared(shared);
      clc.setPreload(preload);
      return clc;
   }

   public static CacheLoaderConfig.IndividualCacheLoaderConfig buildIndividualCacheLoaderConfig(String preload, String cacheloaderClass, String properties, boolean async, boolean fetchPersistentState, boolean purgeOnStartup, boolean ignoreModifications) throws Exception
   {
      return buildSingleCacheLoaderConfig(false, preload, cacheloaderClass, properties, async, fetchPersistentState, false, purgeOnStartup, ignoreModifications).getFirstCacheLoaderConfig();
   }

   /**
    * Helper method that takes a <b>JGroups</b> configuration file and creates an old-style JGroups config {@link String} that can be used
    * in {@link org.jboss.cache.config.Configuration#setClusterConfig(String)}.  Note that expressions
    * in the file - such as <tt>${jgroups.udp.mcast_port:45588}</tt> are expanded out accordingly.
    *
    * @param properties config properties
    * @return a String
    */
   public static String getClusterConfigFromProperties(String properties)
   {
      try
      {
         XmlConfigurator conf = XmlConfigurator.getInstance(ConfiguratorFactory.getConfigStream(properties));
         String tmp = conf.getProtocolStackString();
         // parse this string for ${} substitutions
         // Highly crappy approach!!
         tmp = tmp.replace("${jgroups.udp.mcast_addr:228.10.10.10}", "228.10.10.10");
         tmp = tmp.replace("${jgroups.udp.mcast_port:45588}", "45588");
         tmp = tmp.replace("${jgroups.udp.ip_ttl:2}", "2");
         return tmp;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Problems with properties " + properties, e);
      }
   }
   
   /**
    * Takes a JGroups configuration "old-style" String and injects the "DELAY" protcol.
    *
    * @param jgroupsConfigString JGroups config string
    * @param incomingDelay       incoming delay
    * @param outgoingDelay       outgoing delay
    * @return new string
    */
   public static String injectDelay(String jgroupsConfigString, int incomingDelay, int outgoingDelay)
   {
      String delay = ":DELAY(in_delay=" + incomingDelay + ";out_delay=" + outgoingDelay + ")";
      return jgroupsConfigString.substring(0, jgroupsConfigString.indexOf(":")) + delay + jgroupsConfigString.substring(jgroupsConfigString.indexOf(":"));
   }

   /**
    * Takes a JGroups configuration "old-style" String and injects the "DISCARD" protcol.
    *
    * @param jgroupsConfigString JGroups config string
    * @param up                  factor of incoming messages to discard. 0 is none, 1 is all.
    * @param down                factor of outgoing messages to discard. 0 is none, 1 is all.
    * @return new string
    */
   public static String injectDiscard(String jgroupsConfigString, double up, double down)
   {
      String delay = ":DISCARD(up=" + up + ";down=" + down + ")";
      return jgroupsConfigString.substring(0, jgroupsConfigString.indexOf(":")) + delay + jgroupsConfigString.substring(jgroupsConfigString.indexOf(":"));
   }

   /**
    * This will make sure that cluster config is according {@link #JGROUPS_STACK_TYPE}, even for local caches.
    * This is to avoid the following scenario: if you build a Configuration through new Configuration() then clusterCOnfig
    * is set to default value, which might be UDP.
    * 
    */
   public static Configuration getEmptyConfiguration()
   {
      Configuration tmp = createConfiguration(CacheMode.REPL_SYNC);
      Configuration conf = new Configuration();
      conf.setClusterConfig(UnitTestCacheFactory.mangleClusterConfiguration(tmp.getClusterConfig()));
      assert conf.getClusterConfig() != null;
      return conf;
   }

   private static class UnitTestXmlConfigurationParser extends XmlConfigurationParser
   {

      public Configuration parseFile(String filename, CacheMode mode)
      {
         String finalFileName = filename == null ? DEFAULT_CONFIGURATION_FILE : filename;
         return parseStream(new FileLookup().lookupFile(finalFileName), mode);
      }

      public Configuration parseStream(InputStream stream, CacheMode mode)
      {
         // loop through all elements in XML.
         if (stream == null) throw new ConfigurationException("Input stream for configuration xml is null!");

         Element root = XmlConfigHelper.getDocumentRoot(stream);
         XmlConfigurationParser parser = new UnitTestXmlConfigurationParser();
         Configuration conf = parser.parseElement(root);

         Element list = (Element) root.getElementsByTagNameNS("*","protocol_stacks").item(0);
         NodeList stacks = list.getElementsByTagNameNS("*", "stack");

         for (int i = 0; i < stacks.getLength(); i++)
         {
            Element stack = (Element) stacks.item(i);
            String stackName = stack.getAttribute("name");
            if (stackName.startsWith(JGROUPS_CHANNEL))
            {
               Element jgroupsStack = (Element) stack.getElementsByTagNameNS("*", "config").item(0);
               if (!mode.isSynchronous() && !stackName.contains("-"))
               {
                  conf.setClusterConfig(jgroupsStack);
                  conf.setCacheMode(CacheMode.REPL_ASYNC);
                  break;
               }
               else if (mode.isSynchronous() && stackName.contains("-"))
               {
                  conf.setClusterConfig(jgroupsStack);
                  conf.setCacheMode(CacheMode.REPL_SYNC);
                  break;
               }
            }
         }

         // either way, set mode in the config!!
         conf.setCacheMode(mode);
         return conf;
      }
   }
}
