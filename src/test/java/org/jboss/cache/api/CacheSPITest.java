package org.jboss.cache.api;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.ViewChangeListener;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.jboss.cache.UnitTestCacheFactory;

@Test(groups = {"functional", "pessimistic"}, testName = "api.CacheSPITest")
public class CacheSPITest
{
   private CacheSPI<Object, Object> cache1;
   private CacheSPI<Object, Object> cache2;

   protected NodeLockingScheme nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration conf1 = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);

      Configuration conf2 = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC);

      conf1.setNodeLockingScheme(nodeLockingScheme);
      conf2.setNodeLockingScheme(nodeLockingScheme);

      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(conf1, false, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(conf2, false, getClass());
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache1, cache2);
   }

   public void testGetMembers() throws Exception
   {
      cache1.start();
      List memb1 = cache1.getMembers();
      assertEquals("View has one member", 1, memb1.size());

      Object coord = memb1.get(0);

      cache2.start();
      memb1 = cache1.getMembers();
      TestingUtil.blockUntilViewsReceived(60000, true, cache1, cache2);
      List memb2 = cache2.getMembers();
      assertEquals("View has two members", 2, memb1.size());
      assertEquals("Both caches have same view", memb1, memb2);

      cache2.stop();
      TestingUtil.blockUntilViewsReceived(60000, true, cache1);
      memb1 = cache1.getMembers();
      assertEquals("View has one member", 1, memb1.size());
      assertTrue("Coordinator same", coord.equals(memb1.get(0)));
   }

   public void testIsCoordinator() throws Exception
   {
      cache1.start();
      assertTrue("Cache1 is coordinator", cache1.getRPCManager().isCoordinator());

      cache2.start();
      assertTrue("Cache1 is still coordinator", cache1.getRPCManager().isCoordinator());
      assertFalse("Cache2 is not coordinator", cache2.getRPCManager().isCoordinator());
      ViewChangeListener viewChangeListener = new ViewChangeListener(cache2);
      cache1.stop();
      // wait till cache2 gets the view change notification
      assert viewChangeListener.waitForViewChange(60, TimeUnit.SECONDS) : "Should have received a view change!";
      assertTrue("Cache2 is coordinator", cache2.getRPCManager().isCoordinator());
   }
}
