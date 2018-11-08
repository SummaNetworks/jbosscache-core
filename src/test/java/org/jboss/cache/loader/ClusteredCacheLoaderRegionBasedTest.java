package org.jboss.cache.loader;

import org.testng.annotations.Test;

@Test(groups = {"functional"}, testName = "loader.ClusteredCacheLoaderRegionBasedTest")
public class ClusteredCacheLoaderRegionBasedTest extends ClusteredCacheLoaderTest
{
   public ClusteredCacheLoaderRegionBasedTest()
   {
      useRegionBasedMarshalling = true;
   }
}
