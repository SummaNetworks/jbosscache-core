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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

/**
 * Unit tests for MRUConfiguration.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = "unit", sequential = true, testName = "eviction.MRUConfigurationTest")
public class MRUConfigurationTest
{
   MRUAlgorithmConfig config = null;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      config = new MRUAlgorithmConfig();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      config = null;
   }
   
   public void testXMLParsing() throws Exception
   {
      String xml =
            "<region name=\"/org/jboss/data\">\n" +
                  "<property name=\"maxNodes\" value=\"5000\"></property>\n" +
                  "</region>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(5000, config.getMaxNodes());
   }

   public void testXMLParsing2() throws Exception
   {
      String xml = "<region name=\"/Test/\">\n" +
            "<property name=\"maxNodes\" value=\"10000\"></property>\n" +
            "</region>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(10000, config.getMaxNodes());
   }

   public void testXMLParsing3() throws Exception
   {
      String xml = "<region name=\"/Test/\">\n" +
            "</region>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);
         EvictionElementParser.parseEvictionPolicyConfig(element, config);
         config.validate();

      assert config.getMaxNodes() == -1;

      xml = "<region name=\"/Test/\">\n" +
            "<property name=\"maxNodes\" value=\"10000\"></property>\n" +
            "</region>";


      element = XmlConfigHelper.stringToElementInCoreNS(xml);

      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();

      assertEquals(10000, config.getMaxNodes());
   }

}
