/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.statetransfer;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.buddyreplication.BuddyFqnTransformer;
import org.jboss.cache.buddyreplication.BuddyManager;
import org.jboss.cache.buddyreplication.BuddyReplicationTestsBase;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.marshall.MarshalledValue;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

/**
 * Tests that state transfer works properly if the version is 2.0.0.GA.
 *
 * @author <a href="mailto://brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7646 $
 */
@Test(groups = {"functional"}, testName = "statetransfer.StateTransfer200Test")
public class StateTransfer200Test extends StateTransferTestBase
{
   protected String getReplicationVersion()
   {
      return "2.0.0.GA";
   }

   public void testBuddyBackupExclusion() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCacheWithBr("testBuddyBackupExclusion_1");

      Fqn backup = Fqn.fromRelativeElements(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN, "test");
      cache1.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      cache1.put(backup, "name", JOE);
      cache1.put(A_B, "age", TWENTY);

      CacheSPI<Object, Object> cache2 = createCacheWithBr("StateTransferTestBase_2");
      // Pause to give caches time to see each other
      BuddyReplicationTestsBase.waitForSingleBuddy(cache1, cache2);
      assertNull("_buddy_backup_ not transferred", cache2.get(backup, "test"));
      assertEquals("Correct age for /a/b", TWENTY, cache2.get(A_B, "age"));
   }

   public void testBuddyIntegration() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCacheWithBr("testBuddyIntegration_1");

      // cache 1 won't have a buddy at this stage.
      // put some state in cache 1
      cache1.put(A_B, "name", JOE);
      cache1.put(A_C, "name", JANE);

      // now start up cache 2
      CacheSPI<Object, Object> cache2 = createCacheWithBr("testBuddyIntegration_2");

      // Pause to give caches time to see each other
      BuddyReplicationTestsBase.waitForSingleBuddy(cache1, cache2);

      BuddyFqnTransformer fqnTransformer = new BuddyFqnTransformer();
      // now peek into cache 2 to check that this state has been transferred into the backup subtree
      Fqn test = fqnTransformer.getBackupFqn(cache1.getLocalAddress(), A_B);
      assertEquals("/a/b state should have integrated in backup region " + test, JOE, cache2.get(test, "name"));

      test = fqnTransformer.getBackupFqn(cache1.getLocalAddress(), A_C);
      assertEquals("/a/c state should have integrated in backup region " + test, JANE, cache2.get(test, "name"));
   }

   @SuppressWarnings("null")
   public void testCacheLoaderFailure() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCache(false, false, CorruptedFileCacheLoader.class.getName(), false, true, true);

      cache1.put(A_B, "name", JOE);
      cache1.put(A_B, "age", TWENTY);
      cache1.put(A_C, "name", BOB);
      cache1.put(A_C, "age", FORTY);

      CacheSPI cache2 = null;
      try
      {
         cache2 = createCache(false, false, true, false, false, true);
         cache2.start();

         //Vladimir  October 5th 2007
         //failure of integration of persistent state is not considered to be fatal
         //to revisit with Manik
         //fail("Should have caused an exception");
      }
      catch (Exception e)
      {
      }

      //when persistent transfer fails as in this case state recipient cacheloader should be wiped clean
      assertFalse("/a/b is not in cache loader ", cache2.getCacheLoaderManager().getCacheLoader().exists(A_B));
   }

   public void testLoadEntireStateAfterStart() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCache(false, true, true);

      createAndActivateRegion(cache1, Fqn.ROOT);

      cache1.put(A_B, "name", JOE);
      cache1.put(A_B, "age", TWENTY);
      cache1.put(A_C, "name", BOB);
      cache1.put(A_C, "age", FORTY);

      CacheSPI<Object, Object> cache2 = createCache(false, true, true);

      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{cache1, cache2}, 60000);

      CacheLoader loader = cache2.getCacheLoaderManager().getCacheLoader();

      assertNull("/a/b transferred to loader against policy", loader.get(A_B));

      assertNull("/a/b name transferred against policy", cache2.get(A_B, "name"));
      assertNull("/a/b age transferred against policy", cache2.get(A_B, "age"));
      assertNull("/a/c name transferred against policy", cache2.get(A_C, "name"));
      assertNull("/a/c age transferred against policy", cache2.get(A_C, "age"));

      createAndActivateRegion(cache2, Fqn.ROOT);

      assertEquals("Incorrect name from loader for /a/b", JOE, loader.get(A_B).get("name"));
      assertEquals("Incorrect age from loader for /a/b", TWENTY, loader.get(A_B).get("age"));
      assertEquals("Incorrect name from loader for /a/c", BOB, loader.get(A_C).get("name"));
      assertEquals("Incorrect age from loader for /a/c", FORTY, loader.get(A_C).get("age"));

      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache2.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache2.get(A_C, "age"));
   }


   public void testInitialStateTransfer() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCache(false, false, false);

      cache1.put(A_B, "name", JOE);
      cache1.put(A_B, "age", TWENTY);
      cache1.put(A_C, "name", BOB);
      cache1.put(A_C, "age", FORTY);

      CacheSPI<Object, Object> cache2 = createCache(false, false, false);

      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{cache1, cache2}, 60000);

      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache2.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache2.get(A_C, "age"));
   }

   public void testInitialStateTferWithLoader() throws Exception
   {
      initialStateTferWithLoaderTest(false);
   }

   public void testInitialStateTferWithAsyncLoader() throws Exception
   {
      initialStateTferWithLoaderTest(true);
   }

   protected void initialStateTferWithLoaderTest(boolean asyncLoader) throws Exception
   {
      initialStateTferWithLoaderTest(getDefaultCacheLoader(), getDefaultCacheLoader() , asyncLoader);
   }

   public void testPartialStateTransfer() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCache(false, true, false);

      createAndActivateRegion(cache1, A);

      cache1.put(A_B, "name", JOE);
      cache1.put(A_B, "age", TWENTY);
      cache1.put(A_C, "name", BOB);
      cache1.put(A_C, "age", FORTY);

      CacheSPI<Object, Object> cache2 = createCache(false, true, false);

      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{cache1, cache2}, 60000);

      assertNull("/a/b name transferred against policy", cache2.get(A_B, "name"));
      assertNull("/a/b age transferred against policy", cache2.get(A_B, "age"));
      assertNull("/a/c name transferred against policy", cache2.get(A_C, "name"));
      assertNull("/a/c age transferred against policy", cache2.get(A_C, "age"));

      createAndActivateRegion(cache2, A_B);

      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertNull("/a/c name transferred against policy", cache2.get(A_C, "name"));
      assertNull("/a/c age transferred against policy", cache2.get(A_C, "age"));

      cache1.put(A_D, "name", JANE);

      assertNull("/a/d name transferred against policy", cache2.get(A_D, "name"));

      createAndActivateRegion(cache2, A_C);

      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache2.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache2.get(A_C, "age"));
      assertNull("/a/d name transferred against policy", cache2.get(A_D, "name"));

      createAndActivateRegion(cache2, A_D);

      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache2.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache2.get(A_C, "age"));
      assertEquals("Incorrect name for /a/d", JANE, cache2.get(A_D, "name"));


      cache1.getRegion(A, false).deactivate();
      createAndActivateRegion(cache1, A_B);
      createAndActivateRegion(cache1, A_C);
      createAndActivateRegion(cache1, A_D);

      assertEquals("Incorrect name for /a/b", JOE, cache1.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache1.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache1.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache1.get(A_C, "age"));
      assertEquals("Incorrect name for /a/d", JANE, cache1.get(A_D, "name"));

   }

   public void testLocksAndStateTransfer() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCache(false, true, false);
      createAndActivateRegion(cache1, A);
      cache1.put(A_B, "name", JOE);
      CacheSPI<Object, Object> cache2 = createCache(false, true, false);
      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{cache1, cache2}, 60000);
      createAndActivateRegion(cache2, A_B);
      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));

      assert cache1.getNumberOfLocksHeld() == 0;
      cache1.getRegion(A, false).deactivate();
      assert cache1.getNumberOfLocksHeld() == 0;
      createAndActivateRegion(cache1, A_B);
      assertEquals("Incorrect name for /a/b", JOE, cache1.get(A_B, "name"));
   }


   public void testPartialStateTferWithLoader() throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCache(false, true, true);

      createAndActivateRegion(cache1, A);

      cache1.put(A_B, "name", JOE);
      cache1.put(A_B, "age", TWENTY);
      cache1.put(A_C, "name", BOB);
      cache1.put(A_C, "age", FORTY);

      CacheSPI<Object, Object> cache2 = createCache(false, true, true);

      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{cache1, cache2}, 60000);

      CacheLoader loader = cache2.getCacheLoaderManager().getCacheLoader();

      assertNull("/a/b transferred to loader against policy", loader.get(A_B));

      assertNull("/a/b name transferred against policy", cache2.get(A_B, "name"));
      assertNull("/a/b age transferred against policy", cache2.get(A_B, "age"));
      assertNull("/a/c name transferred against policy", cache2.get(A_C, "name"));
      assertNull("/a/c age transferred against policy", cache2.get(A_C, "age"));

      createAndActivateRegion(cache2, A_B);

      assertEquals("Incorrect name from loader for /a/b", JOE, loader.get(A_B).get("name"));
      assertEquals("Incorrect age from loader for /a/b", TWENTY, loader.get(A_B).get("age"));
      assertNull("/a/c transferred to loader against policy", loader.get(A_C));

      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertNull("/a/c name transferred against policy", cache2.get(A_C, "name"));
      assertNull("/a/c age transferred against policy", cache2.get(A_C, "age"));

      cache1.put(A_D, "name", JANE);

      assertNull("/a/d name transferred against policy", cache2.get(A_D, "name"));

      createAndActivateRegion(cache2, A_C);

      assertEquals("Incorrect name from loader for /a/b", JOE, loader.get(A_B).get("name"));
      assertEquals("Incorrect age from loader for /a/b", TWENTY, loader.get(A_B).get("age"));
      assertEquals("Incorrect name from loader for /a/c", BOB, loader.get(A_C).get("name"));
      assertEquals("Incorrect age from loader for /a/c", FORTY, loader.get(A_C).get("age"));

      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache2.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache2.get(A_C, "age"));
      assertNull("/a/d name transferred against policy", cache2.get(A_D, "name"));

      createAndActivateRegion(cache2, A_D);

      assertEquals("Incorrect name from loader for /a/b", JOE, loader.get(A_B).get("name"));
      assertEquals("Incorrect age from loader for /a/b", TWENTY, loader.get(A_B).get("age"));
      assertEquals("Incorrect name from loader for /a/c", BOB, loader.get(A_C).get("name"));
      assertEquals("Incorrect age from loader for /a/c", FORTY, loader.get(A_C).get("age"));
      assertEquals("Incorrect name from loader for /a/d", JANE, loader.get(A_D).get("name"));

      assertEquals("Incorrect name for /a/b", JOE, cache2.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache2.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache2.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache2.get(A_C, "age"));
      assertEquals("Incorrect name for /a/d", JANE, cache2.get(A_D, "name"));

      cache1.getRegion(A, false).deactivate();

      createAndActivateRegion(cache1, A_B);
      createAndActivateRegion(cache1, A_C);
      createAndActivateRegion(cache1, A_D);

      loader = cache1.getCacheLoaderManager().getCacheLoader();

      assertEquals("Incorrect name from loader for /a/b", JOE, loader.get(A_B).get("name"));
      assertEquals("Incorrect age from loader for /a/b", TWENTY, loader.get(A_B).get("age"));
      assertEquals("Incorrect name from loader for /a/c", BOB, loader.get(A_C).get("name"));
      assertEquals("Incorrect age from loader for /a/c", FORTY, loader.get(A_C).get("age"));
      assertEquals("Incorrect name from loader for /a/d", JANE, loader.get(A_D).get("name"));

      assertEquals("Incorrect name for /a/b", JOE, cache1.get(A_B, "name"));
      assertEquals("Incorrect age for /a/b", TWENTY, cache1.get(A_B, "age"));
      assertEquals("Incorrect name for /a/c", BOB, cache1.get(A_C, "name"));
      assertEquals("Incorrect age for /a/c", FORTY, cache1.get(A_C, "age"));
      assertEquals("Incorrect name for /a/d", JANE, cache1.get(A_D, "name"));
   }

   public void testPartialStateTferWithClassLoader() throws Exception
   {
      // FIXME: This test is meaningless because MarshalledValueInputStream
      // will find the classes w/ their own loader if TCL can't.  Need
      // to find a way to test!
      // But, at least it tests JBCACHE-305 by registering a classloader
      // both before and after start()

      // Set the TCL to a classloader that can't see Person/Address
      Thread.currentThread().setContextClassLoader(getNotFoundClassLoader());

      CacheSPI<Object, Object> cache1 = createCache(
            false, // async
            true, // use marshaller
            true, // use cacheloader
            false, false, true);// don't start
      ClassLoader cl1 = getClassLoader();
      cache1.getRegion(A, true).registerContextClassLoader(cl1);
      startCache(cache1);

      cache1.getRegion(A, true).activate();

      Object ben = createBen(cl1);

      cache1.put(A_B, "person", ben);

      // For cache 2 we won't register loader until later
      CacheSPI<Object, Object> cache2 = createCache(
            false, // async
            true, // use marshalling
            true, // use cacheloader
            false, true, true);// start

      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{cache1, cache2}, 60000);

      CacheLoader loader = cache2.getCacheLoaderManager().getCacheLoader();

      assertNull("/a/b not transferred to loader", loader.get(A_B));

      assertNull("/a/b not transferred to cache", cache2.get(A_B, "person"));

      ClassLoader cl2 = getClassLoader();

      //      cache2.registerClassLoader(A, cl2);
      Region r = cache2.getRegion(A, true);
      r.registerContextClassLoader(cl2);

      r.activate();

      assertEquals("Correct state from loader for /a/b", ben.toString(), getUnmarshalled(loader.get(A_B).get("person")).toString());

      assertEquals("Correct state from cache for /a/b", ben.toString(), getUnmarshalled(cache2.get(A_B, "person")).toString());

   }

   private Object getUnmarshalled(Object o) throws Exception
   {
      return o instanceof MarshalledValue ? ((MarshalledValue) o).get() : o;
   }

   public void testStalePersistentState() throws Exception
   {
      CacheSPI c1 = createCache(true, false, true, true);
      c1.put(A, "K", "V");

      assert c1.get(A, "K").equals("V");
      CacheLoader l1 = c1.getCacheLoaderManager().getCacheLoader();
      assert l1 != null;

      assert l1.exists(A);
      assert l1.get(A).get("K").equals("V");

      // test persistence
      c1.stop();

      assert l1.exists(A);
      assert l1.get(A).get("K").equals("V");

      Cache c2 = createCache(true, false, true, true);

      c2.put(B, "K", "V");

      assert c1.getConfiguration().isFetchInMemoryState();
      assert c1.getConfiguration().getCacheLoaderConfig().isFetchPersistentState();
      c1.start();

      assert c1.get(B, "K").equals("V");
      assert c1.get(A, "K") == null;
   }

   private Object createBen(ClassLoader loader) throws Exception
   {
      Class addrClazz = loader.loadClass(ADDRESS_CLASSNAME);
      Method setCity = addrClazz.getMethod("setCity", String.class);
      Method setStreet = addrClazz.getMethod("setStreet", String.class);
      Method setZip = addrClazz.getMethod("setZip", int.class);
      Object addr = addrClazz.newInstance();
      setCity.invoke(addr, "San Jose");
      setStreet.invoke(addr, "1007 Home");
      setZip.invoke(addr, 90210);

      Class benClazz = loader.loadClass(PERSON_CLASSNAME);
      Method setName = benClazz.getMethod("setName", String.class);
      Method setAddress = benClazz.getMethod("setAddress", addrClazz);
      Object ben = benClazz.newInstance();
      setName.invoke(ben, "Ben");
      setAddress.invoke(ben, addr);

      return ben;
   }


   private BuddyReplicationConfig getBuddyConfig() throws Exception
   {
      BuddyReplicationConfig brc = new BuddyReplicationConfig();
      brc.setEnabled(true);
      brc.setBuddyPoolName("TEST");
      return brc;
   }

   private CacheSPI<Object, Object> createCacheWithBr(String cacheName) throws Exception
   {
      CacheSPI<Object, Object> cache1 = createCache(false, false, false, false, false, true);
      cache1.getConfiguration().setBuddyReplicationConfig(getBuddyConfig());
      cache1.start();
      return cache1;
   }
}
