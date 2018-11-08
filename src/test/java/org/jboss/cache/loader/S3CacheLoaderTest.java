package org.jboss.cache.loader;

import static org.testng.AssertJUnit.assertNotNull;

import java.util.Properties;

import net.noderunner.amazon.s3.emulator.Server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.config.CacheLoaderConfig;
import org.testng.annotations.Test;
import org.testng.annotations.AfterClass;


/**
 * Tests {@link org.jboss.cache.loader.s3.S3CacheLoader}.
 * <p/>
 * This requires a S3 account to truly test; uses an emulator otherwise.
 *
 * @author Elias Ross
 * @version $Id: S3CacheLoaderTest.java 7422 2009-01-09 09:21:18Z mircea.markus $
 */
@Test(groups = {"functional"}, enabled = true, testName = "loader.S3CacheLoaderTest")
public class S3CacheLoaderTest extends CacheLoaderTestsBase
{

   private static final Log log = LogFactory.getLog(S3CacheLoaderTest.class);

   Server server;

   @Override
   protected void configureCache(CacheSPI cache) throws Exception
   {
      if (server == null)
      {
         server = new Server();
         server.start();

      }
      String accessKey = System.getProperty("accessKey");
      String properties;
      if (accessKey == null)
      {
         log.info("Testing using S3CacheLoader using emulator");
         properties =
               "cache.s3.accessKeyId=dummy\n" +
                     "cache.s3.secretAccessKey=dummy\n" +
                     "cache.s3.server=localhost\n" +
                     "cache.s3.port=" + server.getPort() + "\n" +
                     "cache.s3.callingFormat=VANITY" + "\n" +
                     "cache.s3.bucket=localhost" + "\n";
      } else
      {
         properties =
               "cache.s3.accessKeyId=" + accessKey + "\n" +
                     "cache.s3.secretAccessKey=" + System.getProperty("secretKey") + "\n";
      }
      CacheLoaderConfig config = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.s3.S3CacheLoader",
            properties, false, true, false, false, false);
      Properties p = config.getFirstCacheLoaderConfig().getProperties();
      assertNotNull(p.get("cache.s3.accessKeyId"));
      assertNotNull(p.get("cache.s3.secretAccessKey"));
      cache.getConfiguration().setCacheLoaderConfig(config);
   }

   @AfterClass
   public void closeServerConnection() throws Exception 
   {
      server.close();
   }

   protected void postConfigure()
   {
      cache.removeNode(Fqn.root());
   }

   //@Override
   public void testCacheLoaderThreadSafety()
   {

   }

   //@Override
   public void testPartialLoadAndStore()
   {
      // do nothing
   }

   //@Override
   public void testBuddyBackupStore()
   {
      // do nothing
   }

   //@Override
   protected void threadSafetyTest(final boolean singleFqn) throws Exception
   {
   }

   public void testIgnoreModifications() throws Exception
   {
      //do nothing
   }
}
