/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache;


import org.jboss.cache.config.Configuration;
import org.jboss.cache.marshall.CacheMarshaller210;
import org.jboss.cache.marshall.Marshaller;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Tests {@link Fqn}.
 *
 * @author <a href="mailto:bela@jboss.org">Bela Ban</a> May 9, 2003
 * @version $Revision: 7305 $
 */
@Test(groups = "unit", sequential = true, testName = "FqnTest")
public class FqnTest
{
   private Cache<Object, Object> cache;
   private Marshaller marshaller;

   @BeforeTest
   protected void setUp()
   {
      cache = null;
      marshaller = new CacheMarshaller210();
   }

   @AfterTest
   protected void tearDown()
   {
      if (cache != null)
      {
         cache.stop();
         cache = null;
      }
   }

   public void testNull()
   {
      Fqn fqn = Fqn.ROOT;
      assert 0 == fqn.size();
      int hcode = fqn.hashCode();
      assert hcode != -1;
   }

   public void testOne()
   {
      Fqn fqn = Fqn.fromElements(22);
      assert 1 == fqn.size();
      int hcode = fqn.hashCode();
      assert hcode != -1;
   }

   public void testEmptyFqn()
   {
      Fqn f1 = Fqn.ROOT;
      Fqn f2 = Fqn.ROOT;
      assert f1.equals(f2);
   }

   public void testFqn()
   {
      Fqn fqn = Fqn.fromString("/a/b/c");
      assert 3 == fqn.size();

      Fqn fqn2 = Fqn.fromElements("a", "b", "c");
      assert 3 == fqn.size();
      assert fqn.equals(fqn2);
      assert fqn.hashCode() == fqn2.hashCode();
   }

   public void testHereogeneousNames()
   {
      Fqn fqn = Fqn.fromElements("string", 38, true);
      assert 3 == fqn.size();

      Fqn fqn2 = Fqn.fromElements("string", 38, true);
      assert fqn.equals(fqn2);
      assert fqn.hashCode() == fqn2.hashCode();
   }

   public void testHashcode()
   {
      Fqn fqn1, fqn2;
      fqn1 = Fqn.fromElements("a", "b", "c");
      fqn2 = Fqn.fromString("/a/b/c");
      assert fqn1.equals(fqn2);

      HashMap<Fqn, Integer> map = new HashMap<Fqn, Integer>();
      map.put(fqn1, 33);
      map.put(fqn2, 34);
      assert map.size() == 1;
      assert map.get(fqn1).equals(34);
   }

   public void testEquals()
   {
      Fqn fqn1 = Fqn.fromElements("person/test");

      Fqn f1, f2, f3;

      f1 = Fqn.fromRelativeElements(fqn1, "0");
      f2 = Fqn.fromRelativeElements(fqn1, "1");
      f3 = Fqn.fromRelativeElements(fqn1, "2");

      HashMap<Fqn, String> map = new HashMap<Fqn, String>();
      map.put(f1, "0");
      map.put(f2, "1");
      map.put(f3, "2");

      assert map.get(Fqn.fromRelativeElements(fqn1, "0")) != null;
      assert map.get(Fqn.fromRelativeElements(fqn1, "1")) != null;
      assert map.get(Fqn.fromRelativeElements(fqn1, "2")) != null;

   }

   public void testEquals2()
   {
      Fqn f1;
      Fqn f2;
      f1 = Fqn.fromString("/a/b/c");
      f2 = Fqn.fromString("/a/b/c");
      assert f1.equals(f2);

      f2 = Fqn.fromString("/a/b");
      assert !f1.equals(f2);

      f2 = Fqn.fromString("/a/b/c/d");
      assert !f1.equals(f2);
   }

   public void testEquals2WithMarshalling() throws Exception
   {
      Fqn f1, f2;
      f1 = Fqn.fromString("/a/b/c");
      f2 = marshalAndUnmarshal(f1);
      assert f1.equals(f2);
   }

   public void testEquals3()
   {
      Fqn f1;
      Fqn f2;
      f1 = Fqn.fromElements("a", 322649, Boolean.TRUE);
      f2 = Fqn.ROOT;
      assert !f1.equals(f2);
      assert !f2.equals(f1);

      f2 = Fqn.fromString("a/322649/TRUE");
      assert !f1.equals(f2);

      f2 = Fqn.fromElements("a", 322649, Boolean.FALSE);
      assert !f1.equals(f2);

      f2 = Fqn.fromElements("a", 322649, Boolean.TRUE);
      assert f1.equals(f2);
   }

