package org.jboss.cache.eviction.legacy;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionPolicyConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.config.UnsupportedEvictionImplException;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.config.parsing.XmlConfigurationParser2x;
import org.jboss.cache.eviction.BaseEvictionPolicy;
import org.jboss.cache.eviction.DefaultEvictionActionPolicy;
import org.jboss.cache.eviction.EvictionAlgorithm;
import org.jboss.cache.eviction.EvictionPolicyConfigBase;
import org.jboss.cache.eviction.FIFOAlgorithm;
import org.jboss.cache.eviction.FIFOAlgorithmConfig;
import org.jboss.cache.eviction.FIFOConfiguration;
import org.jboss.cache.eviction.FIFOPolicy;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

@Test(groups = "functional", testName = "eviction.legacy.BackwardCompatibilityTest")
public class BackwardCompatibilityTest
{

   private EvictionRegionConfig createEvictionRegionConfig(String regionName, int maxNodes, EvictionPolicyConfigBase cfg)
   {
      EvictionRegionConfig ercDefault = new EvictionRegionConfig();
      ercDefault.setRegionName(regionName);
      if (maxNodes >= 0) cfg.setMaxNodes(maxNodes);
      ercDefault.setEvictionPolicyConfig(cfg);
      return ercDefault;
   }


   public void testUsingEvictionPolicy()
   {
      Configuration c = new Configuration();
      c.setEvictionConfig(new EvictionConfig());
      EvictionConfig evConfig = c.getEvictionConfig();
      evConfig.setDefaultEventQueueSize(20000);
      evConfig.setDefaultEvictionPolicyClass(FIFOPolicy.class.getName());
      List<EvictionRegionConfig> erConfigs = new ArrayList<EvictionRegionConfig>();
      erConfigs.add(createEvictionRegionConfig("/_default_", 2, new FIFOConfiguration()));
      evConfig.setEvictionRegionConfigs(erConfigs);
      doTest(c);
   }

   public void testUsingEvictionPolicyXml() throws Exception
   {
      String xml = "<attribute name=\"EvictionPolicyConfig\">\n" +
            "         <config>\n" +
            "            <attribute name=\"wakeUpIntervalSeconds\">2</attribute>\n" +
            "            <!-- Name of the DEFAULT eviction policy class. -->\n" +
            "            <attribute name=\"policyClass\">org.jboss.cache.eviction.FIFOPolicy</attribute>\n" +
            "\n" +
            "            <!-- Cache wide default -->\n" +
            "            <region name=\"/_default_\">\n" +
            "               <attribute name=\"maxNodes\">2</attribute>\n" +
            "            </region>\n" +
            "         </config>\n" +
            "      </attribute>";

      doTest(xml, true);
   }

   public void testUsingCustomEvictionPolicy()
   {
      try
      {
         Configuration c = new Configuration();
         c.setEvictionConfig(new EvictionConfig());
         EvictionConfig evConfig = c.getEvictionConfig();
         evConfig.setDefaultEventQueueSize(20000);
         evConfig.setDefaultEvictionPolicyClass(MyPolicy.class.getName());
         List<EvictionRegionConfig> erConfigs = new ArrayList<EvictionRegionConfig>();
         erConfigs.add(createEvictionRegionConfig("/_default_", 2, new MyPolicyConfig()));
         evConfig.setEvictionRegionConfigs(erConfigs);
         doTest(c);
         assert false : "Should throw exception";
      }
      catch (UnsupportedEvictionImplException ce)
      {
         // expected
      }
   }

   public void testUsingCustomEvictionPolicyXml() throws Exception
   {
      String xml = "<attribute name=\"EvictionPolicyConfig\">\n" +
            "         <config>\n" +
            "            <attribute name=\"wakeUpIntervalSeconds\">2</attribute>\n" +
            "            <!-- Name of the DEFAULT eviction policy class. -->\n" +
            "            <attribute name=\"policyClass\">" + MyPolicy.class.getName() + "</attribute>\n" +
            "\n" +
            "            <!-- Cache wide default -->\n" +
            "            <region name=\"/_default_\">\n" +
            "               <attribute name=\"maxNodes\">2</attribute>\n" +
            "            </region>\n" +
            "         </config>\n" +
            "      </attribute>";

      try
      {
         doTest(xml, true);
         assert false : "Should throw exception";
      }
      catch (UnsupportedEvictionImplException ce)
      {
         // expected
      }
   }

