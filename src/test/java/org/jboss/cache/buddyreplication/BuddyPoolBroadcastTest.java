/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheSPI;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests basic group membership semantics
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = "functional", testName = "buddyreplication.BuddyPoolBroadcastTest")
public class BuddyPoolBroadcastTest extends BuddyReplicationTestsBase
{
   private Log log = LogFactory.getLog(BuddyPoolBroadcastTest.class);


   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      super.tearDown();
   }

   public void test2CachesWithPoolNames() throws Exception
   {
      List<CacheSPI<Object, Object>> caches = createCaches(2, true);
      cachesTL.set(caches);

      BuddyManager m = caches.get(0).getBuddyManager();
      Map groupMap = m.buddyPool;

      assertEquals("A", groupMap.get(caches.get(0).getLocalAddress()));
      assertEquals("B", groupMap.get(caches.get(1).getLocalAddress()));
   }
}
