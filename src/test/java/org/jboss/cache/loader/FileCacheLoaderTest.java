package org.jboss.cache.loader;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.AfterTest;

@Test(groups = {"functional"}, testName = "loader.FileCacheLoaderTest")
public class FileCacheLoaderTest extends CacheLoaderTestsBase
{
   private String tmpCLLoc = TestingUtil.TEST_FILES + getClass().getName();

   @BeforeTest
   @AfterTest
   public void removeCacheLoaderFolder()
   {
      TestingUtil.recursiveFileRemove(tmpCLLoc);
   }

   protected void configureCache(CacheSPI cache) throws Exception
   {
      cache.getConfiguration().setCacheLoaderConfig(UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", "org.jboss.cache.loader.FileCacheLoader",
            "location=" + tmpCLLoc, false, true, false, false, false));
   }

   public void testIsCharacterPortableLocation()
   {
      FileCacheLoader fcl = new FileCacheLoader();

      Object[][] data = new Object[][]{
            {"C:\\here\\there.txt", true},
            {"/home/here/there", true},
            {"/home/*/jboss", false},
            {"C:\\>/jgroups/jboss", false},
            {"/cache/jboss<", false},
            {"/pojocache|/galder", false},
            {"/pojocache/gal\"der", false}};

      for (Object[] aData : data)
      {
         String path = (String) aData[0];
         boolean expected = (Boolean) aData[1];
         assertEquals(path, expected, fcl.isCharacterPortableLocation(path));
      }
   }

   public void testIsCharacterPortableTree()
   {
      FileCacheLoader fcl = new FileCacheLoader();

      Object[][] data = new Object[][]{
            {Fqn.fromString("/a/b/c/d/e"), true},
            {Fqn.fromString("/a/*/c/d/e"), false},
            {Fqn.fromString("/a/b/>/d/e"), false},
            {Fqn.fromString("/a/</c/d/e"), false},
            {Fqn.fromString("/|/b/c/d/e"), false},
            {Fqn.fromString("/|/b/c/d/e"), false},
            {Fqn.fromString("/a/b/c/d/\""), false},
            {Fqn.fromString("/a/b/c/d/\\"), false},
            {Fqn.fromString("/a/b/c/d///"), true},
            {Fqn.fromString("/a/b/c/:/e"), false},};

      for (Object[] aData : data)
      {
         Fqn fqn = (Fqn) aData[0];
         boolean expected = (Boolean) aData[1];
         assertEquals(fqn.toString(), expected, fcl.isCharacterPortableTree(fqn));
      }
   }

   public void testIsLengthPortablePath()
   {

      // This now always returns true unless we are using a Windows OS older than version 4.0 (Windows 2000/NT)

      FileCacheLoader fcl = new FileCacheLoader();

      Object[][] data = new Object[][]{
            {"C:\\here\\org\\jboss\\cache\\jgroups\\pojocache\\application\\server\\clustering\\portal\\web_services\\org\\jboss\\cache\\jgroups\\pojocache\\application\\server\\clustering\\portal\\webservices\\org\\jboss\\cache\\jgroups\\pojocache\\application\\server\\clustering\\portal\\data.dat", true},
            {"C:\\there\\org\\jboss\\cache\\jgroups\\pojocache\\application\\server\\clustering\\portal\\web_services\\org\\jboss\\cache\\jgroups\\pojocache\\application\\server\\clustering\\portal\\webservices\\org\\jboss\\cache\\jgroups\\pojocache\\application\\server\\clustering\\portal\\data.dat", true},
            {"C:\\deerme\\org\\jboss\\cache\\jgroups\\pojocache\\application\\server\\clustering\\portal\\web_services\\org\\jboss\\cache\\jgroups\\pojocache\\application\\server\\clustering\\portal\\webservices\\org\\jboss\\cache\\jgroups\\pojocache\\application\\server\\clustering\\portal\\data.dat", true}};

      for (Object[] aData : data)
      {
         String path = (String) aData[0];
         boolean expected = (Boolean) aData[1];
         assertEquals(path, expected, fcl.isLengthPortablePath(path));
      }
   }

}
