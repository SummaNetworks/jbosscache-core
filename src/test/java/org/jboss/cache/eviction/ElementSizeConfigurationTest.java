/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.element.EvictionElementParser;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

/**
 * @author Daniel Huang
 * @version $Revision: 7168 $
 */
@Test(groups = "unit", sequential = false, testName = "eviction.ElementSizeConfigurationTest")
public class ElementSizeConfigurationTest
{
   public void testXMLParse1() throws Exception
   {
      ElementSizeAlgorithmConfig config = new ElementSizeAlgorithmConfig();
      String xml = "<region name=\"abc\">" +
            "<property name=\"maxNodes\" value=\"1000\"></property>" +
            "<property name=\"maxElementsPerNode\" value=\"100\"></property>" +
            "</region>";

      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);
      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(100, config.getMaxElementsPerNode());
      assertEquals(1000, config.getMaxNodes());
   }


   public void testXMLParse2() throws Exception
   {
      ElementSizeAlgorithmConfig config = new ElementSizeAlgorithmConfig();
      String xml = "<region name=\"abc\">" +
            "<property name=\"maxNodes\" value=\"1000\"></property>" +
            "</region>";

      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);
      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();
      assert config.getMaxElementsPerNode() == -1;
   }


   public void testXMLParse3() throws Exception
   {
      ElementSizeAlgorithmConfig config = new ElementSizeAlgorithmConfig();
      String xml = "<region name=\"abc\">" +
            "<property name=\"maxElementsPerNode\" value=\"100\"></property>" +
            "</region>";

      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(100, config.getMaxElementsPerNode());
      assertEquals(-1, config.getMaxNodes());
   }

}
