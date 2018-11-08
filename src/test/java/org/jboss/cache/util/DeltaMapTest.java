package org.jboss.cache.util;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = {"functional", "transaction"}, sequential = true, testName = "util.DeltaMapTest")
public class DeltaMapTest
{

   static String Y = "y";

   static String Z = "z";

   static String K = "k";

   HashMap<String, String> hm;
   
   HashMap<String, String> backup;

   DeltaMap<String, String> dm;
   
   @BeforeMethod
   public void setUp()
   {
      hm = new HashMap<String, String>();
      hm.put(null, null);
      hm.put(Y, Z);
      hm.put(K, Y);
      backup = new HashMap<String, String>(hm);
      dm = DeltaMap.create(hm);
      assertEquals(false, dm.isModified());
      assertEquals(hm, dm);
   }

   @AfterMethod
   public void tearDown()
   {
      hm = null;
      backup = null;
      dm = null;
   }
   
   public void testSize()
   {
      assertEquals(3, dm.size());
      dm.put(Y, "HI");
      assertEquals(3, dm.size());
      dm.remove(Y);
      assertEquals(2, dm.size());
      hm.clear();
      assertEquals(0, dm.size());
      dm.put(Z, Z);
      dm.getRemoved().add("NOT HERE");
      assertEquals(1, dm.size());
   }
   
   public void testConcurrent() throws Exception
   {
      ConcurrentHashMap<Object, String> m = new ConcurrentHashMap<Object, String>();
      m.put(new Object(), Z);
      m.put(new Object(), Y);
      DeltaMap<Object, String> dm = DeltaMap.create(m);
      assertEquals(m, dm);
      assertEquals(m.toString(), dm.toString());
   }
   
   public void testChanges() throws Exception
   {
      assertEquals("" + dm.toDebugString(), backup, dm);
      assertEquals(Z, dm.remove(Y));
      assertEquals(true, dm.isModified());
      assertEquals(null, dm.remove(Y));
      assertEquals("changes not made to underlying map", backup, hm);
      assertEquals(false, dm.containsKey(Y));
      assertEquals(false, dm.containsValue(Z));
      assertEquals(null, dm.put(Y, Z));
      assertEquals(Z, dm.put(Y, Z));
      assertEquals("changes not made to underlying map", backup, hm);
      assertEquals(backup.size(), dm.size());
      assertEquals(backup, dm);
      dm.commit();
      assertEquals(hm, dm);
      dm.commit();
      assertEquals(hm, dm);
   }

   public void testAddRemove() throws Exception
   {
      dm.remove(K);
      dm.put(K, Z);
      assertEquals(Z, dm.get(K));
      assertEquals(Z, dm.remove(K));
      assertEquals(null, dm.remove(K));
   }

   public void testExclude() throws Exception
   {
       dm = DeltaMap.excludeKeys(hm, Y);
       assertEquals(false, dm.containsKey(Y));
   }
   
   public void testExclude2() throws Exception
   {
       dm = DeltaMap.excludeKeys(hm, hm.keySet());
       assertEquals(true, dm.isModified());
       assertEquals(0, dm.size());
   }
   
   public void testClearedMap() throws Exception
   {
      dm.clear();
      assertEquals(0, dm.size());
      assertEquals(backup, hm);
      assertEquals(null, dm.remove(Y));
   }
   
   public void testIterator() throws Exception
   {
      dm.remove(null);
      dm.put(K, Y);
      Iterator<Entry<String, String>> i = dm.entrySet().iterator();
      assertEquals(true, i.hasNext());
      assertEquals(true, i.hasNext());
      i.next();
      assertEquals(true, i.hasNext());
      i.next();
      assertEquals("" + dm, false, i.hasNext());
      try
      {
         i.next();
         fail("no next");
      }
      catch (NoSuchElementException e)
      {
      }
      try
      {
         i.next();
         fail("no next");
      }
      catch (NoSuchElementException e)
      {
      }
   }
   
   public void testEx() {
HashMap<String, String> hm = new HashMap<String, String>();
hm.put("a", "apple");
DeltaMap<String, String> dm = DeltaMap.create(hm);
dm.remove("a");
assert hm.containsKey("a");
assert !dm.containsKey("a");
dm.commit();
assert !hm.containsKey("a");
   }
}
