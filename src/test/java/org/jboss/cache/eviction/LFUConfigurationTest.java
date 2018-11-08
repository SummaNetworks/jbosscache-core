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
 * LFU Configuration test.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = "unit", sequential = false, testName = "eviction.LFUConfigurationTest")
public class LFUConfigurationTest
{

   public void testXMLParsing() throws Exception
   {
      LFUAlgorithmConfig config = new LFUAlgorithmConfig();
      String xml =
            "<region name=\"abc\">" +
                  "<property name=\"minNodes\" value=\"10\"></property>" +
                  "<property name=\"maxNodes\" value=\"20\"></property>" +
                  "</region>";

      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(10, config.getMinNodes());
      assertEquals(20, config.getMaxNodes());
   }

   public void testXMLParsing2() throws Exception
   {
      LFUAlgorithmConfig config = new LFUAlgorithmConfig();
      String xml =
            "<region name=\"abc\">" +
                  "<property name=\"minNodes\" value=\"10\"></property>" +
                  "</region>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(10, config.getMinNodes());
      assertEquals(-1, config.getMaxNodes());
   }

   public void testXMLParsing3() throws Exception
   {
      LFUAlgorithmConfig config = new LFUAlgorithmConfig();
      String xml =
            "<region name=\"abc\">" +
                  "<property name=\"maxNodes\" value=\"20\"></property>" +
                  "</region>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(-1, config.getMinNodes());
      assertEquals(20, config.getMaxNodes());

   }
}
