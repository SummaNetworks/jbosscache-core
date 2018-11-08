package org.jboss.cache.testng.other;

import org.testng.annotations.Test;
import org.jboss.cache.testng.Base;

/**                                
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = "functional" , testName = "testng.other.Derived")
public class Derived extends Base
{

   public void testDerivedCcccc()
   {
      System.out.println(getThreadName() + "ccccccccccccccccc");
   }

   public void testDerivedDdddd()
   {
      System.out.println(getThreadName() + "dddddddddddddddddddd");
   }

   protected String getThreadName()
   {
      return "[" + getClass() + " ************ -> " + Thread.currentThread().getName() + "] ";
   }
}
