package org.jboss.cache.marshall;

import org.jboss.cache.Fqn;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

@Test(groups = {"functional"}, testName = "marshall.CacheMarshaller300Test")
public class CacheMarshaller300Test
{
   public void testArrayTypes() throws Exception
   {
      Marshaller m = new CacheMarshaller300();
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bout);
      byte[] s = {1, 2, 3, 4};
      m.objectToObjectStream(s, out);
      out.close();

      ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bin);

      Object o = m.objectFromObjectStream(ois);

      ois.close();

      assert o instanceof byte[];
      byte[] oS = (byte[]) o;
      assert oS.length == 4;
      assert oS[0] == 1;
      assert oS[1] == 2;
      assert oS[2] == 3;
      assert oS[3] == 4;
   }

   public void testBoxedArrayTypes() throws Exception
   {
      Marshaller m = new CacheMarshaller300();
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bout);
      Byte[] s = new Byte[]{1, 2, 3, 4};
      m.objectToObjectStream(s, out);
      out.close();

      ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bin);

      Object o = m.objectFromObjectStream(ois);

      ois.close();

      assert o instanceof Byte[];
      Byte[] oS = (Byte[]) o;
      assert oS.length == 4;
      assert oS[0] == 1;
      assert oS[1] == 2;
      assert oS[2] == 3;
      assert oS[3] == 4;
   }

   public void testMixedArrayTypes() throws Exception
   {
      Marshaller m = new CacheMarshaller300();
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bout);
      Object[] s = {"Hello", Fqn.fromString("/a"), 1, null};
      m.objectToObjectStream(s, out);
      out.close();

      ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
      ObjectInputStream ois = new ObjectInputStream(bin);

      Object o = m.objectFromObjectStream(ois);

      ois.close();

      assert o instanceof Object[];
      Object[] oS = (Object[]) o;
      assert oS.length == 4;
      assert oS[0].equals("Hello");
      assert oS[1].equals(Fqn.fromString("/a"));
      assert oS[2].equals(1);
      assert oS[3] == null;

   }
}
