/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.UnknownHostException;

import org.jboss.cache.transaction.GlobalTransaction;
import org.jgroups.stack.IpAddress;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:bela@jboss.org">Bela Ban</a> Apr 14, 2003
 * @version $Id: GlobalTransactionTest.java 7305 2008-12-12 08:49:20Z mircea.markus $
 */
public class GlobalTransactionTest
{

   @Test(groups = {"functional"}, testName = "GlobalTransactionTest")
   public void testEquality() throws UnknownHostException
   {
      IpAddress a1 = new IpAddress("localhost", 4444);
      GlobalTransaction tx1, tx2;

      tx1 = GlobalTransaction.create(a1);
      tx2 = GlobalTransaction.create(a1);

      assertTrue(tx1.equals(tx2) == false);

      tx2 = tx1;
      assertTrue(tx1.equals(tx2));

   }
   
   @Test(groups = {"functional"}, testName = "GlobalTransactionTest")
   public void testEqualityWithOtherObject() throws UnknownHostException
   {
      IpAddress a1 = new IpAddress("localhost", 4444);
      GlobalTransaction tx1 = GlobalTransaction.create(a1);
      assertFalse(tx1.equals(Thread.currentThread()));
   }

   @Test(groups = {"functional"}, testName = "GlobalTransactionTest")
   public void testEqualityWithNull() throws UnknownHostException
   {
      IpAddress a1 = new IpAddress("localhost", 4444);
      GlobalTransaction tx1 = GlobalTransaction.create(a1);
      assertFalse(tx1.equals(null));
   }

   @Test(groups = {"functional"}, testName = "GlobalTransactionTest")
   public void testHashcode() throws UnknownHostException
   {
      IpAddress a1 = new IpAddress("localhost", 4444);
      GlobalTransaction tx1, tx2;


      tx1 = GlobalTransaction.create(a1);
      tx2 = GlobalTransaction.create(a1);

      assertTrue(tx1.equals(tx2) == false);

      int hcode_1 = tx1.hashCode();
      int hcode_2 = tx2.hashCode();
      assertFalse(hcode_1 == hcode_2);

      tx2 = tx1;
      assertTrue(tx1.equals(tx2));
      hcode_1 = tx1.hashCode();
      hcode_2 = tx2.hashCode();
      assertEquals(hcode_1, hcode_2);
   }

   @Test(groups = {"functional"}, testName = "GlobalTransactionTest")
   public void testExternalization() throws Exception
   {
      IpAddress a1 = new IpAddress("localhost", 4444);
      IpAddress a2 = new IpAddress("localhost", 5555);
      GlobalTransaction tx1, tx2, tx1_copy = null, tx2_copy = null;
      ByteArrayOutputStream bos = null;
      ByteArrayInputStream bis = null;
      ObjectOutputStream out = null;
      ObjectInputStream in = null;
      byte[] buf = null;

      tx1 = GlobalTransaction.create(a1);
      tx2 = GlobalTransaction.create(a2);

      bos = new ByteArrayOutputStream(1024);
      out = new ObjectOutputStream(bos);
      out.writeObject(tx1);
      out.writeObject(tx2);
      out.flush();
      buf = bos.toByteArray();

      bis = new ByteArrayInputStream(buf);
      in = new ObjectInputStream(bis);
      tx1_copy = (GlobalTransaction) in.readObject();
      tx2_copy = (GlobalTransaction) in.readObject();

      assertNotNull(tx1_copy);
      assertNotNull(tx2_copy);
      assertEquals(tx1, tx1_copy);
      assertEquals(tx2, tx2_copy);

      int hcode_1 = tx1.hashCode();
      int hcode_2 = tx2.hashCode();
      int hcode_3 = tx1_copy.hashCode();
      int hcode_4 = tx2_copy.hashCode();
      assertFalse(hcode_1 == hcode_2);
      assertFalse(hcode_3 == hcode_4);
      assertEquals(hcode_1, hcode_3);
      assertEquals(hcode_2, hcode_4);
   }


   @Test(groups = {"functional"}, testName = "GlobalTransactionTest")
   public void testWithNullAddress()
   {
      GlobalTransaction tx1, tx2, tmp_tx1;

      tx1 = GlobalTransaction.create(null);
      tx2 = GlobalTransaction.create(null);

      tmp_tx1 = tx1;
      assertEquals(tx1, tmp_tx1);
      assertTrue(tx1.equals(tx2) == false);
   }

   @Test(groups = {"functional"}, testName = "GlobalTransactionTest")
   public void testOneNullAddress() throws UnknownHostException
   {
      GlobalTransaction tx1, tx2;
      tx1 = GlobalTransaction.create(null);

      assertFalse(tx1.equals(null));

      tx2 = GlobalTransaction.create(null);

      assertFalse(tx1.equals(tx2));
      assertFalse(tx2.equals(tx1));

      IpAddress a1 = new IpAddress("localhost", 4444);
      tx2 = GlobalTransaction.create(a1);

      assertFalse(tx1.equals(tx2));
      assertFalse(tx2.equals(tx1));
   }
}
