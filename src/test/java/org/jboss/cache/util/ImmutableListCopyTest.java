package org.jboss.cache.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import org.testng.annotations.Test;

@Test(groups = "unit", testName = "util.ImmutableListCopyTest")
public class ImmutableListCopyTest
{
   public void testImmutability()
   {
      List<String> l = Immutables.immutableListCopy(Collections.singletonList("one"));
      try
      {
         l.add("two");
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.remove(0);
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.clear();
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.add(0, "x");
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.set(0, "i");
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.addAll(Collections.singletonList("l"));
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.addAll(0, Collections.singletonList("l"));
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.removeAll(Collections.singletonList("l"));
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.retainAll(Collections.singletonList("l"));
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.iterator().remove();
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }

      try
      {
         l.listIterator().set("w");
         assert false;
      }
      catch (UnsupportedOperationException good)
      {

      }
   }

   public void testListIterator()
   {
      List<Integer> list = Immutables.immutableListWrap(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

      ListIterator<Integer> li = list.listIterator();

      int number = 1;
      while (li.hasNext()) assert li.next() == number++;
      assert number == 11;

      number = 10;
      li = list.listIterator(list.size());
      while (li.hasPrevious()) assert li.previous() == number--;
      assert number == 0;
   }

   public void testSubLists()
   {
      List<Integer> ints = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
      List<Integer> list = Immutables.immutableListCopy(ints);

      assert ints.subList(2, 5).equals(list.subList(2, 5));
      assert ints.subList(1, 9).equals(list.subList(1, 9));
      assert ints.subList(0, 1).equals(list.subList(0, 1));
   }
   
   static Object copy(Object o) throws Exception {
       ByteArrayOutputStream bo = new ByteArrayOutputStream();
       new ObjectOutputStream(bo).writeObject(o);
       ByteArrayInputStream is = new ByteArrayInputStream(bo.toByteArray());
       return new ObjectInputStream(is).readObject();
   }
   
   public void testSerialization() throws Exception {
       List<Integer> ints = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
       copy( Immutables.immutableListCopy(ints) );
   }
}
