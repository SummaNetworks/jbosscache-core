package org.jboss.cache.config.parsing;

import org.testng.annotations.Test;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests that all the xml file used within tests are correct with respect to the schema definition.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "functional", testName = "config.parsing.XmlConfigurationSchemaTest")
public class XmlConfigurationSchemaTest
{
   public static final String BASE_DIR_FOR_CONFIG = "./configs";

   private String[] testFiles =
         {
               "buddy-replication-cache.xml",
               "clonable-config.xml",
               "local-lru-eviction.xml",
               "local-passivation.xml",
               "local-tx.xml",
               "mixedPolicy-eviction.xml",
               "mux.xml",
               "parser-test.xml",
               "parser-test-async.xml",
               "policyPerRegion-eviction.xml",
               "replSync.xml",
               "string-property-replaced.xml",
               "mvcc-repl-sync-br.xml"
         };

   /**
    * Simple test to prove that validation works.
    */
   public void testSimpleFile()
   {
      ExceptionCountingErrorHandler handler = new ExceptionCountingErrorHandler();
      System.setProperty("jbosscache.config.schemaLocation", "src/main/resources/jbosscache-config-3.1.xsd");
      XmlConfigurationParser parser = new XmlConfigurationParser(handler);
      for (String file : testFiles)
      {
         parser.parseFile(BASE_DIR_FOR_CONFIG + File.separator + file);
         for (Exception e : handler.exceptionList) e.printStackTrace();
         assert handler.noErrors() : "error during parsing (file " + file + ")";
      }
   }

   /**
    * Test that when the jbc.config.validation is set to true the parser is not validating by default.
    */
   public void testValidationDisbaledOnSystemProperty()
   {
      XmlConfigurationParser parser = new XmlConfigurationParser();
      assert parser.isValidating() : "by default we have a validating parser";

      System.setProperty(RootElementBuilder.VALIDATING_SYSTEM_PROPERTY, "false");
      parser = new XmlConfigurationParser();
      assert !parser.isValidating();

      System.setProperty(RootElementBuilder.VALIDATING_SYSTEM_PROPERTY, "true");
      parser = new XmlConfigurationParser();
      assert parser.isValidating();
   }

   public static class ExceptionCountingErrorHandler implements ErrorHandler
   {
      List<SAXParseException> exceptionList = new ArrayList<SAXParseException>();

      public void warning(SAXParseException exception) throws SAXException
      {
         handleDefault(exception);
      }

      public void error(SAXParseException exception) throws SAXException
      {
         handleDefault(exception);
      }

      public void fatalError(SAXParseException exception) throws SAXException
      {
         handleDefault(exception);
      }

      private void handleDefault(SAXParseException exception)
      {
         exceptionList.add(exception);
      }

      public boolean noErrors()
      {
         return exceptionList.isEmpty();
      }
   }
}
