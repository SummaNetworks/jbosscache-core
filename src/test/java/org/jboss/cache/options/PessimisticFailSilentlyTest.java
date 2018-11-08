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
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests passing in the failSilently option in various scenarios.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, sequential = true, testName = "options.PessimisticFailSilentlyTest")
public class PessimisticFailSilentlyTest
{
   private CacheSPI cache;
   private TransactionManager manager;
   private Transaction tx;
   private Fqn fqn = Fqn.fromString("/a");
   private String key = "key";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      if (cache != null)
         tearDown();
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI) instance.createCache(false, getClass());
      // very short acquisition timeout
      cache.getConfiguration().setLockAcquisitionTimeout(100);
      cache.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.start();
      manager = cache.getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (tx != null)
      {
         try
         {
            manager.resume(tx);
            manager.rollback();
         }
         catch (Exception e)
         {
            // who cares
         }
      }
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   public void testPutKeyValue() throws Exception
   {
      manager.begin();
      cache.put(fqn, key, "value");
      tx = manager.suspend();
      cache.getInvocationContext().getOptionOverrides().setFailSilently(true);
      cache.put(fqn, key, "value2");
   }

   public void testPutData() throws Exception
   {
      Map<String, String> data = new HashMap<String, String>();
      data.put(key, "value");
      manager.begin();
      cache.put(fqn, data);
      tx = manager.suspend();
      cache.getInvocationContext().getOptionOverrides().setFailSilently(true);
      cache.put(fqn, data);
   }

   public void testRemoveNode() throws Exception
   {
      cache.put(fqn, key, "value");
      manager.begin();
      // get a read lock
      cache.get(fqn, key);
      tx = manager.suspend();
      cache.getInvocationContext().getOptionOverrides().setFailSilently(true);
      cache.removeNode(fqn);
   }

   public void testRemoveKey() throws Exception
   {
      cache.put(fqn, key, "value");
      manager.begin();
      // get a read lock
      cache.get(fqn, key);
      tx = manager.suspend();
      cache.getInvocationContext().getOptionOverrides().setFailSilently(true);
      cache.remove(fqn, key);
   }

   public void testGetNode() throws Exception
   {
      cache.put(fqn, key, "value");
      manager.begin();
      // get a WL
      cache.put(fqn, key, "value2");
      tx = manager.suspend();
      cache.getInvocationContext().getOptionOverrides().setFailSilently(true);
      cache.getNode(fqn);
   }

   public void testGetKey() throws Exception
   {
      cache.put(fqn, key, "value");
      manager.begin();
      // get a WL
      cache.put(fqn, key, "value2");
      tx = manager.suspend();
      cache.getInvocationContext().getOptionOverrides().setFailSilently(true);
      cache.get(fqn, key);
   }

   public void testGetChildrenNames() throws Exception
   {
      cache.put(fqn, key, "value");
      manager.begin();
      // get a WL
      cache.put(fqn, key, "value2");
      tx = manager.suspend();
      cache.getInvocationContext().getOptionOverrides().setFailSilently(true);
      cache.getChildrenNames(fqn);

   }

   public void testPutThatWillFail() throws Exception
   {
      manager.begin();
      cache.put(fqn, "k", "v");// this will get WLs on / and /a
      tx = manager.suspend();

      assertEquals(2, cache.getNumberOfLocksHeld());

      // now this call WILL fail, but should fail silently - i.e., not roll back.
      manager.begin();
      cache.getInvocationContext().getOptionOverrides().setFailSilently(true);
      cache.put(fqn, "x", "y");

      // should not roll back, despite the cache put call failing/timing out.
      manager.commit();
   }
}
