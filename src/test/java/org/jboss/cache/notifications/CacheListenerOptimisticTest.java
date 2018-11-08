/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.notifications;

import org.testng.annotations.Test;

@Test(groups = {"functional", "optimistic"}, testName = "notifications.CacheListenerOptimisticTest")
public class CacheListenerOptimisticTest extends CacheListenerTest
{
   public CacheListenerOptimisticTest()
   {
      optLocking = true;
   }
}
