/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.eviction;

import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.element.EvictionElementParser;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

/**
 * Unit tests for NullEvictionPolicyConfig.
 *
 * @author Daniel Huang (dhuang@jboss.org)
 * @version $Revision: 7168 $
 */
@Test(groups = "unit", sequential = false, testName = "eviction.NullEvictionConfigTest")
public class NullEvictionConfigTest
{
   /**
    * Creates a bunch of region elements with LRU configs and confirms
    * that NullEvictionPolicyConfig doesn't barf.
    *
    * @throws Exception
    */
   public void testXMLParsing() throws Exception
   {
      String xml =
            "<region name=\"/org/jboss/data\">\n" +
                  "</region>";

      testConfigBlock(xml);

      xml = "<region name=\"/maxAgeTest/\"/>\n";

      testConfigBlock(xml);
   }

   private void testConfigBlock(String xml) throws Exception
   {
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);
      NullEvictionAlgorithmConfig config = new NullEvictionAlgorithmConfig();
      EvictionElementParser.parseEvictionPolicyConfig(element, config);
      config.validate();
   }
}
