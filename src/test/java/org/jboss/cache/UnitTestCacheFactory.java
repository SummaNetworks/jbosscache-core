/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.config.parsing.XmlConfigurationParser2x;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.jgroups.stack.GossipRouter;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:dpospisi@redhat.com">Dominik Pospisil (dpospisi@redhat.com)</a>
 */
public class UnitTestCacheFactory<K, V>
{

   public UnitTestCacheFactory()
   {
   }

   private final Log log = LogFactory.getLog(UnitTestCacheFactory.class);

   public static AtomicInteger replCacheCount = new AtomicInteger(0);
   public static AtomicInteger localCacheCount = new AtomicInteger(0);

   /**
    * Holds unique mcast_addr for each thread used for JGroups channel construction.
    */
   private static final ThreadLocal<String> threadMcastIP = new ThreadLocal<String>()
   {
      private final AtomicInteger uniqueAddr = new AtomicInteger(11);

      @Override
      protected String initialValue()
      {
         return "228.10.10." + uniqueAddr.getAndIncrement();
      }
   };

   private static final ThreadLocal<String> threadTcpStartPort = new ThreadLocal<String>()
   {
      private final AtomicInteger uniqueAddr = new AtomicInteger(7900);

      @Override
      protected String initialValue()
      {
         return uniqueAddr.getAndAdd(50) + "";
      }
   };

   /**
    * Holds unique mcast_port for each thread used for JGroups channel construction.
    */
   private static final ThreadLocal<Integer> threadMcastPort = new ThreadLocal<Integer>()
   {
      private final AtomicInteger uniquePort = new AtomicInteger(45589);

      @Override
      protected Integer initialValue()
      {
         return uniquePort.getAndIncrement();
      }
   };

   /**
    * For each thread holds list of caches created using this factory.
    */
   private static final ThreadLocal<List<Cache>> threadCaches =
         new ThreadLocal<List<Cache>>()
         {
            @Override
            protected List<Cache> initialValue()
            {
               return new ArrayList<Cache>();
            }
         };

   private final static List<Cache> allCaches = new ArrayList<Cache>();

   /**
    * For each thread holds the name of the test class which executed createCache factory method.
    */
   private static final ThreadLocal<Class> threadTestName = new ThreadLocal<Class>();

   // factory methods

   public Cache<K, V> createCache(Class ownerClass) throws ConfigurationException
   {
      return createCache(true, ownerClass);
   }

   public Cache<K, V> createCache(boolean start, Class ownerClass) throws ConfigurationException
   {
      Configuration config = UnitTestConfigurationFactory.getEmptyConfiguration();
      return createCache(config, start, ownerClass);
   }

   public Cache<K, V> createCache(Configuration.CacheMode mode, Class ownerClass) throws ConfigurationException
   {
      Configuration config = UnitTestConfigurationFactory.getEmptyConfiguration();
      config.setCacheMode(mode);
      return createCache(config, ownerClass);
   }

   public Cache<K, V> createCache(String configFileName, Class ownerClass) throws ConfigurationException
   {
      return createCache(configFileName, true, ownerClass);
   }

   public Cache<K, V> createCache(String configFileName, boolean start, Class ownerClass) throws ConfigurationException
   {
      Configuration c = getConfigurationFromFile(configFileName);
      c.setClusterConfig(UnitTestConfigurationFactory.getEmptyConfiguration().getClusterConfig());
      return createCache(c, start, ownerClass);
   }

   public Configuration getConfigurationFromFile(String configFileName)
   {
      XmlConfigurationParser parser = new XmlConfigurationParser();
      Configuration c;
      try
      {
         c = parser.parseFile(configFileName);
      }
      catch (ConfigurationException e)
      {
         XmlConfigurationParser2x oldParser = new XmlConfigurationParser2x();
         c = oldParser.parseFile(configFileName);
      }
      return c;
   }

   public Cache<K, V> createCache(Configuration configuration, Class ownerClass) throws ConfigurationException
   {
      return createCache(configuration, true, ownerClass);
   }

