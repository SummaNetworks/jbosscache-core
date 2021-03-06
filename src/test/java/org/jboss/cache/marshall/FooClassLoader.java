package org.jboss.cache.marshall;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FooClassLoader extends ClassLoader
{
   private Class foo;
   private boolean useCachedByteStream = true;

   public FooClassLoader(ClassLoader parent)
   {
      super(parent);
   }

   public Class loadFoo() throws ClassNotFoundException
   {
      if (foo == null)
      {
         byte[] bytes;
         if (useCachedByteStream)
         {
            bytes = getFooClazzAsBytes();
         }
         else
         {
            try
            {
               InputStream is = getResourceAsStream("org/jboss/cache/marshall/Foo.clazz");
               bytes = new byte[1024];
               ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
               int read;
               while ((read = is.read(bytes)) > -1)
               {
                  baos.write(bytes, 0, read);
               }
               bytes = getFooClazzAsBytes();
            }
            catch (IOException e)
            {
               throw new ClassNotFoundException("cannot read org/jboss/cache/marshall/Foo.clazz", e);
            }
         }

         foo = this.defineClass("org.jboss.cache.marshall.Foo", bytes, 0, bytes.length);
      }
      return foo;
   }

   private byte[] getFooClazzAsBytes()
   {
      // GENERATED using main() method to read org/jboss/cache/marshall/Foo.clazz into a byte[]
      // so that this byte stream is available even if Foo.clazz is not included in the test classpath by Maven
      // Copy out this generated snippet into FooClassLoader.java and use this byte[] instead of
      // trying to read Foo.clazz off the classpath.

      return new byte[]{
              -54, -2, -70, -66, 0, 0, 0, 46, 0, 61, 7, 0, 2, 1, 0, 28, 111, 114, 103, 47, 106, 98, 111, 115, 115, 47, 99, 97, 99, 104,
              101, 47, 109, 97, 114, 115, 104, 97, 108, 108, 47, 70, 111, 111, 7, 0, 4, 1, 0, 16, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47,
              79, 98, 106, 101, 99, 116, 7, 0, 6, 1, 0, 20, 106, 97, 118, 97, 47, 105, 111, 47, 83, 101, 114, 105, 97, 108, 105, 122, 97, 98,
              108, 101, 1, 0, 16, 115, 101, 114, 105, 97, 108, 86, 101, 114, 115, 105, 111, 110, 85, 73, 68, 1, 0, 1, 74, 1, 0, 13, 67, 111,
              110, 115, 116, 97, 110, 116, 86, 97, 108, 117, 101, 5, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 6, 60, 105, 110, 105, 116, 62, 1,
              0, 3, 40, 41, 86, 1, 0, 4, 67, 111, 100, 101, 10, 0, 3, 0, 16, 12, 0, 12, 0, 13, 1, 0, 15, 76, 105, 110, 101, 78,
              117, 109, 98, 101, 114, 84, 97, 98, 108, 101, 1, 0, 18, 76, 111, 99, 97, 108, 86, 97, 114, 105, 97, 98, 108, 101, 84, 97, 98, 108,
              101, 1, 0, 4, 116, 104, 105, 115, 1, 0, 30, 76, 111, 114, 103, 47, 106, 98, 111, 115, 115, 47, 99, 97, 99, 104, 101, 47, 109, 97,
              114, 115, 104, 97, 108, 108, 47, 70, 111, 111, 59, 1, 0, 6, 101, 113, 117, 97, 108, 115, 1, 0, 21, 40, 76, 106, 97, 118, 97, 47,
              108, 97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 59, 41, 90, 1, 0, 3, 111, 98, 106, 1, 0, 18, 76, 106, 97, 118, 97, 47, 108,
              97, 110, 103, 47, 79, 98, 106, 101, 99, 116, 59, 1, 0, 8, 104, 97, 115, 104, 67, 111, 100, 101, 1, 0, 3, 40, 41, 73, 1, 0,
              8, 116, 111, 83, 116, 114, 105, 110, 103, 1, 0, 20, 40, 41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110,
              103, 59, 7, 0, 30, 1, 0, 26, 106, 97, 118, 97, 47, 115, 101, 99, 117, 114, 105, 116, 121, 47, 83, 101, 99, 117, 114, 101, 82, 97,
              110, 100, 111, 109, 10, 0, 29, 0, 16, 7, 0, 33, 1, 0, 22, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110,
              103, 66, 117, 102, 102, 101, 114, 8, 0, 35, 1, 0, 36, 111, 114, 103, 46, 106, 98, 111, 115, 115, 46, 99, 97, 99, 104, 101, 46, 109,
              97, 114, 115, 104, 97, 108, 108, 46, 70, 111, 111, 91, 114, 97, 110, 100, 111, 109, 61, 10, 0, 32, 0, 37, 12, 0, 12, 0, 38, 1,
              0, 21, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 59, 41, 86, 10, 0, 40, 0, 42, 7, 0,
              41, 1, 0, 16, 106, 97, 118, 97, 47, 117, 116, 105, 108, 47, 82, 97, 110, 100, 111, 109, 12, 0, 43, 0, 26, 1, 0, 7, 110, 101,
              120, 116, 73, 110, 116, 10, 0, 32, 0, 45, 12, 0, 46, 0, 47, 1, 0, 6, 97, 112, 112, 101, 110, 100, 1, 0, 27, 40, 73, 41,
              76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 66, 117, 102, 102, 101, 114, 59, 8, 0, 49, 1, 0, 1,
              93, 10, 0, 32, 0, 51, 12, 0, 46, 0, 52, 1, 0, 44, 40, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105,
              110, 103, 59, 41, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114, 105, 110, 103, 66, 117, 102, 102, 101, 114, 59, 10, 0,
              32, 0, 54, 12, 0, 27, 0, 28, 1, 0, 6, 114, 97, 110, 100, 111, 109, 1, 0, 18, 76, 106, 97, 118, 97, 47, 117, 116, 105, 108,
              47, 82, 97, 110, 100, 111, 109, 59, 1, 0, 2, 115, 98, 1, 0, 24, 76, 106, 97, 118, 97, 47, 108, 97, 110, 103, 47, 83, 116, 114,
              105, 110, 103, 66, 117, 102, 102, 101, 114, 59, 1, 0, 10, 83, 111, 117, 114, 99, 101, 70, 105, 108, 101, 1, 0, 8, 70, 111, 111, 46,
              106, 97, 118, 97, 0, 33, 0, 1, 0, 3, 0, 1, 0, 5, 0, 1, 0, 26, 0, 7, 0, 8, 0, 1, 0, 9, 0, 0, 0, 2,
              0, 10, 0, 4, 0, 1, 0, 12, 0, 13, 0, 1, 0, 14, 0, 0, 0, 47, 0, 1, 0, 1, 0, 0, 0, 5, 42, -73, 0, 15,
              -79, 0, 0, 0, 2, 0, 17, 0, 0, 0, 6, 0, 1, 0, 0, 0, 15, 0, 18, 0, 0, 0, 12, 0, 1, 0, 0, 0, 5, 0,
              19, 0, 20, 0, 0, 0, 1, 0, 21, 0, 22, 0, 1, 0, 14, 0, 0, 0, 57, 0, 1, 0, 2, 0, 0, 0, 5, 43, -63, 0,
              1, -84, 0, 0, 0, 2, 0, 17, 0, 0, 0, 6, 0, 1, 0, 0, 0, 22, 0, 18, 0, 0, 0, 22, 0, 2, 0, 0, 0, 5,
              0, 19, 0, 20, 0, 0, 0, 0, 0, 5, 0, 23, 0, 24, 0, 1, 0, 1, 0, 25, 0, 26, 0, 1, 0, 14, 0, 0, 0, 44,
              0, 1, 0, 1, 0, 0, 0, 2, 4, -84, 0, 0, 0, 2, 0, 17, 0, 0, 0, 6, 0, 1, 0, 0, 0, 27, 0, 18, 0, 0,
              0, 12, 0, 1, 0, 0, 0, 2, 0, 19, 0, 20, 0, 0, 0, 1, 0, 27, 0, 28, 0, 1, 0, 14, 0, 0, 0, 111, 0, 3,
              0, 3, 0, 0, 0, 37, -69, 0, 29, 89, -73, 0, 31, 76, -69, 0, 32, 89, 18, 34, -73, 0, 36, 77, 44, 43, -74, 0, 39, -74,
              0, 44, 18, 48, -74, 0, 50, 87, 44, -74, 0, 53, -80, 0, 0, 0, 2, 0, 17, 0, 0, 0, 18, 0, 4, 0, 0, 0, 32, 0,
              8, 0, 33, 0, 18, 0, 34, 0, 32, 0, 35, 0, 18, 0, 0, 0, 32, 0, 3, 0, 0, 0, 37, 0, 19, 0, 20, 0, 0, 0,
              8, 0, 29, 0, 55, 0, 56, 0, 1, 0, 18, 0, 19, 0, 57, 0, 58, 0, 2, 0, 1, 0, 59, 0, 0, 0, 2, 0, 60,};
   }

/*   public static void main(String[] args) throws Exception
   {
      InputStream is = FooClassLoader.class.getClassLoader().getResourceAsStream("org/jboss/cache/marshall/Foo.clazz");
      byte[] bytes = new byte[1024];
      ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
      int read;
      while ((read = is.read(bytes)) > -1)
      {
         baos.write(bytes, 0, read);
      }
      bytes = baos.toByteArray();

      int i=0;
      for (byte b : bytes)
      {
         i++;
         System.out.print(b);
         System.out.print(", ");
      }
   }*/
}
