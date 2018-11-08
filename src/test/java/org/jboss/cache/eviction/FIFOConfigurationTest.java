/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.element.EvictionElementParser;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

/**
 * Unit test for FIFOConfiguration.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = "unit", sequential = false, testName = "eviction.FIFOConfigurationTest")
public class FIFOConfigurationTest
{
   public void testXMLParse() throws Exception
   {
      FIFOAlgorithmConfig config = new FIFOAlgorithmConfig();
      String xml = "<region name=\"abc\">" +
            "<property name=\"maxNodes\" value=\"1000\"></property>" +
            "</region>";

      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(1000, config.getMaxNodes());

   }

   public void testXMLParse2() throws Exception
   {
      FIFOAlgorithmConfig config = new FIFOAlgorithmConfig();
      String xml = "<region name=\"abc\">" +
            "</region>";

      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();
      assert config.getMaxNodes() == -1;
      assert config.getMinTimeToLive() == -1;
   }

   public void testXMLParse3() throws Exception
   {
      FIFOAlgorithmConfig config = new FIFOAlgorithmConfig();
      String xml = "<region>" +
            "<property name=\"maxNodes\" value=\"1000\"></property>" +
            "</region>";

      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      try
      {
         EvictionElementParser.parseEvictionPolicyConfig(element, config);
         config.validate();
      }
      catch (ConfigurationException ce)
      {
         assertTrue("Configure Exception properly thrown", true);
      }
   }
}
