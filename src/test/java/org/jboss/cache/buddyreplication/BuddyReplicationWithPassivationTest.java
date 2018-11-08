/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = "functional", testName = "buddyreplication.BuddyReplicationWithPassivationTest")
public class BuddyReplicationWithPassivationTest extends BuddyReplicationWithCacheLoaderTest
{
   public BuddyReplicationWithPassivationTest()
   {
      passivation = true;
   }
}
