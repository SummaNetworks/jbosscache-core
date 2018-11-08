/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.marshall;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.RegionManager;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.remote.ReplicateCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the "activate/deactivate" functionality of LegacyTreeCacheMarshaller.
 *
 * @author <a href="mailto://brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7284 $
 */
@Test(groups = "functional", sequential = true, testName = "marshall.ActiveInactiveTest")
public class ActiveInactiveTest extends AbstractVersionAwareMarshallerTest
{
   RegionManager rman;
   CacheSPI cache;
   Configuration c;
   Fqn A = Fqn.fromString("/a");
   Fqn I = Fqn.fromString("/i");
   Fqn A_B = Fqn.fromRelativeElements(A, "b");

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      c = cache.getConfiguration();
      c.setUseRegionBasedMarshalling(true);
      c.setFetchInMemoryState(false);
      cache.start();
      this.cr = TestingUtil.extractComponentRegistry(cache);
      rman = TestingUtil.extractComponentRegistry(cache).getComponent(RegionManager.class);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testDefaultActive() throws Exception
   {
      rman.setDefaultInactive(false);
      assertFalse("Root is not active", rman.isInactive(Fqn.ROOT));

      rman.deactivate(A);
      assertFalse("Root is not active after inactivating subtree",
            rman.isInactive(Fqn.ROOT));

      rman.activate(A);
      assertFalse("Root is not active after activating subtree",
            rman.isInactive(Fqn.ROOT));

      rman.activate(A_B);

      rman.deactivate(Fqn.ROOT);
      assertTrue("Root is active", rman.isInactive(Fqn.ROOT));
   }

   public void testDefaultInactive() throws Exception
   {
      rman.setDefaultInactive(true);

      assertTrue("Root is not inactive", rman.isInactive(Fqn.ROOT));

      rman.activate(A);
      assertTrue("Root is not inactive after activating subtree",
            rman.isInactive(Fqn.ROOT));

      rman.deactivate(A);
      assertTrue("Root is not inactive after inactivating subtree",
            rman.isInactive(Fqn.ROOT));

      rman.deactivate(A_B);

      rman.activate(Fqn.ROOT);
      assertFalse("Root is not active", rman.isInactive(Fqn.ROOT));
   }

   public void testActivate() throws Exception
   {
      rman.setDefaultInactive(false);
      rman.activate(A);
      assertFalse("/a is not active after activating",
            rman.isInactive(A));

      rman.deactivate(A);
      rman.activate(A);
      assertFalse("/a is not active after reactivating",
            rman.isInactive(A));

      rman.reset();
      rman.setDefaultInactive(true);

      rman.activate(I);
      assertFalse("/i is not active after activating",
            rman.isInactive(I));
      assertFalse("/i/k is not active after activating /i",
            rman.isInactive(Fqn.fromString("/i/k")));

      rman.deactivate(I);
      rman.activate(I);
      assertFalse("/i is not active after reactivating",
            rman.isInactive(I));
   }

   public void testInactivate() throws Exception
   {
      rman.setDefaultInactive(true);

      rman.deactivate(I);
      assertTrue("/i is not inactive after inactivating",
            rman.isInactive(I));

      rman.activate(I);
      rman.deactivate(I);
      assertTrue("/i is not inactive after re-inactivating",
            rman.isInactive(I));

      rman.reset();
      rman.setDefaultInactive(false);

      rman.deactivate(A);
      assertTrue("/a is not inactive after inactivating",
            rman.isInactive(A));
      assertTrue("/a/b is not inactive after inactivating /a",
            rman.isInactive(A_B));

      rman.activate(A);
      rman.deactivate(A);
      assertTrue("/a is not inactive after re-inactivating",
            rman.isInactive(A));
   }

   public void testObjectFromByteBuffer() throws Exception
   {
      PutKeyValueCommand put = new PutKeyValueCommand(null, A_B, "name", "Joe");
      ReplicateCommand replicate = new ReplicateCommand(put);

      rman.setDefaultInactive(true);
      // register A as an inactive marshalling region
      Region region_A = rman.getRegion(A, true);
      region_A.registerContextClassLoader(this.getClass().getClassLoader());
      assertFalse("New regions created should be inactive by default", region_A.isActive());
      cache.stop();
      c.setUseRegionBasedMarshalling(true);
      c.setInactiveOnStartup(true);
      VersionAwareMarshaller testee = createVAMandRestartCache(rman);

      byte[] callBytes = testee.objectToByteBuffer(replicate);

      try
      {
         testee.objectFromByteBuffer(callBytes);
         fail("Expected to fail since region is inactive");
      }
      catch (Exception e)
      {
         // expected to fail since region is inactive
      }

      rman.activate(A);
      assertTrue(rman.hasRegion(A, Region.Type.MARSHALLING));

      ReplicableCommand result = (ReplicableCommand) testee.objectFromByteBuffer(callBytes);
      assertEquals("Did not get replicate method when passing" +
            " call for active node", ReplicateCommand.class, result.getClass());
   }

}
