package org.jboss.cache.mgmt;

import org.jboss.cache.interceptors.ActivationInterceptor;
import org.jboss.cache.interceptors.PassivationInterceptor;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import org.testng.annotations.Test;

import java.util.HashMap;

/**
 * Simple functional tests for ActivationInterceptor and PassivationInterceptor statistics
 *
 * @author Jerry Gauthier
 * @version $Id: PassivationTest.java 7735 2009-02-19 13:40:55Z manik.surtani@jboss.com $
 */
@Test(groups = "functional", testName = "mgmt.PassivationTest")
public class PassivationTest extends MgmtTestBase
{
   public PassivationTest()
   {
      passivation = true;
   }

   public void testPassivationMgmt() throws Exception
   {
      assertNotNull("Cache is null.", cache);

      // Note: because these tests are normally executed without a server, the interceptor
      // MBeans are usually not available for use in the tests.  Consequently it's necessary
      // to obtain references to the interceptors and work with them directly.
      ActivationInterceptor act = TestingUtil.findInterceptor(cache, ActivationInterceptor.class);
      assertNotNull("ActivationInterceptor not found.", act);
      PassivationInterceptor pass = TestingUtil.findInterceptor(cache, PassivationInterceptor.class);
      assertNotNull("PassivationInterceptor not found.", pass);

      int miss = 5;
      int activations = 0;
      // was 5 in 1.3 (one per attribute)
      // now just Europe/Albania and Europe/Hungary were loaded

      // verify statistics for entries loaded into cache
      assertEquals("CacheLoaderLoads count error: ", 0, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 0, pass.getPassivations());

      // now try retrieving a valid attribute and an invalid attribute
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + AUSTRIA, cache.get(AUSTRIA, CAPITAL));
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + AUSTRIA, cache.get(AUSTRIA, AREA));

      // verify statistics after retrieving entries - no change since nodes were already loaded
      assertEquals("CacheLoaderLoads count error: ", 0, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 0, pass.getPassivations());

      // now try retrieving an attribute for an invalid node
      assertNull("Retrieval error: did not expect to retrieve " + CAPITAL + " for " + POLAND, cache.get(POLAND, CAPITAL));

      // verify statistics after retrieving invalid node - misses should now be incremented
      miss++;
      assertEquals("CacheLoaderLoads count error: ", 0, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 0, pass.getPassivations());

      // now evict Austria and confirm that it's no longer in cache
      cache.evict(AUSTRIA, false);
      assertNull("Retrieval error: did not expect to find node " + AUSTRIA + " in cache", cache.peek(AUSTRIA, false));

      // passivations should noe be 1
      assertEquals("Passivations count error: ", 1, pass.getPassivations());

      // now try retrieving the attributes again - first retrieval should trigger a cache load
      assertNotNull("Retrieval error: expected to retrieve " + CAPITAL + " for " + AUSTRIA, cache.get(AUSTRIA, CAPITAL));
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + AUSTRIA, cache.get(AUSTRIA, CURRENCY));

      // verify statistics after retrieving evicted entry - loads and activations should now increment by 1
      activations+= 3;
      assertEquals("CacheLoaderLoads count error: ", 1, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 1, pass.getPassivations());

      // now remove Austria and confirm that it's not in cache or loader
      cache.removeNode(AUSTRIA);
      assertNull("Retrieval error: did not expect to find node " + AUSTRIA + " in cache", cache.peek(AUSTRIA, false));
      assertFalse("Retrieval error: did not expect to find node " + AUSTRIA + " in loader", cl.exists(AUSTRIA));

      // verify statistics after removing entry - should be unchanged
      assertEquals("CacheLoaderLoads count error: ", 1, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 1, pass.getPassivations());

      // now try retrieving attributes again - each attempt should fail and cause a miss since node is now removed
      assertNull("Retrieval error: did not expect to retrieve " + CAPITAL + " for " + AUSTRIA, cache.get(AUSTRIA, CAPITAL));
      assertNull("Retrieval error: did not expect to retrieve " + CURRENCY + " for " + AUSTRIA, cache.get(AUSTRIA, CURRENCY));

      // verify statistics after trying to retrieve removed node's attributes - should be two more misses
      miss += 2;
      assertEquals("CacheLoaderLoads count error: ", 1, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 1, pass.getPassivations());

      cache.put(POLAND, new HashMap<String, Object>());
      cache.put(POLAND, CAPITAL, "Warsaw");
      cache.put(POLAND, CURRENCY, "Zloty");
      miss ++;
      assertEquals("CacheLoaderLoads count error: ", 1, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 1, pass.getPassivations());

      // evict Poland - this will cause a passivation
      cache.evict(POLAND, false);
      assertEquals("CacheLoaderLoads count error: ", 1, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 2, pass.getPassivations());

      // retrieve a valid attribute - this will cause an activation and a load
      activations+=3;
      assertNotNull("Retrieval error: expected to retrieve " + CURRENCY + " for " + POLAND, cache.get(POLAND, CURRENCY));
      assertEquals("CacheLoaderLoads count error: ", 2, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 2, pass.getPassivations());

      // evict again - causing another passivation
      cache.evict(POLAND, false);
      assertEquals("CacheLoaderLoads count error: ", 2, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 3, pass.getPassivations());

      // retrieve an invalid attribute - this will cause an activation and a load
      activations+=3;
      assertNull("Retrieval error: did not expect to retrieve " + AREA + " for " + POLAND, cache.get(POLAND, AREA));
      assertEquals("CacheLoaderLoads count error: ", 3, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error: ", miss, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", activations, act.getActivations());
      assertEquals("Passivations count error: ", 3, pass.getPassivations());

      // reset statistics
      act.resetStatistics();
      pass.resetStatistics();

      // check the statistics again
      assertEquals("CacheLoaderLoads count error after reset: ", 0, act.getCacheLoaderLoads());
      assertEquals("CacheLoaderMisses count error after reset: ", 0, act.getCacheLoaderMisses());
      assertEquals("Activations count error: ", 0, act.getActivations());
      assertEquals("Passivations count error: ", 0, pass.getPassivations());
   }
}