   public Cache<K, V> createCache(InputStream is, Class ownerClass) throws ConfigurationException
   {
      return createCache(is, true, ownerClass);
   }

   public Cache<K, V> createCache(InputStream is, boolean start, Class ownerClass) throws ConfigurationException
   {
      XmlConfigurationParser parser = new XmlConfigurationParser();
      Configuration c = parser.parseStream(is);
      return createCache(c, start, ownerClass);
   }

   public Cache<K, V> createCache(Configuration configuration, boolean start, Class ownerClass) throws ConfigurationException
   {
      checkCaches(ownerClass);
//      tryMCastAddress();

      switch (configuration.getCacheMode())
      {
         case LOCAL:
            // local cache, no channel used
            localCacheCount.incrementAndGet();
            break;
         case REPL_SYNC:
         case REPL_ASYNC:
         case INVALIDATION_ASYNC:
         case INVALIDATION_SYNC:
            // replicated cache, update channel setup
            mangleConfiguration(configuration);
            replCacheCount.incrementAndGet();
            break;
         default:
            log.info("Unknown cache mode!");
            throw new IllegalStateException("Unknown cache mode!");
      }

      Cache<K, V> cache = new DefaultCacheFactory<K, V>().createCache(configuration, start);

      List<Cache> caches = threadCaches.get();
      caches.add(cache);

      synchronized (allCaches)
      {
         allCaches.add(cache);
      }
      return cache;

   }

   private void tryMCastAddress()
   {
      String useIpV4 = System.getProperty("java.net.preferIPv4Stack");
      log.info("++++++++++++++++++++++++++++++ useIpV4 property=" + useIpV4);
      SocketAddress socketAddress = new InetSocketAddress("224.10.10.10", 45588);
      try
      {
         MulticastSocket ms = new MulticastSocket(socketAddress);
      }
      catch (IOException e)
      {
         log.info("+++++++++++++++++++++++++++ Error : " + e.getMessage(), e);
      }
   }

   /**
    * Destroys all caches created by this factory in the current thread.
    *
    * @return true if some cleanup was actually performed
    */
   public boolean cleanUp()
   {
      List<Cache> caches = new ArrayList<Cache>(threadCaches.get());
      boolean ret = false;

      for (Cache cache : caches)
      {
         TestingUtil.killCaches(cache);
         ret = true;
      }
      return ret;
   }

   public void removeCache(Cache c)
   {
      List<Cache> caches = threadCaches.get();
      synchronized (allCaches)
      {
         if (caches.contains(c))
         {
            caches.remove(c);
            allCaches.remove(c);
         }
         else if (allCaches.contains(c))
         {
            System.err.println("[" + Thread.currentThread().getName() + "] WARNING! Remove cache called from different thread.");
            Thread.dumpStack();
         }
      }
   }

   /**
    * Updates cluster configuration to ensure mutual thread isolation.
    *
    * @param configuration Configuration to update.
    */
   public void mangleConfiguration(Configuration configuration)
   {
      String clusterConfig = configuration.getClusterConfig();
      clusterConfig = mangleClusterConfiguration(clusterConfig);
      configuration.setClusterConfig(clusterConfig);
      // Check if the cluster name contains thread id. If not, append.
      // We can not just append the threadId, since some of the tests are crating instances
      // using configurations derived from configurations returned by this factory.

      String clusterName = configuration.getClusterName();

      // append thread id
      if (clusterName.indexOf(Thread.currentThread().getName()) == -1)
      {
         clusterName = clusterName + "-" + Thread.currentThread().getName();
      }
      configuration.setClusterName(clusterName);

   }

   public static String mangleClusterConfiguration(String clusterConfig)
   {
      if (clusterConfig == null)
      {
         if (UnitTestConfigurationFactory.JGROUPS_CHANNEL.equals("udp"))
         {
            return mangleClusterConfigurationUdp(null);
         }
         else
         {
            return mangleClusterConfigurationTcp(null);
         }
      }
      if (clusterConfig.contains("UDP("))
      {
         clusterConfig = mangleClusterConfigurationUdp(clusterConfig);
      }
      else
      {
         clusterConfig = mangleClusterConfigurationTcp(clusterConfig);
      }
      return clusterConfig;
   }

