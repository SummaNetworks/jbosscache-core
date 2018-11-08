/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.Properties;

import org.testng.annotations.Test;

/**
 * Unit test class for FileCacheLoaderConfig
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
@Test(groups={"functional"}, testName = "loader.FileCacheLoaderConfigTest")
public class FileCacheLoaderConfigTest
{
   private FileCacheLoaderConfig fclc = new FileCacheLoaderConfig();

   public void testSetProperties()
   {
      fclc.setProperties((Properties)null);
      assertTrue(fclc.isCheckCharacterPortability());
      assertNull(fclc.getLocation());

      Properties p = new Properties();
      p.setProperty("location", "any");
      fclc.setProperties(p);
      assertTrue(fclc.isCheckCharacterPortability());
      assertEquals("any", fclc.getLocation());

      p.clear();

      p.setProperty("check.character.portability", "true");
      fclc.setProperties(p);
      assertTrue(fclc.isCheckCharacterPortability());
      assertNull(fclc.getLocation());

      p.clear();

      p.setProperty("check.character.portability", "false");
      fclc.setProperties(p);
      assertFalse(fclc.isCheckCharacterPortability());
      assertNull(fclc.getLocation());
   }
}
