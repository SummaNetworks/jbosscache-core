package org.jboss.cache.notifications;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.buddyreplication.BuddyReplicationTestsBase;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", testName = "notifications.BuddyGroupChangeNotificationTest")
public class BuddyGroupChangeNotificationTest extends BuddyReplicationTestsBase
{
   Cache c1, c2, c3;
   static boolean stage2 = false;
   static boolean notificationsReceived = true;

   @BeforeMethod
   public void setUp() throws CloneNotSupportedException
   {
      UnitTestCacheFactory cf = new UnitTestCacheFactory<Object, Object>();
      Configuration conf = new Configuration();
      conf.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      conf.setBuddyReplicationConfig(brc);

      c1 = cf.createCache(conf, false, getClass());
      c2 = cf.createCache(conf.clone(), false, getClass());
      c3 = cf.createCache(conf.clone(), false, getClass());

      c1.start();
      c2.start();
      c3.start();

      // make sure views are received and groups are formed first
      TestingUtil.blockUntilViewsReceived(60000, c1, c2, c3);
   }

   @AfterMethod
   public void tearDown() throws Exception
   {
      super.tearDown();
      TestingUtil.killCaches(c1, c2, c3);
      c1 = null;
      c2 = null;
      c3 = null;
   }

   @Test(timeOut = 60000)
   public void testChangingGroups() throws Exception
   {
      // initial state
      waitForBuddy(c1, c2, true, 60000);
      waitForBuddy(c2, c3, true, 60000);
      waitForBuddy(c3, c1, true, 60000);

      // kill c3
      c3.stop();

      waitForBuddy(c1, c2, true, 60000);
      waitForBuddy(c2, c1, true, 60000);

      stage2 = true;
      c3.start();

      waitForBuddy(c1, c2, true, 60000);
      waitForBuddy(c2, c3, true, 60000);
      waitForBuddy(c3, c1, true, 60000);

      assert notificationsReceived;
   }
}
