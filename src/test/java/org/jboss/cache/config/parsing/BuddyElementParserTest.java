package org.jboss.cache.config.parsing;

import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.parsing.element.BuddyElementParser;
import org.jboss.cache.buddyreplication.NextMemberBuddyLocator;

/**
 * Tester class for {@link org.jboss.cache.config.parsing.element.BuddyElementParser}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "unit", sequential = true, testName = "config.parsing.BuddyElementParserTest")
public class BuddyElementParserTest
{

   /** one instance per all tests as it is stateless */
   BuddyElementParser parser = new BuddyElementParser();


   /**
    * Test default values for unspecified elements.
    */
   public void testDefaultValues() throws Exception
   {
      String xmlConfig = "<buddyReplication enabled=\"true\"/>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xmlConfig);
      BuddyReplicationConfig brConfig = parser.parseBuddyElement(element);
      assert brConfig.getBuddyLocatorConfig().getClassName().equals(NextMemberBuddyLocator.class.getName()) : "default buddy locator class is NextMemberBuddyLocator";
      assert brConfig.getBuddyLocatorConfig().getBuddyLocatorProperties().isEmpty();
      assert brConfig.isDataGravitationRemoveOnFind() : "default to true";
      assert brConfig.isDataGravitationSearchBackupTrees() : "default to true";
      assert brConfig.isAutoDataGravitation() : "default to false";
   }

   /**
    * If NextMemberBuddyLocator is set as buddy locator, but no params are being specified for it, make sure that
    * default values for numBudies and ignoreColocatedBuddies are present.
    */
   public void testDefaultParamsForNextMemberBuddyLocator() throws Exception
   {
      String xmlConfig =
            "   <buddyReplication enabled=\"true\" poolName=\"groupOne\">\n" +
            "      <locator>\n" +
            "         <properties>\n" +
            "            numBuddies = 3\n" +
            "         </properties>\n" +
            "      </locator>\n" +
            "   </buddyReplication>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xmlConfig);
      BuddyReplicationConfig brConfig = parser.parseBuddyElement(element);
      assert brConfig.getBuddyLocatorConfig().getClassName().equals(NextMemberBuddyLocator.class.getName()) : "default buddy locator class is NextMemberBuddyLocator";
      assert brConfig.getBuddyLocatorConfig().getBuddyLocatorProperties().get("numBuddies").equals("3");
      assert brConfig.getBuddyLocatorConfig().getBuddyLocatorProperties().size() == 1;
   }

   public void testNormalConfig() throws Exception
   {
      String xmlConfig =
            "   <buddyReplication enabled=\"true\" poolName=\"groupOne\">\n" +
            "      <locator>\n" +
            "         <properties>\n" +
            "            numBuddies = 3\n" +
            "         </properties>\n" +
            "      </locator>\n" +
            "   </buddyReplication>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xmlConfig);
      BuddyReplicationConfig brConfig = parser.parseBuddyElement(element);
      assert brConfig.isEnabled();
      assert brConfig.getBuddyPoolName().equals("groupOne");
      assert brConfig.getBuddyLocatorConfig().getBuddyLocatorProperties().get("numBuddies").equals("3");
   }
}
