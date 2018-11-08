package org.jboss.cache.statetransfer;

import org.testng.annotations.Test;

@Test(groups = {"functional"}, testName = "statetransfer.StateTransferCompatibilityTest")
public class StateTransferCompatibilityTest extends StateTransferTestBase
{
   protected String getReplicationVersion()
   {
      return "2.0.0.GA";
   }

   /**
    * These tests ensure that http://jira.jboss.com/jira/browse/JBCACHE-738
    * compatibility between non-delegating cacheloaders is maintained. In the tests
    * below first cacheloader parameter is the state producer and the second cacheloader
    * parameter is the state receiver. By having each cacheloader be a state receiver
    * and a state producer we ensure complete cacheloader compatibility.
    */
   public void testCompatibilityBetweenFileAndJbdmCacheLoaders() throws Exception
   {
      initialStateTferWithLoaderTest("org.jboss.cache.loader.FileCacheLoader",
              "org.jboss.cache.loader.jdbm.JdbmCacheLoader", false);
   }

   public void testCompatibilityBetweenFileAndJDBCCacheLoaders() throws Exception
   {
      initialStateTferWithLoaderTest("org.jboss.cache.loader.FileCacheLoader",
              "org.jboss.cache.loader.JDBCCacheLoader", false);
   }

   public void testCompatibilityBetweenFileAndBdbjeCacheLoaders() throws Exception
   {

      initialStateTferWithLoaderTest("org.jboss.cache.loader.FileCacheLoader",
              "org.jboss.cache.loader.bdbje.BdbjeCacheLoader", false);
   }

   public void testCompatibilityBetweenJbdmAndJDBCCacheLoaders() throws Exception
   {
      initialStateTferWithLoaderTest("org.jboss.cache.loader.jdbm.JdbmCacheLoader",
              "org.jboss.cache.loader.JDBCCacheLoader", false);
   }

   public void testCompatibilityBetweenJbdmAndBdbjeCacheLoaders() throws Exception
   {
      initialStateTferWithLoaderTest("org.jboss.cache.loader.jdbm.JdbmCacheLoader",
              "org.jboss.cache.loader.bdbje.BdbjeCacheLoader", false);
   }

   public void testCompatibilityBetweenJbdmAndFileCacheLoaders() throws Exception
   {
      initialStateTferWithLoaderTest("org.jboss.cache.loader.jdbm.JdbmCacheLoader",
              "org.jboss.cache.loader.FileCacheLoader", false);
   }

   public void testCompatibilityBetweenJDBCAndBdjeCacheLoaders() throws Exception
   {
      initialStateTferWithLoaderTest("org.jboss.cache.loader.JDBCCacheLoader",
              "org.jboss.cache.loader.bdbje.BdbjeCacheLoader", false);
   }

   public void testCompatibilityBetweenJDBCAndFileCacheLoaders() throws Exception
   {

      initialStateTferWithLoaderTest("org.jboss.cache.loader.JDBCCacheLoader",
              "org.jboss.cache.loader.FileCacheLoader", false);
   }

   public void testCompatibilityBetweenJDBCAndJbdmCacheLoaders() throws Exception
   {

      initialStateTferWithLoaderTest("org.jboss.cache.loader.JDBCCacheLoader",
              "org.jboss.cache.loader.jdbm.JdbmCacheLoader", false);
   }

   public void testCompatibilityBetweenBdbjeandJDBCCacheLoaders() throws Exception
   {

      initialStateTferWithLoaderTest("org.jboss.cache.loader.bdbje.BdbjeCacheLoader",
              "org.jboss.cache.loader.JDBCCacheLoader", false);
   }

   public void testCompatibilityBetweenBdbjeandFileCacheLoaders() throws Exception
   {

      initialStateTferWithLoaderTest("org.jboss.cache.loader.bdbje.BdbjeCacheLoader",
              "org.jboss.cache.loader.FileCacheLoader", false);
   }

   public void testCompatibilityBetweenBdbjeandJbdmCacheLoaders() throws Exception
   {

      initialStateTferWithLoaderTest("org.jboss.cache.loader.bdbje.BdbjeCacheLoader",
              "org.jboss.cache.loader.jdbm.JdbmCacheLoader", false);
   }

   protected void initialStateTferWithLoaderTest(boolean asyncLoader) throws Exception
   {
      initialStateTferWithLoaderTest("org.jboss.cache.loader.FileCacheLoader",
              "org.jboss.cache.loader.FileCacheLoader", asyncLoader);
   }
}
