package org.jboss.cache;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests {@link FqnComparator}.
 *
 * @author xenephon
 */
@Test(groups = "unit", testName = "ComparatorTest")
public class ComparatorTest
{
   FqnComparator comp = new FqnComparator();

   public void testSingleCompare()
   {
      Fqn fqn1 = Fqn.fromString("one");
      Fqn fqn2 = Fqn.fromString("two");

      assertTrue(comp.compare(fqn1, fqn2) < 0);
      assertTrue(comp.compare(fqn2, fqn1) > 0);
      assertTrue(comp.compare(fqn1, fqn1) == 0);
      assertTrue(comp.compare(fqn2, fqn2) == 0);
   }

   public void testNullCompare()
   {
      Fqn fqn1 = Fqn.fromList(Collections.emptyList());
      Fqn fqn2 = Fqn.fromList(Collections.emptyList());

      assertTrue(comp.compare(fqn1, fqn2) == 0);
      assertTrue(comp.compare(fqn2, fqn1) == 0);
      assertTrue(comp.compare(fqn1, fqn1) == 0);
      assertTrue(comp.compare(fqn2, fqn2) == 0);
   }

   public void testOneNullCompare()
   {
      Fqn fqn1 = Fqn.fromList(Collections.emptyList());
      List<Object> temp = new ArrayList<Object>();
      temp.add("one");
      Fqn fqn2 = Fqn.fromList(temp);

      assertTrue(comp.compare(fqn1, fqn2) < 0);
      assertTrue(comp.compare(fqn2, fqn1) > 0);
   }

   public void testNotComparableCompare()
   {
      Fqn fqn1 = Fqn.fromList(Collections.emptyList());

      List<Object> temp = new ArrayList<Object>();
      temp.add("one");
      Fqn fqn2 = Fqn.fromList(temp);

      assertTrue(comp.compare(fqn1, fqn2) < 0);
      assertTrue(comp.compare(fqn2, fqn1) > 0);
   }

   public void testMultiChildCompare()
   {

      Fqn fqn1 = Fqn.fromString("/one/two");

      Fqn fqn2 = Fqn.fromString("/one/two/three");

      assertTrue(comp.compare(fqn1, fqn2) < 0);
      assertTrue(comp.compare(fqn2, fqn1) > 0);

      assertTrue(comp.compare(fqn2, fqn2) == 0);

      assertTrue(comp.compare(fqn1, fqn1) == 0);
   }

   public void testMultiNotChildCompare()
   {

      Fqn fqn1 = Fqn.fromString("/one/two");

      Fqn fqn2 = Fqn.fromString("/three/four");

      assertTrue(comp.compare(fqn1, fqn2) < 0);
      assertTrue(comp.compare(fqn2, fqn1) > 0);

      assertTrue(comp.compare(fqn2, fqn2) == 0);

      assertTrue(comp.compare(fqn1, fqn1) == 0);
   }

   public void testPartialMultiNotChildCompare()
   {

      Fqn fqn1 = Fqn.fromString("/one/two");

      Fqn fqn2 = Fqn.fromString("/three");

      assertTrue(comp.compare(fqn1, fqn2) < 0);
      assertTrue(comp.compare(fqn2, fqn1) > 0);

      assertTrue(comp.compare(fqn2, fqn2) == 0);

      assertTrue(comp.compare(fqn1, fqn1) == 0);
   }

   public void testEqualsMultidCompare()
   {

      Fqn fqn1 = Fqn.fromString("/one/two");

      Fqn fqn2 = Fqn.fromString("/one/two");

      assertTrue(comp.compare(fqn1, fqn2) == 0);
      assertTrue(comp.compare(fqn2, fqn1) == 0);

      assertTrue(comp.compare(fqn2, fqn2) == 0);

      assertTrue(comp.compare(fqn1, fqn1) == 0);
   }

   public void testStringIntMultidCompare()
   {
      Fqn fqn1 = Fqn.fromString("/one/two");

      List<Object> temp = new ArrayList<Object>();
      temp.add(1234);
      Fqn fqn2 = Fqn.fromList(temp);

      assertTrue(comp.compare(fqn1, fqn2) > 0);
      assertTrue(comp.compare(fqn2, fqn1) < 0);

      assertTrue(comp.compare(fqn2, fqn2) == 0);

      assertTrue(comp.compare(fqn1, fqn1) == 0);
   }

   public void testOrdinaryObjectCompare()
   {
      Fqn fqn1 = Fqn.fromElements(new XYZ(), new ABC());
      Fqn fqn2 = Fqn.fromElements("XYZ", "ABC");
      Fqn fqn3 = Fqn.fromElements("XYZ", new ABC());

      Fqn fqn4 = Fqn.fromElements("XYZ", new XYZ());

      assertEquals(0, comp.compare(fqn1, fqn2));
      assertEquals(0, comp.compare(fqn1, fqn3));
      assertEquals(0, comp.compare(fqn2, fqn3));
      assertEquals(true, comp.compare(fqn1, fqn4) < 0);
      assertEquals(true, comp.compare(fqn4, fqn1) > 0);
   }

   private static class XYZ
   {
      @Override
      public String toString()
      {
         return "XYZ";
      }
   }

   private static class ABC
   {
      @Override
      public String toString()
      {
         return "ABC";
      }
   }

}