   public void testEquals3WithMarshalling() throws Exception
   {
      Fqn f1, f2;
      f1 = Fqn.fromElements("a", 322649, Boolean.TRUE);
      f2 = marshalAndUnmarshal(f1);
      assert f1.equals(f2);
      assert f2.equals(f1);

      Fqn f3 = Fqn.fromString("a/322649/TRUE");
      f3 = marshalAndUnmarshal(f3);
      assert !f1.equals(f3);

      f2 = Fqn.fromElements("a", 322649, Boolean.FALSE);
      f2 = marshalAndUnmarshal(f2);
      assert !f1.equals(f2);

      f2 = Fqn.fromElements("a", 322649, Boolean.TRUE);
      f2 = marshalAndUnmarshal(f2);
      assert f1.equals(f2);
   }

   public void testEquals4()
   {
      Fqn fqn = Fqn.fromString("X");
      // Check casting
      assert !fqn.equals("X");
      // Check null
      assert !fqn.equals(null);
   }

   public void testNullElements() throws CloneNotSupportedException
   {
      Fqn fqn0 = Fqn.fromElements((Object) null);
      assert 1 == fqn0.size();

      Fqn fqn1 = Fqn.fromElements("NULL", null, 0);
      assert 3 == fqn1.size();

      Fqn fqn2 = Fqn.fromElements("NULL", null, 0);
      assert fqn1.hashCode() == fqn2.hashCode();
      assert fqn1.equals(fqn2);
   }

   public void testIteration()
   {
      Fqn fqn = Fqn.fromString("/a/b/c");
      assert 3 == fqn.size();
      Fqn tmp_fqn = Fqn.ROOT;
      assert 0 == tmp_fqn.size();
      for (int i = 0; i < fqn.size(); i++)
      {
         String s = (String) fqn.get(i);
         tmp_fqn = Fqn.fromRelativeElements(tmp_fqn, s);
         assert tmp_fqn.size() == i + 1;
      }
      assert 3 == tmp_fqn.size();
      assert fqn.equals(tmp_fqn);
   }

   public void testIsChildOf()
   {
      Fqn child = Fqn.fromString("/a/b");
      Fqn parent = Fqn.fromString("/a");
      assert child.isChildOf(parent);
      assert !parent.isChildOf(child);
      assert child.isChildOrEquals(child);

      parent = Fqn.fromString("/a/b/c");
      child = Fqn.fromString("/a/b/c/d/e/f/g/h/e/r/e/r/t/tt/");
      assert child.isChildOf(parent);
   }

   public void testIsChildOf2()
   {
      Fqn child = Fqn.fromString("/a/b/c/d");
      assert "/b/c/d".equals(child.getSubFqn(1, child.size()).toString());
   }

   public void testParentage()
   {
      Fqn fqnRoot = Fqn.ROOT;
      Fqn parent = fqnRoot.getParent();
      assert parent.equals(fqnRoot);

      Fqn fqnOne = Fqn.fromString("/one");
      parent = fqnOne.getParent();
      assert parent.equals(fqnRoot);
      assert fqnOne.isChildOf(parent);

      Fqn fqnTwo = Fqn.fromString("/one/two");
      parent = fqnTwo.getParent();
      assert parent.equals(fqnOne);
      assert fqnTwo.isChildOf(parent);

      Fqn fqnThree = Fqn.fromString("/one/two/three");
      parent = fqnThree.getParent();
      assert parent.equals(fqnTwo);
      assert fqnThree.isChildOf(parent);

   }

   public void testRoot()
   {
      Fqn fqn = Fqn.ROOT;
      assert fqn.isRoot();

      fqn = Fqn.fromString("/one/two");
      assert !fqn.isRoot();

      Fqn f = Fqn.fromString("/");

      assert f.isRoot();
      assert f.equals(Fqn.ROOT);
   }

   public void testGetName()
   {
      Fqn integerFqn = Fqn.fromElements(1);
      assert "1".equals(integerFqn.getLastElementAsString());

      Object object = new Object();
      Fqn objectFqn = Fqn.fromElements(object);
      assert object.toString().equals(objectFqn.getLastElementAsString());
   }

