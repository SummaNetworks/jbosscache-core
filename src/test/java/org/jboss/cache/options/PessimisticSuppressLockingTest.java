/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests the suppression of locking nodes
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, sequential = true, testName = "options.PessimisticSuppressLockingTest")
public class PessimisticSuppressLockingTest
{
   private Fqn fqn = Fqn.fromString("/blah");
   private Fqn fqn1 = Fqn.fromString("/blah/1");

   private CacheSPI<String, String> cache;

   private TransactionManager m;

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      Configuration config = new Configuration();
      config.setCacheMode(Configuration.CacheMode.LOCAL);
      config.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      config.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache = (CacheSPI<String, String>) instance.createCache(config, getClass());
      m = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
      m = null;
   }

   public void testSuppressionOfWriteLocks() throws Exception
   {
      TransactionManager m = cache.getTransactionManager();

      m.begin();
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      cache.put(fqn, "x", "1");
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      assertEquals(2, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());

      cache.removeNode(fqn);

      m.begin();
      cache.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      assertTrue(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      cache.put(fqn, "x", "2");
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      assertEquals(0, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());

      // test normal operation again
      cache.removeNode(fqn);

      m.begin();
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      cache.put(fqn, "x", "3");
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      assertEquals(2, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   /**
    * This one fails now.
    *
    * @throws Exception
    */
   public void testSuppressionOf2WriteLocks() throws Exception
   {
      TransactionManager m = cache.getTransactionManager();

      m.begin();
      cache.put(fqn, "x", "1");
      assertEquals(2, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());

      cache.removeNode(fqn);

      m.begin();
      cache.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      cache.put(fqn, "x", "2");
      cache.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      cache.put(fqn1, "y", "3");
      assertEquals(0, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());

      Map<String, String> map = new HashMap<String, String>();
      map.put("x", "1");
      m.begin();
      cache.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      cache.put(fqn, map);
      cache.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      cache.put(fqn1, map);
      assertEquals(0, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());

      // test normal operation again
      cache.removeNode(fqn);

      m.begin();
      cache.put(fqn, "x", "3");
      assertEquals(2, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testSuppressionOfReadLocks() throws Exception
   {
      cache.put(fqn, "x", "y");

      m.begin();
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      cache.get(fqn, "x");
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      assertEquals(2, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());

      m.begin();
      cache.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      assertTrue(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      cache.get(fqn, "x");
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      assertEquals(0, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());

      // test normal operation again

      m.begin();
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      cache.get(fqn, "x");
      assertFalse(cache.getInvocationContext().getOptionOverrides().isSuppressLocking());
      assertEquals(2, cache.getNumberOfLocksHeld());
      m.commit();
      assertEquals(0, cache.getNumberOfLocksHeld());
   }

   public void testNodeCreation()
   {
      assertNull(cache.getRoot().getChild(fqn));
      cache.getInvocationContext().getOptionOverrides().setSuppressLocking(true);
      cache.put(fqn, "x", "y");
      assertEquals(0, cache.getNumberOfLocksHeld());
      assertEquals("y", cache.getRoot().getChild(fqn).get("x"));
   }

}
