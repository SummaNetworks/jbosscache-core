package org.jboss.cache.util;

import org.jboss.cache.Cache;
import org.jboss.cache.DataContainer;
import org.jboss.cache.DataContainerImpl;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.invocation.CacheInvocationDelegate;
import org.jboss.cache.util.Caches.ChildSelector;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.jboss.cache.UnitTestCacheFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;


/**
 * Tests {@link Caches}.
 */
@Test(groups = "functional", sequential = true, testName = "util.CachesTest")
public class CachesTest
{
   String a = "a";

   String b = "b";

   String c = "c";

   Cache cache;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = new UnitTestCacheFactory().createCache(getClass());
   }   
   
   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }
   
   public void testSegment()
   {
      Map m = Caches.asPartitionedMap(cache);
      // m.put(a, b);
      testMap(m);
      m.clear();
      int c = 100;
      for (int i = 0; i < c; i++)
      {
         m.put(Integer.toHexString(i), "foo " + i);
      }
      for (int i = 0; i < c; i++)
      {
         assertEquals("foo " + i, m.get(Integer.toHexString(i)));
      }
   }

   public void testAsMap()
   {
      Map m = Caches.asMap(cache);
      testMap(m);
      for (Node n : (Set<Node>) cache.getRoot().getChildren())
      {
         assertEquals("/a", n.getFqn().toString());
         assertEquals(c, n.get("K"));
      }
      m.clear();

      m.put(a, a);
      testCollectionRemove(m.keySet());
      m.put(a, a);
      testCollectionRemove(m.values());
      m.put(a, a);
      testCollectionRemove(m.entrySet());
   }

   private void testCollectionRemove(Collection c)
   {
      Iterator i;
      i = c.iterator();
      assertEquals(true, i.hasNext());
      try
      {
         i.remove();
         fail("no next");
      }
      catch (IllegalStateException e)
      {
      }
      i.next();
      i.remove();
      assertEquals(false, i.hasNext());
      assertEquals("C " + c, 0, c.size());
   }

   public void testAsSimpleSet()
   {
      Set s = Caches.asSimpleSet(cache.getRoot());
      testSet(s);
   }

   private void testSet(Set s)
   {
      assertEquals(0, s.size());
      assertEquals(true, s.add(a));
      assertEquals(false, s.add(a));
      assertEquals(1, s.size());
      assertEquals("[a]", s.toString());
      assertEquals(true, s.contains(a));
      assertEquals(true, s.remove(a));
      assertEquals(false, s.remove(a));
      assertEquals(false, s.contains(a));
      s.add(b);
      s.clear();
      assertEquals(false, s.contains(b));
      s.add(c);
      Iterator i = s.iterator();
      s.add(a);
      s.remove(a); // stable iterator
      assertTrue(i.hasNext());
      assertEquals(c, i.next());
      i.remove();
      assertEquals(false, i.hasNext());
      assertEquals(0, s.size());
      assertEquals(true, s.isEmpty());
   }

   private void testMap(Map m)
   {
      assertEquals(null, m.put(a, b));
      assertEquals(b, m.put(a, c));
      assertEquals("{a=c}", m.toString());
      assertEquals(1, m.size());
      assertEquals("a", m.keySet().iterator().next());
      assertEquals(true, m.containsKey(a));
      assertEquals(true, m.containsValue(c));
      assertEquals(false, m.containsValue(b));
      assertEquals(c, m.remove(a));
      assertEquals(null, m.remove(a));
      assertEquals(0, m.size());
      assertEquals(false, m.keySet().iterator().hasNext());
      m.put(c, a);
      assertEquals(1, m.keySet().size());
      assertEquals(1, m.entrySet().size());
      assertEquals(1, m.values().size());
      Iterator i = m.keySet().iterator();
      m.put(b, b);
      m.remove(b); // stable iterator
      assertEquals(true, i.hasNext());
      assertEquals(c, i.next());
      assertEquals(false, i.hasNext());

      assertEquals(true, m.keySet().contains(c));
      assertEquals(true, m.entrySet().contains(new SimpleImmutableEntry(c, a)));
      assertEquals(true, m.values().contains(a));
      assertEquals(false, m.keySet().contains(a));
      assertEquals(false, m.entrySet().contains(new SimpleImmutableEntry(a, c)));
      assertEquals(false, m.values().contains(c));
      assertEquals(false, m.isEmpty());
      m.clear();
      assertEquals(0, m.size());
      m.put(a, a);
      m.clear();
      assertEquals(0, m.size());
      assertEquals(true, m.isEmpty());
   }

   public void testAsSimpleMap()
   {
      Map m = Caches.asSimpleMap(cache.getRoot());
      testMap(m);
   }

   public void testSelector()
   {
      Map m = Caches.asPartitionedMap(cache.getRoot(), new DepartmentSelector());
      Person f = new Person("Fred", a);
      Person g = new Person("George", b);
      Person h = new Person("Harry", b);
      // associate person with a number
      m.put(f, 42);
      m.put(g, 69);
      m.put(h, 21);
      assertEquals(42, m.get(f));
      assertEquals(69, m.get(g));
   }

   private String printDetails(Cache c)
   {
      DataContainer dc = ((CacheInvocationDelegate) c).getDataContainer();
      return ((DataContainerImpl) dc).printDetails();
   }

   public static class Person
   {
      String name;

      String department;

      public Person(String name, String department)
      {
         super();
         this.name = name;
         this.department = department;
      }

      public String getDepartment()
      {
         return department;
      }

      public String getName()
      {
         return name;
      }
   }

   public static class DepartmentSelector implements ChildSelector<Person>
   {

      public Fqn childName(Person key)
      {
         return Fqn.fromElements(key.getDepartment());
      }

   }
}
