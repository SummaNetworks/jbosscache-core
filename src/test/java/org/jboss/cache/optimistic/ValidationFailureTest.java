/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Cache;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

/**
 * Tests a failure in validating a concurrently updated node
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.ValidationFailureTest")
public class ValidationFailureTest extends AbstractOptimisticTestCase
{
   public void testValidationFailureLockRelease() throws Exception
   {
      CacheSPI<Object, Object> cache = createCache();

      TransactionManager mgr = cache.getTransactionManager();

      mgr.begin();
      cache.put("/a", "key", "value");
      mgr.commit();

      // should succeed, 0 locks left over
      assertEquals("value", cache.get("/a", "key"));
      assertEquals(0, cache.getNumberOfLocksHeld());

      mgr.begin();
      cache.put("/b", "key", "value");
      Transaction tx1 = mgr.suspend();

      mgr.begin();
      cache.put("/b", "key", "value2");
      mgr.commit();

      mgr.resume(tx1);
      try
      {
         mgr.commit();
         assertTrue("Should have failed", false);
      }
      catch (Exception e)
      {
         assertTrue("Expecting this to fail", true);
      }

      // nothing should have been locked.
      assertEquals(0, cache.getNumberOfLocksHeld());

     TestingUtil.killCaches((Cache<Object, Object>) cache);
   }
}
