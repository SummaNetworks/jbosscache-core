/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link org.jboss.cache.eviction.ExpirationAlgorithm}.
 *
 * @author Elias Ross
 * @version $Revision: 7576 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "eviction.ExpirationPolicyTest")
public class ExpirationPolicyTest extends EvictionTestsBase
{
   private static final Log log = LogFactory.getLog(ExpirationPolicyTest.class);

   private CacheSPI<Object, Object> cache;
   private EvictionController ec;

   Fqn fqn1 = Fqn.fromString("/node/1");
   Fqn fqn2 = Fqn.fromString("/node/2");
   Fqn fqn3 = Fqn.fromString("/node/3");
   Fqn fqn4 = Fqn.fromString("/node/4");

   Long future;
   Long past;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration conf = new Configuration();
      ExpirationAlgorithmConfig eAC = new ExpirationAlgorithmConfig();
      EvictionRegionConfig eRC = new EvictionRegionConfig(Fqn.ROOT, eAC);
      EvictionConfig econf = new EvictionConfig(eRC);
      econf.setWakeupInterval(0);
      conf.setEvictionConfig(econf);
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(conf, false, getClass());
      cache.start();
      ec = new EvictionController(cache);

      future = System.currentTimeMillis() + 1500;
      past = System.currentTimeMillis() - 1500;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }


   public void testEviction() throws Exception
   {
      cache.put(fqn1, ExpirationAlgorithmConfig.EXPIRATION_KEY, future);
      cache.put(fqn2, ExpirationAlgorithmConfig.EXPIRATION_KEY, past);
      cache.put(fqn3, ExpirationAlgorithmConfig.EXPIRATION_KEY, future);
      cache.put(fqn4, "foo", "bar");

      ec.startEviction();
      assertNotNull(cache.getNode(fqn1));
      assertNull(cache.getNode(fqn2));
      assertNotNull(cache.getNode(fqn3));
      assertNotNull(cache.getNode(fqn4));

      Thread.sleep(2000);
      ec.startEviction();

      log.info("should remove 1 and 3 now");
      assertNull(cache.getNode(fqn1));
      assertNull(cache.getNode(fqn3));
   }

   public void testUpdate() throws Exception
   {
      try
      {
         log.info("update 1 from future to past");
         cache.put(fqn1, ExpirationAlgorithmConfig.EXPIRATION_KEY, future);
         new EvictionController(cache).startEviction();
         assertNotNull(cache.getNode(fqn1));
         cache.put(fqn1, ExpirationAlgorithmConfig.EXPIRATION_KEY, past);
         new EvictionController(cache).startEviction();
         assertNull(cache.getNode(fqn1));
      }
      finally
      {
         cache.removeNode(Fqn.ROOT);
      }
   }
}
