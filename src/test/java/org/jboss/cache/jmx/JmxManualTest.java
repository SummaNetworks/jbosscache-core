package org.jboss.cache.jmx;

import org.jboss.cache.Cache;
import org.jboss.cache.DefaultCacheFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.eviction.FIFOAlgorithmConfig;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

// do NOT enable this test in SVN as it will cause Hudson (or any other continuous integration test harness) to get

// stuck.
@Test(groups = "maual", enabled = false, testName = "jmx.JmxManualTest")
public class JmxManualTest
{
   public void testLocal() throws IOException
   {
      Configuration c = new Configuration();
      Cache cache = new DefaultCacheFactory().createCache(c);
      cache.put("/a/b/c", "a", "b");
      cache.put("/a/b/c", "c", "d");
      cache.put("/a/b/d", "a", "b");
      cache.put("/a/b/e", "c", "d");

      System.in.read();
   }

   public void testLocalNoJMX() throws IOException
   {
      Configuration c = new Configuration();
      c.setExposeManagementStatistics(false);
      Cache cache = new DefaultCacheFactory().createCache(c);
      cache.put("/a/b/c", "a", "b");
      cache.put("/a/b/c", "c", "d");
      cache.put("/a/b/d", "a", "b");
      cache.put("/a/b/e", "c", "d");

      System.in.read();
   }

   public void testLocalWithEviction() throws IOException
   {
      Configuration c = new Configuration();
      EvictionConfig ec = new EvictionConfig();
      ec.setWakeupInterval(250, TimeUnit.MILLISECONDS);
      EvictionRegionConfig erc = new EvictionRegionConfig();
      erc.setEvictionAlgorithmConfig(new FIFOAlgorithmConfig(2));
      erc.setRegionFqn(Fqn.ROOT);
      ec.setDefaultEvictionRegionConfig(erc);
      c.setEvictionConfig(ec);
      Cache cache = new DefaultCacheFactory().createCache(c);
      cache.put("/a/b/c", "a", "b");
      cache.put("/a/b/c", "c", "d");
      cache.put("/a/b/d", "a", "b");
      cache.put("/a/b/e", "c", "d");

      System.in.read();
   }

   public void testLocalWithEvictionXML() throws IOException
   {
      Cache cache = new DefaultCacheFactory().createCache("config-samples/eviction-enabled.xml");
      cache.put("/a/b/c", "a", "b");
      cache.put("/a/b/c", "c", "d");
      cache.put("/a/b/d", "a", "b");
      cache.put("/a/b/e", "c", "d");

      System.in.read();
   }

}
