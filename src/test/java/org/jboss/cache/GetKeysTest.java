/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache;

import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import org.testng.annotations.AfterMethod;

/**
 * @author <a href="mailto:bela@jboss.org">Bela Ban</a>
 * @version $Id: GetKeysTest.java 7305 2008-12-12 08:49:20Z mircea.markus $
 */

@Test(groups = {"functional"}, sequential = true, testName = "GetKeysTest")
public class GetKeysTest
{
   CacheSPI<Object, Object> cache;

   @AfterMethod
   public void tearDown()
   {
      if (cache != null) {
         cache.stop();
         cache.destroy();
         cache = null;
      }
   }
   
   @Test(groups = {"functional"})
   public void testGetKeys() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(getClass());
      cache.put("/a/b/c", "name", "Bela Ban");
      cache.put("/a/b/c", "age", 40);
      cache.put("/a/b/c", "city", "Kreuzlingen");

      Set keys = cache.getNode("/a/b/c").getKeys();
      assertNotNull(keys);
      assertEquals(3, keys.size());

      ByteArrayOutputStream outstream = new ByteArrayOutputStream(20);
      ObjectOutputStream out = new ObjectOutputStream(outstream);
      out.writeObject(keys);// must be serializable      
   }

   @Test(groups = {"functional"})
   public void testGetChildren() throws Exception
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(getClass());
      cache.put("/a/b/c", null);
      cache.put("/a/b/c/1", null);
      cache.put("/a/b/c/2", null);
      cache.put("/a/b/c/3", null);

      Set children = cache.getNode("/a/b/c").getChildrenNames();
      assertNotNull(children);
      assertEquals(3, children.size());

      ByteArrayOutputStream outstream = new ByteArrayOutputStream(20);
      ObjectOutputStream out = new ObjectOutputStream(outstream);
      out.writeObject(children);// must be serializable
   }

   @Test(groups = {"functional"})
   public void testGetKeysOnNode()
   {
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(getClass());
      cache.put("/a/b/c", "key", "value");
      Node<Object, Object> node = cache.getRoot().getChild(Fqn.fromString("/a/b/c"));
      Set<Object> keySet = node.getKeys();
      try
      {

         keySet.add("asd");
         fail();
      }
      catch (Exception e)
      {
         //expected
      }
   }
}
