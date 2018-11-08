/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.BeforeClass;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

/**
 * Tests how groups are formed and disbanded
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "jgroups"}, testName = "buddyreplication.Buddy4Nodes2BackupsTest")
public class Buddy4Nodes2BackupsTest extends AbstractNodeBasedBuddyTest
{
   @BeforeClass
   public void createCaches() throws Exception
   {
      caches = createCaches(2, 4, false);
   }

   private Log log = LogFactory.getLog(Buddy4Nodes2BackupsTest.class);
   BuddyFqnTransformer fqnTransformer = new BuddyFqnTransformer();

   public void test2Buddies() throws Exception
   {
      TestingUtil.blockUntilViewsReceived(5000, caches.toArray(new Cache[0]));

      waitForBuddy(caches.get(0), caches.get(1), false);
      waitForBuddy(caches.get(0), caches.get(2), false);

      waitForBuddy(caches.get(1), caches.get(2), false);
      waitForBuddy(caches.get(1), caches.get(3), false);

      waitForBuddy(caches.get(2), caches.get(3), false);
      waitForBuddy(caches.get(2), caches.get(0), false);

      waitForBuddy(caches.get(3), caches.get(0), false);
      waitForBuddy(caches.get(3), caches.get(1), false);
   }


   public void testPutAndRemove2() throws Exception
   {
      String fqn = "/test";
      String backupFqn = "/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + fqnTransformer.getGroupNameFromAddress(caches.get(0).getLocalAddress()) + fqn;

      // put something in cache 1
      assertNoStaleLocks(caches);

      caches.get(0).put(fqn, key, value);

      assertNoStaleLocks(caches);

      // this should be in neither of the other cachePool' "main" trees
      assertEquals(value, caches.get(0).get(fqn, key));
      assertNull("Should be null", caches.get(1).get(fqn, key));
      assertNull("Should be null", caches.get(2).get(fqn, key));
      assertNull("Should be null", caches.get(3).get(fqn, key));

      // check the backup trees
      assertEquals("Buddy should have data in backup tree", value, caches.get(1).get(backupFqn, key));
      assertEquals("Buddy should have data in backup tree", value, caches.get(2).get(backupFqn, key));
      assertNull("Should be null", caches.get(3).get(backupFqn, key));

      assertNoStaleLocks(caches);

      // now remove
      caches.get(0).removeNode(fqn);
      assertNoStaleLocks(caches);

      assertNull("Should be null", caches.get(0).get(fqn, key));
      assertNull("Should be null", caches.get(1).get(fqn, key));
      assertNull("Should be null", caches.get(2).get(fqn, key));
      assertNull("Should be null", caches.get(3).get(fqn, key));

      // check the backup trees
      assertNull("Should be null", caches.get(0).get(backupFqn, key));
      assertNull("Should be null", caches.get(1).get(backupFqn, key));
      assertNull("Should be null", caches.get(2).get(backupFqn, key));
      assertNull("Should be null", caches.get(3).get(backupFqn, key));

      assertNoStaleLocks(caches);
   }
   

   @Test (dependsOnMethods = {"test2Buddies", "testPutAndRemove2"} )
   public void testRemovalFromCluster2Buddies() throws Throwable
{
      assertNoLocks(caches);

      TestingUtil.sleepThread(getSleepTimeout());

      waitForBuddy(caches.get(0), caches.get(1), false);
      waitForBuddy(caches.get(0), caches.get(2), false);

      waitForBuddy(caches.get(1), caches.get(2), false);
      waitForBuddy(caches.get(1), caches.get(3), false);

      waitForBuddy(caches.get(2), caches.get(3), false);
      waitForBuddy(caches.get(2), caches.get(0), false);

      waitForBuddy(caches.get(3), caches.get(0), false);
      waitForBuddy(caches.get(3), caches.get(1), false);

      // now remove a cache from the cluster
      caches.get(1).stop();
      caches.set(1, null);

      TestingUtil.sleepThread(getSleepTimeout());

      // now test new buddy groups
      waitForBuddy(caches.get(0), caches.get(2), false);
      waitForBuddy(caches.get(0), caches.get(3), false);

      waitForBuddy(caches.get(2), caches.get(3), false);
      waitForBuddy(caches.get(2), caches.get(0), false);

      waitForBuddy(caches.get(3), caches.get(0), false);
      waitForBuddy(caches.get(3), caches.get(2), false);
      assertNoLocks(caches);
   }

}
