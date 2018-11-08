package org.jboss.cache;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import org.testng.annotations.Test;

@Test(groups = {"functional"}, testName = "VersionConversionTest")
public class VersionConversionTest
{
   public void testStringToShort()
   {
      try
      {
         Version.getVersionShort("1.2.4SP1");
         fail("Correctly did not accept versionString '1.2.4SP1'");
      }
      catch (IllegalArgumentException ok) {}

      try
      {
         Version.getVersionShort("1.2.4 SP1");
         fail("Correctly did not accept versionString '1.2.4 SP1'");
      }
      catch (IllegalArgumentException ok) {}

      try
      {
         Version.getVersionShort("1.3.alpha");
         fail("Correctly did not accept versionString '1.3.alpha'");
      }
      catch (IllegalArgumentException ok) {}

      assertEquals("MAX_SHORT correct", Short.MAX_VALUE, Version.getVersionShort("15.31.63"));
      assertEquals("0.0.1 correct", 1, Version.getVersionShort("0.0.1"));
      assertEquals("0.1.0 correct", (short) Math.pow(2, 6), Version.getVersionShort("0.1.0"));
      assertEquals("1.0 correct", (short) Math.pow(2, 11), Version.getVersionShort("1.0"));
      assertEquals("1.0.1 correct", (short) Math.pow(2, 11) + 1, Version.getVersionShort("1.0.1"));
      assertEquals("1.1 correct", (short) (Math.pow(2,11) + Math.pow(2,6)), Version.getVersionShort("1.1"));
      assertEquals("1.1.1 correct", (short) (Math.pow(2,11) + Math.pow(2,6)) + 1, Version.getVersionShort("1.1.1"));
      assertEquals("2.0 correct", (short) Math.pow(2,12), Version.getVersionShort("2.0"));

      // Ignore final qualifiers
      assertEquals("1.3.0.alpha correct", (short) (Math.pow(2,11) + Math.pow(2,7) + Math.pow(2,6)), Version.getVersionShort("1.3.0.alpha"));
      assertEquals("1.3.0.RC1 correct", (short) (Math.pow(2,11) + Math.pow(2,7) + Math.pow(2,6)), Version.getVersionShort("1.3.0.RC1"));
      assertEquals("1.3.0.SP1 correct", (short) (Math.pow(2,11) + Math.pow(2,7) + Math.pow(2,6)), Version.getVersionShort("1.3.0.SP1"));

      // Special cases
      assertEquals("1.2.4.SP2 correct", (short) 124, Version.getVersionShort("1.2.4"));
      assertEquals("1.2.4.SP2 correct", (short) 1241, Version.getVersionShort("1.2.4.SP1"));
      assertEquals("1.2.4.SP2 correct", (short) (Math.pow(2,11) + Math.pow(2,7)) + 4, Version.getVersionShort("1.2.4.SP2"));
   }

   public void testShortToString()
   {
      assertEquals("0.0.1 correct", "0.0.1", Version.getVersionString(Version.getVersionShort("0.0.1")));
      assertEquals("1.3.0 correct", "1.3.0", Version.getVersionString(Version.getVersionShort("1.3.0")));

      // Special cases
      assertEquals("1.2.4 correct", "1.2.4", Version.getVersionString((short) 124));
      assertEquals("1.2.4.SP1 correct", "1.2.4.SP1", Version.getVersionString((short) 1241));
      assertEquals("1.2.4.SP2 correct", "1.2.4.SP2", Version.getVersionString(Version.getVersionShort("1.2.4.SP2")));
   }

   public void testDefault()
   {
      short defaultShort = Version.getVersionShort();
      String versionString = Version.getVersionString(defaultShort);
      // only compare the main version string.
      String versionToCompareAgainst = Version.version;
      versionToCompareAgainst = versionToCompareAgainst.replaceAll("[\\-\\.]\\w+$", "");

      assertEquals("Round-trip conversion consistent", versionToCompareAgainst, versionString);
   }
}
