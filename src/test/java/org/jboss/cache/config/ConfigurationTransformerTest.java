package org.jboss.cache.config;

import org.jboss.cache.config.parsing.ConfigFilesConvertor;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.config.parsing.XmlConfigurationParser2x;
import org.jboss.cache.eviction.LRUAlgorithmConfig;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * Test how xsl for migrating config files from 2.x to 3.x works.
 * For each existing config file in 2.x it does the following:
 * <ol>
 * <li> it transforms it into an 3.x file using the xslt transformer
 * <li> it parses the file with 2.x parser
 * <li> it parses the transform with a 3.x parser
 * <li> checks that the two resulting <tt>Configuration</tt> objects are equal.
 * </ol>
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "unit", testName = "config.ConfigurationTransformerTest")
public class ConfigurationTransformerTest
{
   public static final String XSLT_FILE = "config2to3.xslt";
   private static final String BASE_DIR = "configs/conf2x";
   ConfigFilesConvertor convertor = new ConfigFilesConvertor();

   /**
    * Useful when {@link testEqualityOnTransformedFiles} fails and you need to isolate a failure.
    */
   public void testSingleFile() throws Exception
   {
      String fileName = getFileName("/policyPerRegion-eviction.xml");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      convertor.parse(fileName, baos, XSLT_FILE);

      XmlConfigurationParser newParser = new XmlConfigurationParser();
      XmlConfigurationParser2x oldParser = new XmlConfigurationParser2x();

      Configuration newConfig = newParser.parseStream(new ByteArrayInputStream(baos.toByteArray()));
      Configuration oldConfig = oldParser.parseFile(fileName);

      assert oldConfig.equals(newConfig);
   }

   public void testEqualityOnTransformedFiles() throws Exception
   {
      String[] fileNames = {
            "buddy-replication-cache.xml",
            "local-cache.xml",
            "multiplexer-enabled-cache.xml",
            "total-replication-cache.xml",
      };
      for (String file : fileNames)
      {
         String fileName = getFileName(file);
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         convertor.parse(fileName, baos, XSLT_FILE);

         XmlConfigurationParser newParser = new XmlConfigurationParser();
         XmlConfigurationParser2x oldParser = new XmlConfigurationParser2x();

         Configuration newConfig = newParser.parseStream(new ByteArrayInputStream(baos.toByteArray()));
         Configuration oldConfig = oldParser.parseFile(fileName);

         assert newConfig.equals(oldConfig);
      }
   }

   /**
    * Not like the rest of elements, eviction was also changed in 3.x.
    * As the parser produces different results, we semantically check here that eviction is transformed corectly.
    */
   public void testEqualityOnEvictionTransformedFiles() throws Exception
   {
      String[] fileNames = {
            "cacheloader-enabled-cache.xml",
            "clonable-config.xml",
            "default-test-config2x.xml",
            "eviction-enabled-cache.xml",
            "optimistically-locked-cache.xml",
            "policyPerRegion-eviction.xml",
      };
      for (String file : fileNames)
      {
         String fileName = getFileName(file);
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         convertor.parse(fileName, baos, XSLT_FILE);

         XmlConfigurationParser newParser = new XmlConfigurationParser();
         XmlConfigurationParser2x oldParser = new XmlConfigurationParser2x();

         Configuration newConfig = newParser.parseStream(new ByteArrayInputStream(baos.toByteArray()));
         Configuration oldConfig = oldParser.parseFile(fileName);

         assert newConfig.equals(oldConfig);
      }
   }

   public void testUnlimitedValues() throws Exception
   {
      // in 3.x, unlimited values in eviction are denoted by -1 and not 0!
      String fileName = getFileName("/zeroTTL.xml");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      convertor.parse(fileName, baos, XSLT_FILE);

      XmlConfigurationParser newParser = new XmlConfigurationParser();
      XmlConfigurationParser2x oldParser = new XmlConfigurationParser2x();

      Configuration newConfig = newParser.parseStream(new ByteArrayInputStream(baos.toByteArray()));
      Configuration oldConfig = oldParser.parseFile(fileName);

      for (EvictionRegionConfig erc : oldConfig.getEvictionConfig().getEvictionRegionConfigs())
      {
         correctUnlimitedValues(erc);
      }
      correctUnlimitedValues(oldConfig.getEvictionConfig().getDefaultEvictionRegionConfig());

      assert oldConfig.equals(newConfig);
   }

   private void correctUnlimitedValues(EvictionRegionConfig erc)
   {
      LRUAlgorithmConfig eac = (LRUAlgorithmConfig) erc.getEvictionAlgorithmConfig();
      if (eac.getMaxAge() <= 0) eac.setMaxAge(-1);
      if (eac.getMaxNodes() <= 0) eac.setMaxNodes(-1);
      if (eac.getMinTimeToLive() <= 0) eac.setMinTimeToLive(-1);
      if (eac.getTimeToLive() <= 0) eac.setTimeToLive(-1);
   }

   private String getFileName(String s)
   {
      return BASE_DIR + File.separator + s;
   }
}
