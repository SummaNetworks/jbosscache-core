package org.jboss.cache.util;

import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

@Test(groups = "unit", testName = "util.FastCopyHashMapTest")
public class FastCopyHashMapTest
{
   public void testSerialization() throws Exception
   {
      Map map = new FastCopyHashMap();
      map.put("k1", "v1");
      map.put("k2", "v2");

      Map map2 = serializeAndDeserialize(map);

      assert map2 instanceof FastCopyHashMap;
      assert map2.size() == map.size();
      for (Object key : map.keySet()) assert map2.containsKey(key);
   }

   public void testNonexistentKey() throws Exception
   {
      Map map = new FastCopyHashMap();
      map.put("k1", "v1");
      map.put("k2", "v2");

      assert map.get("dont exist") == null;
   }

   public void testNonexistentKeyDeserialized() throws Exception
   {
      Map map = new FastCopyHashMap();
      map.put("k1", "v1");
      map.put("k2", "v2");
      Map map2 = serializeAndDeserialize(map);
      assert map2.get("dont exist") == null;
   }

   private <T> T serializeAndDeserialize(T object) throws Exception
   {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      oos.writeObject(object);
      oos.close();
      baos.close();

      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
      Object retval = ois.readObject();
      ois.close();
      return (T) retval;
   }
}
