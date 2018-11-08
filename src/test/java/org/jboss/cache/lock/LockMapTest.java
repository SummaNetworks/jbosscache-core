/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.lock;

import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Ben Wang
 */
@Test(groups = {"functional"}, sequential = true, testName = "lock.LockMapTest")
public class LockMapTest
{
   private LockMap map_;

   /**
    * Constructor for LockMapTest.
    *
    * @param arg0
    */

   @BeforeMethod(alwaysRun = true)
   protected void setUp() throws Exception
   {
      map_ = new LockMap();
   }

   @AfterMethod(alwaysRun = true)
   protected void tearDown() throws Exception
   {
      map_.removeAll();
   }

   final public void testIsOwner()
   {
      map_.addReader(this);
      assertTrue(map_.isOwner(this, LockMap.OWNER_READ));
      map_.setWriterIfNotNull(this);
      assertTrue(map_.isOwner(this, LockMap.OWNER_WRITE));
      assertTrue(map_.isOwner(this, LockMap.OWNER_ANY));
      map_.removeAll();
   }

   final public void testAddReader()
   {
      map_.addReader(this);
      assertTrue(map_.isOwner(this, LockMap.OWNER_READ));
      map_.removeReader(this);
   }

   final public void testAddWriter()
   {
      map_.setWriterIfNotNull(this);
      assertTrue(map_.writerOwner().equals(this));
      map_.removeWriter();
   }
}
