/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.cache.lock;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "lock.LockParentRootFlagTest")
public class LockParentRootFlagTest
{
   // to test https://jira.jboss.org/jira/browse/JBCACHE-1420

   public void testMVCCSet()
   {
      doTest(NodeLockingScheme.MVCC, true);
   }

   public void testMVCCUnset()
   {
      doTest(NodeLockingScheme.MVCC, false);
   }

   public void testPessimisticSet()
   {
      doTest(NodeLockingScheme.PESSIMISTIC, true);
   }

   public void testPessimisticUnset()
   {
      doTest(NodeLockingScheme.PESSIMISTIC, false);
   }

   public void testOptimisticSet()
   {
      doTest(NodeLockingScheme.OPTIMISTIC, true);
   }

   public void testOptimisticUnset()
   {
      doTest(NodeLockingScheme.OPTIMISTIC, false);
   }


   private void doTest(NodeLockingScheme nls, boolean set)
   {
      Cache c = null;
      try
      {
         c = new UnitTestCacheFactory().createCache(false, getClass());
         c.getConfiguration().setNodeLockingScheme(nls);
         c.getConfiguration().setLockParentForChildInsertRemove(set);
         if (nls.isVersionedScheme())
            c.getConfiguration().setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
         c.start();
         assert c.getRoot().isLockForChildInsertRemove() == set;
      }
      finally
      {
         TestingUtil.killCaches(c);
      }
   }
}
