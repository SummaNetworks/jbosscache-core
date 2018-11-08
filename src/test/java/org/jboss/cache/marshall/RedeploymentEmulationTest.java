/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.marshall;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.config.Configuration;
import org.jgroups.Global;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;

/**
 * Unit test demonstrating usability of marshalling for application redeployment in application server.
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
@Test(groups = {"functional"}, enabled = false, sequential = true, testName = "marshall.RedeploymentEmulationTest")
// this relies on an old project structure that no longer exists
public class RedeploymentEmulationTest
{
   private Cache<Object, Object> cache;

   private static final String INSTANCE_LIBRARY = "jgroups-all.jar";
   private static final String INSTANCE_CLASS_NAME = "org.jgroups.Global";
   private static final String USER_DIR = ".";//System.getProperty("user.dir");
   private static final String FILE_SEPARATOR = File.separator;//System.getProperty("file.separator");
   private static final String LIB_DIR_NAME = "lib";
   private static final String LIB_DIR = USER_DIR + FILE_SEPARATOR + LIB_DIR_NAME + FILE_SEPARATOR;
   private static final String LIB_DIR_SP = System.getProperty("lib.dir");//"lib";
   private static final Log log = LogFactory.getLog(RedeploymentEmulationTest.class);

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());

      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setUseRegionBasedMarshalling(true);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      log.info("**** IN TEAR DOWN ***");
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testClassCastException() throws Exception
   {
      cache.start();

      URLClassLoader ucl1 = createOrphanClassLoader();
      Thread.currentThread().setContextClassLoader(ucl1);

      Class clazz1 = ucl1.loadClass(INSTANCE_CLASS_NAME);
      cache.put(fqn("/a"), "key", clazz1.newInstance());

      Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
      try
      {
         @SuppressWarnings("unused")
         Global object = (Global) cache.get(fqn("/a"), "key");
         fail("Should have produced a ClassCastException");
      }
      catch (ClassCastException cce)
      {
         assertTrue(cce.getMessage().startsWith(INSTANCE_CLASS_NAME));
      }
   }

   public void testRegisterUnregister() throws Exception
   {
      cache.start();

      URLClassLoader ucl1 = createOrphanClassLoader();
      Thread.currentThread().setContextClassLoader(ucl1);

      Region region = cache.getRegion(fqn("/"), true);
      region.registerContextClassLoader(Thread.currentThread().getContextClassLoader());
      region.activate();

      Class clazz1 = ucl1.loadClass(INSTANCE_CLASS_NAME);
      cache.put(fqn("/a"), "key", clazz1.newInstance());

      region.deactivate();
      region.unregisterContextClassLoader();

      Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());

      region.registerContextClassLoader(Thread.currentThread().getContextClassLoader());

      try
      {
         Global object = (Global) cache.get(fqn("/a"), "key");
         assertNull(object);
      }
      catch (ClassCastException cce)
      {
//         cce.printStackTrace();
         fail("Should not have produced a ClassCastException");
      }

      region.deactivate();
      region.unregisterContextClassLoader();
   }

   @SuppressWarnings("deprecation")
   private URLClassLoader createOrphanClassLoader() throws MalformedURLException
   {
      File f;
      if (LIB_DIR_SP == null)
      {
         /* lib.dir system property is null, so we assume this test is being run
         * inside an IDE, where the user dir is the root of JBossCache. We know
         * JGroups lib is located in lib/jgroups.jar */
         f = new File(USER_DIR + FILE_SEPARATOR + LIB_DIR + FILE_SEPARATOR);
      }
      else
      {
         /* lib.dir is set, so we assume that you are running from the build.xml
         * which means that the user dir might be a completely different one. lib.dir
         * system property allows us to know where the lib directory is independently
         * of the user dir*/
         f = new File(LIB_DIR_SP);
      }

      URL context = f.toURL();
      URL jar = new URL(context, INSTANCE_LIBRARY);
      URLClassLoader ucl1 = new URLClassLoader(new URL[]{jar}, null);

      return ucl1;
   }

   private static Fqn fqn(String fqn)
   {
      return Fqn.fromString(fqn);
   }
}
