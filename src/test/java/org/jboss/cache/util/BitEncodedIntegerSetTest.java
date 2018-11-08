package org.jboss.cache.util;

import org.testng.annotations.Test;

import java.util.HashSet;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", testName = "util.BitEncodedIntegerSetTest")
public class BitEncodedIntegerSetTest
{
   public void testLimits()
   {
      BitEncodedIntegerSet set = new BitEncodedIntegerSet();
      set.add(0);
      set.add(1);
      set.add(62);
      set.add(63);

      for (int i = 0; i < 64; i++)
      {
         if (i == 0 || i == 1 || i == 62 || i == 63)
         {
            assert set.contains(i) : "Should contain " + i;
         }
         else
         {
            assert !set.contains(i) : "Should not contain " + i;
         }
      }
   }

   public void testRemoval()
   {
      BitEncodedIntegerSet set = new BitEncodedIntegerSet();
      set.add(0);
      set.add(1);
      set.add(62);
      set.add(63);

      set.remove(0);
      set.remove(63);

      for (int i = 0; i < 64; i++)
      {
         if (i == 1 || i == 62)
         {
            assert set.contains(i);
         }
         else
         {
            assert !set.contains(i);
         }
      }

   }

   public void testAddAll()
   {
      BitEncodedIntegerSet set = new BitEncodedIntegerSet();
      set.add(0);
      set.add(1);
      set.add(62);
      set.add(63);

      for (int i = 0; i < 64; i++)
      {
         if (i == 0 || i == 1 || i == 62 || i == 63)
         {
            assert set.contains(i);
         }
         else
         {
            assert !set.contains(i);
         }
      }

      BitEncodedIntegerSet set2 = new BitEncodedIntegerSet();
      set2.add(0);
      set2.add(1);
      set2.add(44);
      set2.add(55);

      for (int i = 0; i < 64; i++)
      {
         if (i == 0 || i == 1 || i == 44 || i == 55)
         {
            assert set2.contains(i);
         }
         else
         {
            assert !set2.contains(i);
         }
      }

      set.addAll(set2);

      for (int i = 0; i < 64; i++)
      {
         if (i == 0 || i == 1 || i == 62 || i == 63 || i == 44 || i == 55)
         {
            assert set.contains(i) : "Should contain " + i;
         }
         else
         {
            assert !set.contains(i);
         }
      }
   }

   public void testClear()
   {
      BitEncodedIntegerSet set = new BitEncodedIntegerSet();
      set.add(0);
      set.add(1);
      set.add(62);
      set.add(63);

      for (int i = 0; i < 64; i++)
      {
         if (i == 0 || i == 1 || i == 62 || i == 63)
         {
            assert set.contains(i);
         }
         else
         {
            assert !set.contains(i);
         }
      }
      set.clear();

      assert set.isEmpty();
   }

   public void testIsEmpty()
   {
      BitEncodedIntegerSet set = new BitEncodedIntegerSet();
      assert set.isEmpty();

      set.add(1);

      assert !set.isEmpty();

   }

   public void testEquals()
   {
      BitEncodedIntegerSet set1 = new BitEncodedIntegerSet();
      BitEncodedIntegerSet set2 = new BitEncodedIntegerSet();

      assert set1.equals(set2);
      assert set2.equals(set1);

      set1.add(1);

      assert !set1.equals(set2);
      assert !set2.equals(set1);

      set2.add(1);

      assert set1.equals(set2);
      assert set2.equals(set1);

      set2.add(2);

      assert !set1.equals(set2);
      assert !set2.equals(set1);

      assert set1.equals(set1);
      assert !set1.equals(null);
      assert !set1.equals(new HashSet());
   }

   public void testHashCode()
   {
      BitEncodedIntegerSet set = new BitEncodedIntegerSet();

      int hash = set.hashCode();

      assert hash >= Integer.MIN_VALUE;
      assert hash <= Integer.MAX_VALUE;

   }

   public void testToString()
   {
      BitEncodedIntegerSet set = new BitEncodedIntegerSet();

      assert set.toString() != null;

   }

}
