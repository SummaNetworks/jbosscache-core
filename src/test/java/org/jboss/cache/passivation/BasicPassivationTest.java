/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.passivation;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.parsing.XmlConfigurationParser;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeActivated;
import org.jboss.cache.notifications.annotation.NodePassivated;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.notifications.event.NodeEvent;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.EvictionController;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author Ben Wang
 * @version $Revision: 7496 $
 */
@Test(groups = {"functional"}, testName = "passivation.BasicPassivationTest")
public class BasicPassivationTest
{
   CacheSPI cache;
   final String ROOT_STR = "/test";
   Throwable t1_ex, t2_ex;
   final long DURATION = 10000;
   boolean isTrue;
   final String FQNSTR = "/org/jboss/3";
   int activationCount = 0;
   int passivationCount = 0;
  private EvictionController ec;

  @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
     UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
     cache = (CacheSPI) instance.createCache(new XmlConfigurationParser().parseFile("configs/local-passivation.xml"), false, getClass());
     cache.getConfiguration().getEvictionConfig().setWakeupInterval(0);
     cache.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
     Object listener = new TestCacheListener();
     cache.getConfiguration().getCacheLoaderConfig().getFirstCacheLoaderConfig().setClassName(DummyInMemoryCacheLoader.class.getName());
     cache.start();
     ec = new EvictionController(cache);
     cache.getNotifier().addCacheListener(listener);
     t1_ex = t2_ex = null;
      isTrue = true;
   }

  @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testBasic()
   {
      activationCount = 0;
      passivationCount = 0;

      cache.put(FQNSTR, FQNSTR, FQNSTR);

      TestingUtil.sleepThread(1100);
      ec.startEviction();

      assert !(cache.exists(FQNSTR) && cache.getNode(FQNSTR).getKeys().contains(FQNSTR)) : "Should have been evicted!!";
      Object val = cache.get(FQNSTR, FQNSTR);
      assertNotNull("DataNode should not be empty ", val);

      assertEquals("activation count:", 1, activationCount);
      assertEquals("passivation count:", 1, passivationCount);
   }

   public void testDualPassivation() throws Exception
   {
      Fqn fqn = Fqn.fromString(FQNSTR);
      cache.put(fqn, "key", "value");
      cache.evict(fqn);
      cache.evict(fqn);
      assertEquals("Proper value after 2 passivations", "value", cache.get(fqn, "key"));
   }

   public void testIntermingledPassivation() throws Exception
   {
      Fqn fqn = Fqn.fromString(FQNSTR);
      cache.put(fqn, "key1", "value");
      cache.evict(fqn);
      cache.put(fqn, "key2", "value");
      cache.evict(fqn);
      assertEquals("Proper value after 2 passivations", "value", cache.get(fqn, "key1"));

   }

   @CacheListener
   public class TestCacheListener
   {
      @NodeActivated
      @NodePassivated
      public void callback(NodeEvent ne)
      {
         if (ne.isPre())
            return;// we are not interested in postActivate event
         if (!ne.getFqn().isChildOrEquals(Fqn.fromString(FQNSTR)))
            return;// don't care about fqn that doesn't belong to me.

         if (ne.getType() == Event.Type.NODE_ACTIVATED)
            activationCount++;
         else if (ne.getType() == Event.Type.NODE_PASSIVATED)
            passivationCount++;
      }
   }
}
