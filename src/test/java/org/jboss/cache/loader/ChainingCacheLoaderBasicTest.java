/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

import java.io.File;

/**
 * Tests basic functionality of a chaining cache loader with 2 different loaders
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, testName = "loader.ChainingCacheLoaderBasicTest")
public class ChainingCacheLoaderBasicTest extends CacheLoaderTestsBase
{
   private static final String loc1 = "JBossCache-ChainingCacheLoaderBasicTest-1";
   private static final String loc2 = "JBossCache-ChainingCacheLoaderBasicTest-2";

   public ChainingCacheLoaderBasicTest()
   {
      File dir1 = new File(loc1);
      File dir2 = new File(loc2);

      if (!dir1.exists()) dir1.mkdirs();
      if (!dir2.exists()) dir2.mkdirs();
   }

   protected void configureCache(CacheSPI cache) throws Exception
   {
      cache.getConfiguration().setCacheLoaderConfig(getCacheLoaderConfig(loc1, loc2));
      TestingUtil.recursiveFileRemove(loc1);
      TestingUtil.recursiveFileRemove(loc2);

   }

   protected CacheLoaderConfig getCacheLoaderConfig(String loc1, String loc2) throws Exception
   {
      CacheLoaderConfig clc = new CacheLoaderConfig();
      // clc 1
      IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
      iclc.setClassName(DummySharedInMemoryCacheLoader.class.getName());
      iclc.setAsync(false);
      iclc.setFetchPersistentState(true);
      iclc.setPurgeOnStartup(false);
      iclc.setIgnoreModifications(false);
      iclc.setProperties("bin=" + loc1);
      clc.addIndividualCacheLoaderConfig(iclc);

      IndividualCacheLoaderConfig iclc2 = iclc.clone();
      iclc2.setFetchPersistentState(false);
      iclc2.setProperties("bin=" + loc2);
      clc.addIndividualCacheLoaderConfig(iclc2);
      clc.setPassivation(false);
      clc.setShared(false);
      return clc;
   }
}
