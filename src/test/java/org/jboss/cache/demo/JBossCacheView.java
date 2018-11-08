/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 * Created on March 25 2003
 */
package org.jboss.cache.demo;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.CacheFactory;
import org.jboss.cache.DefaultCacheFactory;


/**
 * Graphical view of a JBoss Cache instance.  Think of it as a view in an MVC model.  An instance of this view
 * needs to be given a reference to the  model ({@link CacheModelDelegate}) and needs to registers as a
 * {@link org.jboss.cache.CacheListener}. Changes to the cache structure are propagated from the model to the
 * view (via the CacheListener interface), changes from the GUI (e.g. by a user) are executed on the cache model
 * which will delegate on the actual Cache instance (which, if clustered, will broadcast the changes to all replicas).
 * <p/>
 * The view itself maintains references to the nodes, but doesn't cache any of the data associated with the nodes. When
 * data needs to be displayed, the underlying cache will be accessed directly.
 *
 * @author Manik Surtani
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 * @version $Revision: 7168 $
 */
public class JBossCacheView
{
   private static Log log = LogFactory.getLog(JBossCacheView.class.getName());

   /**
    * A reference to the Swing GUI that displays contents to the user.
    */
   private JBossCacheGUI gui = null;

   /**
    * Whether or not to use the embedded BeanShell console.
    */
   private boolean useConsole = false;

   /**
    * Cache configuration file.
    */
   private String configurationFile = null;

   /**
    * The cache model.
    */
   private CacheModelDelegate cacheModelDelegate;

   /**
    * Gets the configuration file
    *
    * @return String containing the path to the configuration file
    */
   public String getConfigurationFile()
   {
      return configurationFile;
   }

   /**
    * Sets a reference to the cache model
    *
    * @param cacheModelDelegate cache model instance to associate with this view
    */
   public void setCacheModelDelegate(CacheModelDelegate cacheModelDelegate)
   {
      this.cacheModelDelegate = cacheModelDelegate;
      if (gui != null) gui.setCacheModelDelegate(cacheModelDelegate);
   }

   /**
    * Main code for the view
    *
    * @param args arguments passed via command line
    * @throws Exception if there's any issues starting the cache demo
    */
   public void doMain(String[] args) throws Exception
   {
      parseParameters(args);

      if (configurationFile == null)
      {
         help();
         throw new Exception("Configuration file cannot be null, please specify with the -config parameter when starting!");
      }

      CacheModelDelegate cacheModelDelegate = createCacheDelegate();
      cacheModelDelegate.getGenericCache().start();
      setCacheModelDelegate(cacheModelDelegate);
      start();
   }

   /**
    * Starts the view
    *
    * @throws Exception
    */
   public void start() throws Exception
   {
      if (gui == null)
      {
         log.info("start(): creating the GUI");
         System.out.println("start(): creating the GUI");
         gui = createGUI(cacheModelDelegate, useConsole);
      }
   }

   /**
    * Stops the view
    */
   public void stop()
   {
      if (gui != null)
      {
         log.info("stop(): disposing the GUI");
         gui.stopGui();
         gui = null;
      }
   }

   /**
    * Starts the view
    *
    * @param args valid arguments are: -console to enable using the embedded beanShell console, -config [path/to/config/file] to specify a config file.
    */
   public static void main(String args[])
   {
      try
      {
         JBossCacheView view = new JBossCacheView();
         view.doMain(args);
      }
      catch (Exception ex)
      {
         log.error("Cannot start up!!", ex);
      }
   }

   /**
    * Parses the parameters
    *
    * @param args arguments passed via command line
    */
   protected void parseParameters(String[] args)
   {
      for (int i = 0; i < args.length; i++)
      {
         if (args[i].equals("-console"))
         {
            useConsole = true;
            continue;
         }
         if (args[i].equals("-config"))
         {
            configurationFile = args[++i];
            continue;
         }
         help();
         return;
      }
   }

   /**
    * Factory method that creates the cache model delegate instance for this demo
    *
    * @return instance of CacheModelDelegate
    * @throws Exception
    */
   protected CacheModelDelegate createCacheDelegate() throws Exception
   {
      CacheFactory<String, String> factory = new DefaultCacheFactory<String, String>();
      Cache<String, String> cache = factory.createCache(configurationFile, false);
      CacheModelDelegate delegate = new JBossCacheModelDelegate();
      delegate.setCacheShellVariable(cache);

      return delegate;
   }

   protected JBossCacheGUI createGUI(CacheModelDelegate delegate, boolean useConsole) throws Exception
   {
      return new JBossCacheGUI(delegate, useConsole);
   }

   private static void help()
   {
      System.out.println("JBossCacheView [-help] " +
            "[-console] " +
            "[-config <path to configuration file to use>]");

      System.out.println();
      System.out.println("-console enables the embedded BeanShell console");
      System.out.println("-config allows you to provide a path to the configuration file to use.");
      System.out.println();
      System.out.println();
   }
}