   public void testUsingCustomEvictionPolicyNonDefault()
   {
      try
      {
         Configuration c = new Configuration();
         c.setEvictionConfig(new EvictionConfig());
         EvictionConfig evConfig = c.getEvictionConfig();
         evConfig.setDefaultEventQueueSize(20000);
         evConfig.setDefaultEvictionPolicyClass(FIFOPolicy.class.getName());
         List<EvictionRegionConfig> erConfigs = new ArrayList<EvictionRegionConfig>();
         erConfigs.add(createEvictionRegionConfig("/_default_", 2, new FIFOConfiguration()));
         erConfigs.add(createEvictionRegionConfig("/a/b/c", 2, new MyPolicyConfig()));
         evConfig.setEvictionRegionConfigs(erConfigs);
         doTest(c);
         assert false : "Should throw exception";
      }
      catch (UnsupportedEvictionImplException ce)
      {
         // expected
      }
   }

   public void testUsingCustomEvictionPolicyNonDefaultXml() throws Exception
   {
      String xml = "<attribute name=\"EvictionPolicyConfig\">\n" +
            "         <config>\n" +
            "            <attribute name=\"wakeUpIntervalSeconds\">2</attribute>\n" +
            "            <!-- Name of the DEFAULT eviction policy class. -->\n" +
            "            <attribute name=\"policyClass\">" + FIFOPolicy.class.getName() + "</attribute>\n" +
            "\n" +
            "            <!-- Cache wide default -->\n" +
            "            <region name=\"/_default_\">\n" +
            "               <attribute name=\"maxNodes\">2</attribute>\n" +
            "            </region>\n" +
            "            <region name=\"/a/b/c\" policyClass=\"" + MyPolicy.class.getName() + "\">" +
            "               <attribute name=\"maxNodes\">2</attribute>\n" +
            "            </region>\n" +
            "         </config>\n" +
            "      </attribute>";

      try
      {
         doTest(xml, true);
         assert false : "Should throw exception";
      }
      catch (UnsupportedEvictionImplException ce)
      {
         // expected
      }
   }

   public void testControl()
   {
      Configuration c = new Configuration();
      c.setEvictionConfig(new EvictionConfig());
      EvictionRegionConfig defaultRegion = c.getEvictionConfig().getDefaultEvictionRegionConfig();
      defaultRegion.setEvictionAlgorithmConfig(new FIFOAlgorithmConfig(2));
      doTest(c);
   }

   public void testControlXml() throws Exception
   {
      String xml = "<jbosscache><eviction wakeUpInterval=\"6\">" +
            "<default algorithmClass=\"" + FIFOAlgorithm.class.getName() + "\">" +
            "<property name=\"maxNodes\" value=\"2\"></property>" +
            "</default>" +
            "</eviction></jbosscache>";
      doTest(xml, false);
   }

   private void doTest(String xml, boolean legacy) throws Exception
   {
      if (legacy)
      {
         EvictionConfig ec = XmlConfigurationParser2x.parseEvictionConfig(XmlConfigHelper.stringToElementInCoreNS(xml));
         Configuration c = new Configuration();
         c.setEvictionConfig(ec);
         doTest(c);
      }
      else
      {
         doTest(new XmlConfigurationParser().parseElement(XmlConfigHelper.stringToElementInCoreNS(xml)));
      }
   }

   private void doTest(Configuration c)
   {
      Cache cache = null;
      try
      {
         c.getEvictionConfig().setWakeupInterval(0);
         cache = new UnitTestCacheFactory().createCache(c, getClass());

         EvictionRegionConfig erc = cache.getRegion(Fqn.ROOT, false).getEvictionRegionConfig();
         assert erc.getEvictionAlgorithmConfig() instanceof FIFOAlgorithmConfig;
         assert erc.getEvictionActionPolicyClassName().equals(DefaultEvictionActionPolicy.class.getName());

         cache.put("/a/b/1", "a", "b");
         cache.put("/a/b/2", "a", "b");
         cache.put("/a/b/3", "a", "b");

         new EvictionController(cache).startEviction();

         assert cache.getNode("/a/b/1") == null;
         assert cache.getNode("/a/b/2") != null;
         assert cache.getNode("/a/b/3") != null;
      }
      finally
      {
         TestingUtil.killCaches(cache);
      }
   }

   public static class MyPolicy extends BaseEvictionPolicy
   {
      public EvictionAlgorithm getEvictionAlgorithm()
      {
         return null;
      }

      public Class<? extends EvictionPolicyConfig> getEvictionConfigurationClass()
      {
         return null;
      }
   }

   public class MyPolicyConfig extends EvictionPolicyConfigBase
   {

      protected void setEvictionPolicyClassName()
      {
         this.setEvictionPolicyClass(MyPolicy.class.getName());
      }
   }
}
