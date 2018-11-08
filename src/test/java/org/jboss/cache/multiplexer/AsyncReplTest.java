/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.cache.multiplexer;

import org.jboss.cache.Cache;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Repeats the superclass tests, but with the multiplexer enabled.
 *
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7168 $
 */
@Test(groups = {"functional", "jgroups"}, testName = "multiplexer.AsyncReplTest")
public class AsyncReplTest extends org.jboss.cache.replicated.AsyncReplTest
{
   private ThreadLocal<MultiplexerTestHelper> muxHelperTL = new ThreadLocal<MultiplexerTestHelper>();

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      muxHelperTL.set(new MultiplexerTestHelper());

      super.setUp();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      try
      {
         super.tearDown();
      }
      finally
      {
         if (muxHelperTL.get() != null)
         {
            muxHelperTL.get().tearDown();
            muxHelperTL.set(null);
         }
      }
   }

   @Test(enabled = true)
   public void testPutShouldNotReplicateToDifferentCluster()
   {
      super.testPutShouldNotReplicateToDifferentCluster();
   }

   protected void configureMultiplexer(Cache cache) throws Exception
   {
      muxHelperTL.get().configureCacheForMux(cache);
   }

   protected void validateMultiplexer(Cache cache)
   {
      AssertJUnit.assertTrue("Cache is using multiplexer", cache.getConfiguration().isUsingMultiplexer());
   }

   @Override // workaround for JBCACHE-1434
   public void testStateTransfer() {
       super.testStateTransfer();
   }
}
