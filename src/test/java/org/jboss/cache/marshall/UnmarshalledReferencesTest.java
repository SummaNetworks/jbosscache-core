package org.jboss.cache.marshall;

import org.testng.annotations.Test;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

/**
 * Tester for <code>UnmarshalledReferences</code>. 
 *
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = {"functional"}, testName = "marshall.UnmarshalledReferencesTest")
public class UnmarshalledReferencesTest
{
   public void testSimpleGetPut()
   {
      UnmarshalledReferences refs = new UnmarshalledReferences();
      for (int i = 0; i < 100; i++)
      {
         refs.putReferencedObject(i, String.valueOf(i));
      }
      for (int i = 0; i < 100; i++)
      {
         assertEquals(refs.getReferencedObject(i), String.valueOf(i));
      }
   }

   public void testPutWithGap()
   {
      UnmarshalledReferences refs = new UnmarshalledReferences();
      refs.putReferencedObject(0, "0");
      refs.putReferencedObject(2, "2");
      assertEquals(refs.getReferencedObject(0), "0");
      assertNull(refs.getReferencedObject(1));
      assertEquals(refs.getReferencedObject(2), "2");
   }

   public void testPutBefore()
   {
      UnmarshalledReferences refs = new UnmarshalledReferences();
      refs.putReferencedObject(2, "2");
      refs.putReferencedObject(3, "3");

      //when adding this make sure other positions are not shifted
      refs.putReferencedObject(1, "1");

      assertNull(refs.getReferencedObject(0));
      assertEquals("1", refs.getReferencedObject(1));
      assertEquals("2", refs.getReferencedObject(2));
      assertEquals("3", refs.getReferencedObject(3));
   }
}
