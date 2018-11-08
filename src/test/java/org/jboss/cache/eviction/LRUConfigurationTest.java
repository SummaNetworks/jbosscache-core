/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.config.EvictionConfig;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.element.EvictionElementParser;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

/**
 * Unit tests for LRUConfiguration.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = "unit", sequential = false, testName = "eviction.LRUConfigurationTest")
public class LRUConfigurationTest
{

   public void testXMLParsing() throws Exception
   {
      LRUAlgorithmConfig config = new LRUAlgorithmConfig();
      String xml =
            "<region name=\"/org/jboss/data\">\n" +
                  "<property name=\"maxNodes\" value=\"5000\"></property>\n" +
                  "<property name=\"timeToLive\" value=\"1000\"></property>\n" +
                  "</region>";

      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(5000, config.getMaxNodes());
      assertEquals(1000, config.getTimeToLive());
   }

   public void testXMLParsing2() throws Exception
   {
      LRUAlgorithmConfig config = new LRUAlgorithmConfig();
      String xml = "<region name=\"/maxAgeTest/\">\n" +
            "<property name=\"maxNodes\" value=\"10000\"></property>\n" +
            "<property name=\"timeToLive\" value=\"1000\"></property>\n" +
            "<property name=\"maxAge\" value=\"10\"></property>\n" +
            "</region>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(10000, config.getMaxNodes());
      assertEquals(1000, config.getTimeToLive());
      assertEquals(10, config.getMaxAge());
   }

   public void testXMLParsing3() throws Exception
   {
      String xml = "<eviction wakeupInterval=\"30\"><region name=\"/maxAgeTest/\">\n" +
            "<property name=\"maxNodes\" value=\"10000\"></property>\n" +
            "<property name=\"maxAge\" value=\"10\"></property>\n" +
            "</region></eviction>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);
      boolean caught = false;
      try
      {
         EvictionConfig ec = new EvictionElementParser().parseEvictionElement(element);
         ec.getEvictionRegionConfigs().get(0).validate();
      }
      catch (ConfigurationException ce)
      {
         caught = true;
      }
      assertTrue("Configure exception should have been caught", caught);
   }
}
