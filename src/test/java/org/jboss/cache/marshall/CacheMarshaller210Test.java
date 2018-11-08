package org.jboss.cache.marshall;

import org.jboss.cache.Fqn;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.commands.remote.ReplicateCommand;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

@Test(groups = {"functional"}, testName = "marshall.CacheMarshaller210Test")
public class CacheMarshaller210Test extends CacheMarshaller200Test
{
   public CacheMarshaller210Test()
   {
      currentVersion = "2.1.0.GA";
      currentVersionShort = 21;
      expectedMarshallerClass = CacheMarshaller210.class;
   }

   protected void doMapTest(int size) throws Exception
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      Map map = createMap(size);
      Fqn fqn = Fqn.fromString("/my/stuff");
      String key = "key";
      PutKeyValueCommand putCommand = new PutKeyValueCommand(null, fqn, key, map);
      ReplicateCommand replicateCommand = new ReplicateCommand(putCommand);

      byte[] buf = marshaller.objectToByteBuffer(replicateCommand);

      assertEquals(replicateCommand, marshaller.objectFromByteBuffer(buf));
   }

   protected Map createMap(int size)
   {
      Map map = new HashMap(size);
      for (int i = 0; i < size; i++) map.put("key-" + i, "value-" + i);
      return map;
   }

   public void testLargeNumberOfObjectReferences() throws Exception
   {
      doMapTest(500000);
   }

   public void testVInts() throws IOException
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      
      CacheMarshaller210 cm210 = (CacheMarshaller210) marshaller.defaultMarshaller;
      CacheMarshaller200 cm200 = (CacheMarshaller200) marshaller.getMarshaller(20);
      int[] ints = {2, 100, 500, 12000, 20000, 500000, 2000000, Integer.MAX_VALUE};

      for (int i : ints)
      {
         getAndTestSize(cm200, i);
         getAndTestSize(cm210, i);
      }
   }

   public void testVLongs() throws IOException
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;

      CacheMarshaller210 cm210 = (CacheMarshaller210) marshaller.defaultMarshaller;
      CacheMarshaller200 cm200 = (CacheMarshaller200) marshaller.getMarshaller(20);
      long[] ints = {2, 100, 500, 12000, 20000, 500000, 2000000, Integer.MAX_VALUE, (long) Integer.MAX_VALUE + 500000L, Long.MAX_VALUE};

      for (long i : ints)
      {
         getAndTestSize(cm200, i);
         getAndTestSize(cm210, i);
      }
   }

   private int getAndTestSize(CacheMarshaller200 m, int i) throws IOException
   {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      m.writeUnsignedInt(oos, i);
      oos.flush();
      oos.close();
      baos.flush();
      baos.close();
      byte[] bytes = baos.toByteArray();
      int byteL = bytes.length;
      assert i == m.readUnsignedInt(new ObjectInputStream(new ByteArrayInputStream(bytes)));
      return byteL;
   }

   private int getAndTestSize(CacheMarshaller200 m, long i) throws IOException
   {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      m.writeUnsignedLong(oos, i);
      oos.flush();
      oos.close();
      baos.flush();
      baos.close();
      byte[] bytes = baos.toByteArray();
      int byteL = bytes.length;
      assert i == m.readUnsignedLong(new ObjectInputStream(new ByteArrayInputStream(bytes)));
      return byteL;
   }
}
