package org.jboss.cache.config.parsing;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.ConfigurationException;
import org.testng.annotations.Test;

@Test(groups = "unit", testName = "config.parsing.ParseFileWith30SchemaTest")
public class ParseFileWith30SchemaTest
{
   public void testFileWithDeclared30Schema()
   {
      String fileToTest = "configs/simple-3_0-config-file.xml";
      XmlConfigurationParser parser = new XmlConfigurationParser();
      assert parser.isValidating();
      Configuration c = parser.parseFile(fileToTest);
      assert c.getLockAcquisitionTimeout() == 500;
   }

   @Test (expectedExceptions = ConfigurationException.class)
   public void testFileWithDeclared30SchemaWith31Elements()
   {
      String fileToTest = "configs/incorrect-3_0-config-file.xml";
      XmlConfigurationParser parser = new XmlConfigurationParser();
      assert parser.isValidating();
      Configuration c = parser.parseFile(fileToTest);
      assert false : "Should throw exception!";
   }
}
