package org.jboss.cache.config.parsing;

import org.jboss.cache.Fqn;
import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.config.MissingPolicyException;
import org.jboss.cache.config.parsing.element.EvictionElementParser;
import org.jboss.cache.eviction.DefaultEvictionActionPolicy;
import org.jboss.cache.eviction.FIFOAlgorithmConfig;
import org.jboss.cache.eviction.LFUAlgorithmConfig;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.jboss.cache.eviction.MRUAlgorithmConfig;
import org.jboss.cache.eviction.NullEvictionAlgorithm;
import org.jboss.cache.eviction.NullEvictionAlgorithmConfig;
import org.jboss.cache.eviction.RemoveOnEvictActionPolicy;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

/**
 * Tester class for {@link org.jboss.cache.config.parsing.element.EvictionElementParser}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "unit", testName = "config.parsing.EvictionElementParserTest")
public class EvictionElementParserTest
{
   EvictionElementParser parser = new EvictionElementParser();

   /**
    * Test that when nodes are not specified the config defaults to certain values.
    */
   public void testDefaults()
   {
      String xml =
            "   <eviction wakeUpInterval=\"5\">\n" +
                  "      <default algorithmClass=\"org.jboss.cache.eviction.MRUAlgorithm\">\n" +
                  "         <property name=\"maxNodes\" value=\"10\"></property>\n" +
                  "         <property name=\"minTimeToLive\" value=\"10\"></property>\n" +
                  "      </default>\n" +
                  "      <region name=\"/org/jboss/xyz\" eventQueueSize=\"21\">\n" +
                  "         <property name=\"maxNodes\" value=\"2103\"></property>\n" +
                  "         <property name=\"minTimeToLive\" value=\"22\"></property>\n" +
                  "      </region>\n" +
                  "   </eviction>";
      EvictionConfig evictionConfig = getEvictionConfig(xml, false);
      assert evictionConfig.getDefaultEvictionRegionConfig().getEventQueueSize() == EvictionConfig.EVENT_QUEUE_SIZE_DEFAULT;
      assert evictionConfig.getDefaultEvictionRegionConfig().getEvictionActionPolicyClassName().equals(DefaultEvictionActionPolicy.class.getName());
   }

   public void testRegionWithNoProperties()
   {
      String xml =
            "   <eviction wakeUpInterval=\"5\">\n" +
                  "      <default algorithmClass=\"org.jboss.cache.eviction.MRUAlgorithm\">\n" +
                  "         <property name=\"maxNodes\" value=\"10\"></property>\n" +
                  "         <property name=\"minTimeToLive\" value=\"10\"></property>\n" +
                  "      </default>\n" +
                  "      <region name=\"/org/jboss/abc\" eventQueueSize=\"21\">\n" +
                  "      </region>\n" +
                  "      <region name=\"/org/jboss/xyz\" algorithmClass=\"org.jboss.cache.eviction.FIFOAlgorithm\">\n" +
                  "      </region>\n" +
                  "   </eviction>";
      EvictionConfig evictionConfig = getEvictionConfig(xml, false);
      assert evictionConfig.getDefaultEvictionRegionConfig().getEventQueueSize() == EvictionConfig.EVENT_QUEUE_SIZE_DEFAULT;
      assert evictionConfig.getDefaultEvictionRegionConfig().getEvictionActionPolicyClassName().equals(DefaultEvictionActionPolicy.class.getName());
      EvictionRegionConfig abc = evictionConfig.getEvictionRegionConfig("/org/jboss/abc");
      EvictionRegionConfig xyz = evictionConfig.getEvictionRegionConfig("/org/jboss/xyz");

      assert abc.getEventQueueSize() == 21;
      assert abc.getEvictionAlgorithmConfig() instanceof MRUAlgorithmConfig;
      assert ((MRUAlgorithmConfig) abc.getEvictionAlgorithmConfig()).getMaxNodes() == 10;
      assert ((MRUAlgorithmConfig) abc.getEvictionAlgorithmConfig()).getMinTimeToLive() == 10;

      assert xyz.getEventQueueSize() == EvictionConfig.EVENT_QUEUE_SIZE_DEFAULT;
      assert xyz.getEvictionAlgorithmConfig() instanceof FIFOAlgorithmConfig;
      assert ((FIFOAlgorithmConfig) xyz.getEvictionAlgorithmConfig()).getMaxNodes() == -1;
      assert ((FIFOAlgorithmConfig) xyz.getEvictionAlgorithmConfig()).getMinTimeToLive() == -1;      
   }

   /**
    * test unnecessary propertys
    */
   public void testUnnecessaryAttributes()
   {
      String xml =
            "   <eviction wakeUpInterval=\"5\" defaultPolicyClass=\"org.jboss.cache.eviction.MRUPolicy\" defaultEventQueueSize=\"123456\">\n" +
                  "      <default>\n" +
                  "         <property name=\"maxNodes\" value=\"6\"></property>\n" +
                  "         <property name=\"minTimeToLive\" value=\"7\"></property>\n" +
                  "      </default>\n" +
                  "   </eviction>";
      try
      {
         EvictionConfig config = getEvictionConfig(xml, false);
         assert false : "Should throw ConfigurationException!";
      }
      catch (ConfigurationException good)
      {
         // expected
      }
   }


   /**
    * test an happy flow.
    */
   public void testNormalConfig()
   {
      String xml =
            "   <eviction wakeUpInterval=\"5\">\n" +
                  "      <default algorithmClass=\"org.jboss.cache.eviction.MRUAlgorithm\" eventQueueSize=\"123456\">\n" +
                  "         <property name=\"maxNodes\" value=\"6\"></property>\n" +
                  "         <property name=\"minTimeToLive\" value=\"7\"></property>\n" +
                  "      </default>\n" +
                  "      <region name=\"/org/jboss/data\">\n" +
                  "         <property name=\"minTimeToLive\" value=\"1002\"></property>\n" +
                  "         <property name=\"maxNodes\" value=\"2021\"></property>\n" +
                  "      </region>\n" +
                  "      <region name=\"/org/jboss/xyz\" algorithmClass=\"org.jboss.cache.eviction.LRUAlgorithm\" eventQueueSize=\"21\">\n" +
                  "         <property name=\"maxNodes\" value=\"2103\"></property>\n" +
                  "         <property name=\"timeToLive\" value=\"22\"></property>\n" +
                  "      </region>\n" +
                  "   </eviction>";
      EvictionConfig config = getEvictionConfig(xml, false);
      //tests the defaults
      assert config.getWakeupInterval() == 5;
      assert config.getDefaultEvictionRegionConfig().getEvictionAlgorithmConfig() instanceof MRUAlgorithmConfig;
      assert config.getDefaultEvictionRegionConfig().getEventQueueSize() == 123456;
      assert config.getEvictionRegionConfigs().size() == 2;

      //test first region config
      EvictionRegionConfig erConfig1 = config.getDefaultEvictionRegionConfig();
      erConfig1.getRegionFqn().equals(Fqn.ROOT);
      MRUAlgorithmConfig defaultPolicyConfig = (MRUAlgorithmConfig) erConfig1.getEvictionAlgorithmConfig();
      assert defaultPolicyConfig.getMaxNodes() == 6;
      assert defaultPolicyConfig.getMinTimeToLive() == 7;

      //test second region config
      EvictionRegionConfig erConfig2 = config.getEvictionRegionConfigs().get(0);
      assert erConfig2.getEventQueueSize() == 123456 : "Got " + erConfig2.getEventQueueSize();
      assert erConfig2.getRegionFqn().equals(Fqn.fromString("/org/jboss/data"));
      MRUAlgorithmConfig mruConfiguration = (MRUAlgorithmConfig) erConfig2.getEvictionAlgorithmConfig();
      assert mruConfiguration.getMinTimeToLive() == 1002;
      assert mruConfiguration.getMaxNodes() == 2021;

      //test 3rd region config
      EvictionRegionConfig erConfig3 = config.getEvictionRegionConfigs().get(1);
      assert erConfig3.getEventQueueSize() == 21;
      assert erConfig3.getRegionFqn().equals(Fqn.fromString("/org/jboss/xyz"));
      LRUAlgorithmConfig lruConfiguration = (LRUAlgorithmConfig) erConfig3.getEvictionAlgorithmConfig();
      assert lruConfiguration.getTimeToLive() == 22;
      assert lruConfiguration.getMaxNodes() == 2103;

      assert config.getDefaultEvictionRegionConfig().getEvictionActionPolicyClassName().equals(DefaultEvictionActionPolicy.class.getName());
   }

   public void testLruConfig()
   {
      String xml =
            "   <eviction wakeUpInterval=\"45000\">\n" +
                  "      <default algorithmClass=\"org.jboss.cache.eviction.LRUAlgorithm\" eventQueueSize=\"4\">\n" +
                  "         <property name=\"maxNodes\" value=\"5000\"></property>\n" +
                  "         <property name=\"timeToLive\" value=\"1000000\"></property>\n" +
                  "         <property name=\"maxAge\" value=\"15000\"></property>\n" +
                  "      </default>\n" +
                  "      <region name=\"/fifo\">\n" +
                  "         <property name=\"maxNodes\" value=\"5000\"></property>\n" +
                  "         <property name=\"timeToLive\" value=\"1000000\"></property>\n" +
                  "      </region>\n" +
                  "      <region name=\"/mru\">\n" +
                  "         <property name=\"maxNodes\" value=\"10000\"></property>\n" +
                  "         <property name=\"timeToLive\" value=\"1000000\"></property>\n" +
                  "      </region>\n" +
                  "      <region name=\"/lfu\">\n" +
                  "         <property name=\"maxNodes\" value=\"5000\"></property>\n" +
                  "         <property name=\"timeToLive\" value=\"1000000\"></property>\n" +
                  "      </region>\n" +
                  "   </eviction>";
      EvictionConfig evConfig = getEvictionConfig(xml, false);
      EvictionRegionConfig evictionRegionConfig = evConfig.getDefaultEvictionRegionConfig();
      assert evictionRegionConfig.getRegionName().equals(Fqn.ROOT.toString()) : "Was " + evictionRegionConfig.getRegionName();
      assert ((LRUAlgorithmConfig) evictionRegionConfig.getEvictionAlgorithmConfig()).getTimeToLive() == 1000000;

      assert evConfig.getDefaultEvictionRegionConfig().getEvictionActionPolicyClassName().equals(DefaultEvictionActionPolicy.class.getName());
   }

   /**
    * This is an mandatory parameter, if it is not present then an configuration exception is expected.
    */
   public void testMissingWakeUpInterval() throws Exception
   {
      String xml =
            "   <eviction>\n" +
                  "      <default algorithmClass=\"org.jboss.cache.eviction.LRUAlgorithm\" eventQueueSize=\"200000\">\n" +
                  "         <property name=\"maxNodes\">5000</property>\n" +
                  "         <property name=\"timeToLive\">1000</property>\n" +
                  "      </default>\n" +
                  "      <region name=\"/org/jboss/data\">\n" +
                  "         <property name=\"timeToLive\">1002</property>\n" +
                  "      </region>\n" +
                  "   </eviction>";
      try
      {
         getEvictionConfig(xml, false);
         assert false : "exception expected as wake up interval is not set";
      }
      catch (ConfigurationException e)
      {
         //expected
      }
   }

   /**
    * If no policy is configured at region basis, and also no policy is configured at region basis,
    * exception is expected.
    */
   public void testMissingPolicyOnRegion()
   {
      String xml =
            "   <eviction wakeUpInterval=\"5000\">\n" +
                  "      <region name=\"/org/jboss/data\" eventQueueSize=\"5\">\n" +
                  "         <property name=\"timeToLive\">1002</property>\n" +
                  "      </region>\n" +
                  "   </eviction>";
      try
      {
         getEvictionConfig(xml, false);
         assert false : "missing policy in both default and region, exception expected.";
      }
      catch (MissingPolicyException e)
      {
         //expected
      }
   }

   /**
    * Same as above, except no queue size is specified.  SHould NOT fail.
    */
   public void testMissingQueueSizeOnRegion()
   {
      String xml =
            "   <eviction wakeUpInterval=\"5000\">\n" +
                  "      <region name=\"/org/jboss/data\" algorithmClass=\"org.jboss.cache.eviction.LRUAlgorithm\">\n" +
                  "         <property name=\"timeToLive\">1002</property>\n" +
                  "      </region>\n" +
                  "   </eviction>";
      EvictionConfig ec = getEvictionConfig(xml, false);
      assert ec.getEvictionRegionConfigs().get(0).getEventQueueSize() == EvictionConfig.EVENT_QUEUE_SIZE_DEFAULT;
   }


   private EvictionConfig getEvictionConfig(String xml, boolean validate)
   {
      Element el;
      try
      {
         el = XmlConfigHelper.stringToElementInCoreNS(xml);
      }
      catch (Exception e)
      {
         throw new ConfigurationException(e);
      }
      EvictionConfig cfg = parser.parseEvictionElement(el);
      if (validate)
      {
         cfg.getDefaultEvictionRegionConfig().validate();
         for (EvictionRegionConfig erc : cfg.getEvictionRegionConfigs()) erc.validate();
      }
      return cfg;
   }

   public void testMissingDefaultEvictionClass() throws Exception
   {
      String xml =
            "   <eviction wakeUpInterval=\"5000\">\n" +
                  "      <default>\n" +
                  "         <property name=\"maxNodes\">5000</property>\n" +
                  "         <property name=\"timeToLive\">1000</property>\n" +
                  "      </default>\n" +
                  "      <region name=\"/org/jboss/data\" algorithmClass=\"org.jboss.cache.eviction.LFUAlgorithm\">\n" +
                  "         <property name=\"maxNodes\">5000</property>\n" +
                  "         <property name=\"minNodes\">1000</property>\n" +
                  "      </region>\n" +
                  "   </eviction>";
      try
      {
         getEvictionConfig(xml, true);
         assert false : " exception expected as default element does not have a eviction policy defined";
      }
      catch (MissingPolicyException e)
      {
         //expected
      }
   }

   public void testDifferentEvictionActionPolicyClasses() throws Exception
   {
      String xml =
            "   <eviction wakeUpInterval=\"5000\">\n" +
                  "      <default algorithmClass=\"" + NullEvictionAlgorithm.class.getName() + "\" actionPolicyClass=\"" + RemoveOnEvictActionPolicy.class.getName() + "\">\n" +
                  "      </default>\n" +
                  "      <region name=\"/one\" algorithmClass=\"org.jboss.cache.eviction.LFUAlgorithm\">\n" +
                  "         <property name=\"maxNodes\">5000</property>\n" +
                  "         <property name=\"minNodes\">1000</property>\n" +
                  "      </region>\n" +
                  "      <region name=\"/two\" actionPolicyClass=\"" + DefaultEvictionActionPolicy.class.getName() + "\">\n" +
                  "      </region>\n" +
                  "   </eviction>";
      EvictionConfig config = getEvictionConfig(xml, false);

      // default region
      assert config.getDefaultEvictionRegionConfig().getEvictionAlgorithmConfig() instanceof NullEvictionAlgorithmConfig;
      assert config.getDefaultEvictionRegionConfig().getEvictionActionPolicyClassName().equals(RemoveOnEvictActionPolicy.class.getName());

      // region /one
      assert findRegionConfig(config, "/one").getEvictionAlgorithmConfig() instanceof LFUAlgorithmConfig;
      assert findRegionConfig(config, "/one").getEvictionActionPolicyClassName().equals(RemoveOnEvictActionPolicy.class.getName());

      // region /two
      assert findRegionConfig(config, "/two").getEvictionAlgorithmConfig() instanceof NullEvictionAlgorithmConfig;
      assert findRegionConfig(config, "/two").getEvictionActionPolicyClassName().equals(DefaultEvictionActionPolicy.class.getName());
   }

   private EvictionRegionConfig findRegionConfig(EvictionConfig evictionConfig, String fqn)
   {
      for (EvictionRegionConfig erc : evictionConfig.getEvictionRegionConfigs())
      {
         if (erc.getRegionFqn().equals(Fqn.fromString(fqn))) return erc;
      }

      return null;
   }
}
