/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.cache.config.parsing;

import org.jboss.cache.Fqn;
import org.jboss.cache.config.EvictionAlgorithmConfig;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import org.jboss.cache.config.parsing.element.EvictionElementParser;
import org.jboss.cache.eviction.MRUAlgorithmConfig;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

/**
 * Eviction was internally changed from version 2.x to version 3.x.
 * This is a tests to check eviction compatibility between these two versions.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "unit", testName = "config.parsing.Eviction2xto3xCompatibilityTest")
public class Eviction2xto3xCompatibilityTest
{
   private EvictionElementParser evictionElementParser;

   @BeforeMethod
   public void setUp()
   {
      evictionElementParser = new EvictionElementParser();
   }

   public void testDefaultValues1() throws Exception
   {
      String oldFormat =
                  "      <attribute name=\"EvictionPolicyConfig\">\n" +
                  "         <config>\n" +
                  "            <attribute name=\"wakeUpIntervalSeconds\">5</attribute>\n" +
                  "            <attribute name=\"eventQueueSize\">200000</attribute>\n" +
                  "            <attribute name=\"policyClass\">org.jboss.cache.eviction.LRUPolicy</attribute>\n" +
                  "            <region name=\"/_default_\" eventQueueSize=\"1234\" policyClass=\"org.jboss.cache.eviction.MRUPolicy\">\n" +
                  "               <attribute name=\"maxNodes\">5001</attribute>\n" +
                  "               <attribute name=\"minTimeToLiveSeconds\">1001</attribute>\n" +
                  "            </region>\n" +
                  "            <region name=\"/org/jboss/data\">\n" +
                  "               <attribute name=\"minTimeToLiveSeconds\">1000</attribute>\n" +
                  "               <attribute name=\"maxNodes\">5000</attribute>\n" +
                  "            </region>\n" +
                  "         </config>\n" +
                  "      </attribute>";
      Element oldEl = XmlConfigHelper.stringToElementInCoreNS(oldFormat);
      EvictionConfig oldEvConfig = XmlConfigurationParser2x.parseEvictionConfig(oldEl);
      //this will be transformed in root region, so make sure that the root region will be corectly set up
      MRUAlgorithmConfig defaultAlgorithmConfig = (MRUAlgorithmConfig) oldEvConfig.getDefaultEvictionRegionConfig().getEvictionAlgorithmConfig();
      assert oldEvConfig.getDefaultEvictionRegionConfig().getEventQueueSize() == 1234;
      assert defaultAlgorithmConfig.getEvictionAlgorithmClassName().equals("org.jboss.cache.eviction.MRUAlgorithm");
      assert defaultAlgorithmConfig.getMaxNodes() == 5001;
      assert defaultAlgorithmConfig.getMinTimeToLive() == 1001000;


      assert oldEvConfig.getEvictionRegionConfigs().size() == 1;
      EvictionRegionConfig orgJbossData = oldEvConfig.getEvictionRegionConfigs().get(0);
      assert orgJbossData.getRegionFqn().equals(Fqn.fromString("org/jboss/data"));
      assert orgJbossData.getEvictionAlgorithmConfig().getEvictionAlgorithmClassName().equals("org.jboss.cache.eviction.LRUAlgorithm");
      assert orgJbossData.getEventQueueSize() == 200000;
   }

   public void simpleTest() throws Exception
   {
      String oldFormat =
            "      <attribute name=\"EvictionPolicyConfig\">\n" +
                  "         <config>\n" +
                  "            <attribute name=\"wakeUpIntervalSeconds\">5</attribute>\n" +
                  "            <attribute name=\"eventQueueSize\">200000</attribute>\n" +
                  "            <attribute name=\"policyClass\">org.jboss.cache.eviction.LRUPolicy</attribute>\n" +
                  "            <region name=\"/_default_\">\n" +
                  "               <attribute name=\"maxNodes\">5000</attribute>\n" +
                  "               <attribute name=\"timeToLiveSeconds\">1000</attribute>\n" +
                  "            </region>\n" +
                  "            <region name=\"/org/jboss/data\"  policyClass=\"org.jboss.cache.eviction.LFUPolicy\">\n" +
                  "               <attribute name=\"minTimeToLiveSeconds\">1000</attribute>\n" +
                  "               <attribute name=\"maxNodes\">5000</attribute>\n" +
                  "            </region>\n" +
                  "         </config>\n" +
                  "      </attribute>";
      String newFormat =
                  "<eviction wakeUpInterval=\"5000\">\n" +
                  "  <default algorithmClass=\"org.jboss.cache.eviction.LRUAlgorithm\" eventQueueSize=\"200000\">\n" +
                  "     <property name=\"maxNodes\" value=\"5000\"></property>\n" +
                  "     <property name=\"timeToLive\" value=\"1000000\"></property>\n" +
                  "  </default>\n" +
                  "  <region name=\"/org/jboss/data\" algorithmClass=\"org.jboss.cache.eviction.LFUAlgorithm\" eventQueueSize=\"200000\">\n" +
                  "     <property name=\"minTimeToLive\" value=\"1000000\"></property>\n" +
                  "     <property name=\"maxNodes\" value=\"5000\"></property>\n" +
                  "  </region>\n" +
                  "</eviction>";
      Element oldEl = XmlConfigHelper.stringToElementInCoreNS(oldFormat);
      Element newEl = XmlConfigHelper.stringToElementInCoreNS(newFormat);
      EvictionConfig oldEvConfig = XmlConfigurationParser2x.parseEvictionConfig(oldEl);
      EvictionConfig newEvConfig = evictionElementParser.parseEvictionElement(newEl);
      assert oldEvConfig.getDefaultEvictionRegionConfig().equals(newEvConfig.getDefaultEvictionRegionConfig());

      EvictionRegionConfig oldRegionConfig = oldEvConfig.getEvictionRegionConfigs().get(0);
      EvictionRegionConfig newRegionConfig = newEvConfig.getEvictionRegionConfigs().get(0);
      EvictionAlgorithmConfig oldEvictionAlgorithmConfig = oldRegionConfig.getEvictionAlgorithmConfig();
      EvictionAlgorithmConfig newEvictionAlgorithmConfig = newRegionConfig.getEvictionAlgorithmConfig();
      assert oldEvictionAlgorithmConfig.equals(newEvictionAlgorithmConfig);

      assert oldEvConfig.equals(newEvConfig);
   }

   public void testFailureOnCustomEvictionPolicy() throws Exception
   {
      String oldFormat =
            "      <attribute name=\"EvictionPolicyConfig\">\n" +
                  "         <config>\n" +
                  "            <attribute name=\"wakeUpIntervalSeconds\">5</attribute>\n" +
                  "            <attribute name=\"eventQueueSize\">200000</attribute>\n" +
                  "            <attribute name=\"policyClass\">org.jboss.cache.eviction.LRUPolicy</attribute>\n" +
                  "            <region name=\"/_default_\">\n" +
                  "               <attribute name=\"maxNodes\">5000</attribute>\n" +
                  "               <attribute name=\"timeToLiveSeconds\">1000</attribute>\n" +
                  "            </region>\n" +
                  "            <region name=\"/org/jboss/data\"  policyClass=\"org.custom.eviction.policy.LFUPolicy\">\n" +
                  "               <attribute name=\"minTimeToLiveSeconds\">1000</attribute>\n" +
                  "               <attribute name=\"maxNodes\">5000</attribute>\n" +
                  "            </region>\n" +
                  "         </config>\n" +
                  "      </attribute>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(oldFormat);
      try
      {
         XmlConfigurationParser2x.parseEvictionConfig(element);
         assert false : "custom eviction config not supported for the old parser";
      } catch (Exception e)
      {
      }
   }

   public void testFailureOnCustomDefaultEvictionPolicy() throws Exception
   {
      String oldFormat =
            "      <attribute name=\"EvictionPolicyConfig\">\n" +
                  "         <config>\n" +
                  "            <attribute name=\"wakeUpIntervalSeconds\">5</attribute>\n" +
                  "            <attribute name=\"eventQueueSize\">200000</attribute>\n" +
                  "            <attribute name=\"policyClass\">org.custom.eviction.policy.LFUPolicy</attribute>\n" +
                  "            <region name=\"/_default_\">\n" +
                  "               <attribute name=\"maxNodes\">5000</attribute>\n" +
                  "               <attribute name=\"timeToLiveSeconds\">1000</attribute>\n" +
                  "            </region>\n" +
                  "            <region name=\"/org/jboss/data\" policyClass=\"org.jboss.cache.eviction.LRUPolicy\">\n" +
                  "               <attribute name=\"minTimeToLiveSeconds\">1000</attribute>\n" +
                  "               <attribute name=\"maxNodes\">5000</attribute>\n" +
                  "            </region>\n" +
                  "         </config>\n" +
                  "      </attribute>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(oldFormat);
      try
      {
         XmlConfigurationParser2x.parseEvictionConfig(element);
         assert false : "default custom eviction config not supported for the old parser";
      } catch (Exception e)
      {
      }
   }
}
