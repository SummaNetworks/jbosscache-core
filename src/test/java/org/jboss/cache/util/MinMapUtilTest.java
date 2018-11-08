package org.jboss.cache.util;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertSame;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = {"functional", "transaction"}, sequential = true, testName = "util.MinMapUtilTest")
public class MinMapUtilTest
{

   private static final Map<String, String> empty = Collections.emptyMap();
   private Map<String, String> map;
   private String key = "a";
   private String key2 = "b";
   private String value = "y";
   
   @BeforeMethod
   public void before() {
      map = empty;
   }

   @AfterMethod
   public void tearDown() {
      map = null;
   }
   
   public void testRemove()
   {
      assertSame(map, MinMapUtil.remove(map, key));
      map = MinMapUtil.put(map, key, value);
      map = MinMapUtil.put(map, key2, value);
      assertEquals(value, map.get(key));
      assertEquals(value, map.get(key2));
      MinMapUtil.remove(map, key2);
      assertSame(map, MinMapUtil.remove(map, "not here"));
      assertSame(empty, MinMapUtil.remove(map, key));
   }
   
   public void testPut()
   {
      assertSame(map, MinMapUtil.remove(map, key));
      map = MinMapUtil.put(map, key, value);
      assertEquals(1, map.size());
      assertEquals(true, map.containsKey(key));
      map = MinMapUtil.put(map, key, value);
      assertEquals(1, map.size());
      assertEquals(true, map.containsKey(key));
      map = MinMapUtil.put(map, key2, value);
      assertEquals(2, map.size());
   }
   
   public void testPutAll()
   {
      HashMap<String, String> hm = new HashMap<String, String>();
      assertSame(empty, MinMapUtil.putAll(map, hm));
      hm.put(key, value);
      assertEquals(1, MinMapUtil.putAll(map, hm).size());
      hm.put(key2, value);
      assertEquals(2, MinMapUtil.putAll(map, hm).size());
   }
}