   public void testRemovalNonString() throws Exception
   {
      Fqn f = Fqn.fromElements("test", 1);

      Configuration c = new Configuration();
      c.setCacheMode("LOCAL");
      cache = new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());

      cache.put(f, "key", "value");

      assert "value".equals(cache.get(f, "key"));
      assert cache.getRoot().hasChild(f);

      cache.removeNode(f);

      assert cache.get(f, "key") == null;
      assert !cache.getRoot().hasChild(f);
   }

   Fqn marshalAndUnmarshal(Fqn fqn) throws Exception
   {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(baos);
      marshaller.objectToObjectStream(fqn, out);
      out.close();
      baos.close();
      ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
      return (Fqn) marshaller.objectFromObjectStream(in);
   }

   // testing generics

   public void testSize()
   {
      Fqn f = Fqn.ROOT;
      assert f.size() == 0;
      assert f.isRoot();

      f = Fqn.fromString("/");
      assert f.size() == 0;
      assert f.isRoot();

      f = Fqn.fromString("/hello");
      assert f.size() == 1;
      assert !f.isRoot();
   }

   public void testGenerations()
   {
      Fqn f = Fqn.fromElements(1, 2, 3, 4, 5, 6, 7);

      assert f.equals(f.getAncestor(f.size()));
      assert f.getParent().equals(f.getAncestor(f.size() - 1));
      assert Fqn.ROOT.equals(f.getAncestor(0));
      assert Fqn.fromElements(1).equals(f.getAncestor(1));
      assert Fqn.fromElements(1, 2).equals(f.getAncestor(2));
      assert Fqn.fromElements(1, 2, 3).equals(f.getAncestor(3));
      assert Fqn.fromElements(1, 2, 3, 4).equals(f.getAncestor(4));
      assert Fqn.fromElements(1, 2, 3, 4, 5).equals(f.getAncestor(5));

      try
      {
         f.getAncestor(-1);
         // should fail
         assert false;
      }
      catch (IllegalArgumentException good)
      {
         // expected
      }

      try
      {
         f.getAncestor(f.size() + 1);
         // should fail
         assert false;
      }
      catch (IndexOutOfBoundsException good)
      {
         // expected
      }
   }

   public void testReplacingDirectAncestor()
   {
      Fqn fqn = Fqn.fromString("/a/b/c");
      Fqn newParent = Fqn.fromString("/hot/dog");
      Fqn expectedNewChild = Fqn.fromString("/hot/dog/c");

      assert expectedNewChild.equals(fqn.replaceAncestor(fqn.getParent(), newParent));
   }

   public void testReplacingindirectAncestor()
   {
      Fqn fqn = Fqn.fromString("/a/b/c");
      Fqn newParent = Fqn.fromString("/hot/dog");
      Fqn expectedNewChild = Fqn.fromString("/hot/dog/b/c");

      assert expectedNewChild.equals(fqn.replaceAncestor(fqn.getParent().getParent(), newParent));
   }

   public void testDifferentFactories()
   {
      Fqn[] fqns = new Fqn[6];
      int i = 0;
      fqns[i++] = Fqn.fromString("/a/b/c");
      fqns[i++] = Fqn.fromRelativeElements(Fqn.ROOT, "a", "b", "c");
      fqns[i++] = Fqn.fromElements("a", "b", "c");
      fqns[i++] = Fqn.fromList(Arrays.asList(new Object[]{"a", "b", "c"}));
      fqns[i++] = Fqn.fromRelativeList(Fqn.ROOT, Arrays.asList(new Object[]{"a", "b", "c"}));
      fqns[i] = Fqn.fromRelativeFqn(Fqn.ROOT, Fqn.fromString("/a/b/c"));

      // all of the above should be equal to each other.
      for (i = 0; i < fqns.length; i++)
      {
         for (int j = 0; j < fqns.length; j++)
         {
            assert fqns[i].equals(fqns[j]) : "Error on equals comparing " + i + " and " + j;
            assert fqns[j].equals(fqns[i]) : "Error on equals comparing " + i + " and " + j;
            assert fqns[i].hashCode() == fqns[j].hashCode() : "Error on hashcode comparing " + i + " and " + j;
         }
      }
   }
}