   /**
    * Updates cluster configuration to ensure mutual thread isolation.
    */
   public static String mangleClusterConfigurationUdp(String clusterConfig)
   {
      if (clusterConfig == null)
      {
         // No explicit cluster configuration found. we need to resolve the default config
         // now in orded to be able to update it before the cache (and the channel) starts.

         clusterConfig = UnitTestConfigurationFactory.getEmptyConfiguration().getClusterConfig();

      }

      // replace mcast_addr
      Pattern pattern = Pattern.compile("mcast_addr=[^;]*");
      Matcher m = pattern.matcher(clusterConfig);
      if (m.find())
      {
         String newAddr = threadMcastIP.get();
         clusterConfig = m.replaceFirst("mcast_addr=" + newAddr);
      }
      else
      {
         Thread.dumpStack();
         throw new IllegalStateException();
      }

      // replace mcast_port
      pattern = Pattern.compile("mcast_port=[^;]*");
      m = pattern.matcher(clusterConfig);
      if (m.find())
      {
//         String origPort = m.group().substring(m.group().indexOf("=") + 1);
         String newPort = threadMcastPort.get().toString();
         clusterConfig = m.replaceFirst("mcast_port=" + newPort);
      }

      return clusterConfig;
   }

   /**
    * Updates cluster configuration to ensure mutual thread isolation.
    */
   public static String mangleClusterConfigurationTcp(String clusterConfig)
   {
      if (clusterConfig == null)
      {
         clusterConfig = UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC).getClusterConfig();
      }

      // replace mcast_addr
      Pattern pattern = Pattern.compile("start_port=[^;]*");
      Matcher m = pattern.matcher(clusterConfig);
      String newStartPort;
      if (m.find())
      {
         newStartPort = threadTcpStartPort.get();
         clusterConfig = m.replaceFirst("start_port=" + newStartPort);
      }
      else
      {
         System.out.println("Config is:" + clusterConfig);
         Thread.dumpStack();
         throw new IllegalStateException();
      }

      if (clusterConfig.indexOf("TCPGOSSIP") < 0) //onluy adjust for TCPPING 
      {
         // replace mcast_port
         pattern = Pattern.compile("initial_hosts=[^;]*");
         m = pattern.matcher(clusterConfig);
         if (m.find())
         {
            clusterConfig = m.replaceFirst("initial_hosts=" + "127.0.0.1[" + newStartPort + "]");
         }
      }

      return clusterConfig;
   }

//   private String getThreadId()
//   {
//      return "[" + Thread.currentThread().getName() + "]";
//   }

   private void checkCaches(Class ownerClass)
   {
      Class lastTestClass = threadTestName.get();

      if ((lastTestClass != null) && (!lastTestClass.equals(ownerClass)))
      {

         String threadId = "[" + Thread.currentThread().getName() + "] ";

         // we are running new test class
         // check if there is a cache(s) instance left & kill it if possitive

         if (cleanUp())
         {
            System.err.print(threadId + "WARNING! ");
            System.err.print(threadId + " A test method in " + lastTestClass + " did not clean all cache instances properly. ");
            System.err.println(threadId + " Use UnitTestCacheFactory.cleanUp() or TestngUtil.killCaches(...) ");
         }

      }
      threadTestName.set(ownerClass);
   }

   private String extractTestName()
   {
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      if (stack.length == 0) return null;
      for (int i = stack.length - 1; i > 0; i--)
      {
         StackTraceElement e = stack[i];
         String className = e.getClassName();
         if (className.indexOf("org.jboss.cache") != -1) return className; //+ "." + e.getMethodName();
      }
      return null;
   }


   public static void main(String[] args) throws Exception
   {
      GossipRouter router = new GossipRouter(12000, "localhost");
      router.start();

   }
}