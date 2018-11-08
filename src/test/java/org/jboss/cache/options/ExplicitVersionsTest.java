/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.optimistic.DataVersion;
import org.jboss.cache.optimistic.DefaultDataVersion;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * Tests the passing in of explicit {@see DataVersion} instances when using optimistic locking.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "optimistic"}, testName = "options.ExplicitVersionsTest")
public class ExplicitVersionsTest
{
   private CacheSPI<String, String> cache;
   private Fqn fqn = Fqn.fromString("/a");
   private String key = "key";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      if (cache != null)
         tearDown();
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache = (CacheSPI<String, String>) instance.createCache(false, getClass());
      cache.getConfiguration().setCacheMode(Configuration.CacheMode.LOCAL);
      cache.getConfiguration().setNodeLockingScheme("OPTIMISTIC");
      cache.getConfiguration().setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache.start();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   public void testSimplePut() throws Exception
   {
      DataVersion version = new TestVersion("99");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache.put(fqn, key, "value");

      //now retrieve the data from the cache.
      assertEquals("value", cache.get(fqn, key));

      // get a hold of the node
      NodeSPI<String, String> node = cache.getNode(fqn);
      DataVersion versionFromCache = node.getVersion();

      assertEquals(TestVersion.class, versionFromCache.getClass());
      assertEquals("99", ((TestVersion) versionFromCache).getInternalVersion());
   }

   public void testFailingPut() throws Exception
   {
      DataVersion version = new TestVersion("99");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache.put(fqn, key, "value");

      version = new TestVersion("25");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(version);
      TransactionManager mgr = cache.getTransactionManager();
      mgr.begin();
      cache.put(fqn, key, "value2");
      try
      {
         mgr.commit();
         assertTrue("expected to fail", false);
      }
      catch (Exception e)
      {
         // should fail.
         assertTrue("expected to fail", true);
      }
   }

   public void testIncompatibleVersionTypes() throws Exception
   {
      DataVersion version = new TestVersion("99");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache.put(fqn, key, "value");
      TransactionManager mgr = cache.getTransactionManager();
      mgr.begin();
      cache.getInvocationContext().getOptionOverrides().setDataVersion(new DefaultDataVersion(777));
      cache.put(fqn, key, "value2");
      try
      {
         // this call will use implicit versioning and will hence fail.
         mgr.commit();
         assertTrue("expected to fail", false);
      }
      catch (Exception e)
      {
         // should fail.
         assertTrue("expected to fail", true);
      }
   }

   public void testExplicitVersionOnLeaf() throws Exception
   {
      cache.put("/org/domain/Entity", null);
      assertEquals(1, ((DefaultDataVersion) ((NodeSPI) cache.getNode("/org/domain/Entity")).getVersion()).getRawVersion());

      TestVersion v = new TestVersion("Arse");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(v);
      cache.put(Fqn.fromString("/org/domain/Entity/EntityInstance#1"), "k", "v");

      assertEquals(1, ((DefaultDataVersion) ((NodeSPI) cache.getNode("/org/domain/Entity")).getVersion()).getRawVersion());
      assertEquals(v, cache.getNode("/org/domain/Entity/EntityInstance#1").getVersion());
   }

   public void testExplicitVersionOnLeafImplicitParentCreation() throws Exception
   {
      TestVersion v = new TestVersion("Arse");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(v);
      cache.put(Fqn.fromString("/org/domain/Entity/EntityInstance#1"), "k", "v");

      assertEquals(0, ((DefaultDataVersion) ((NodeSPI) cache.getNode("/org/domain/Entity")).getVersion()).getRawVersion());
      assertEquals(v, cache.getNode("/org/domain/Entity/EntityInstance#1").getVersion());
   }

   public void testExplicitVersionsOnParents()
   {
      Node<String, String> root = cache.getRoot();

      TestVersion lev2V = new TestVersion("Lev2-v");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(lev2V);
      root.addChild(Fqn.fromString("LEV2"));

      NodeSPI<String, String> lev2 = (NodeSPI<String, String>) root.getChild(Fqn.fromString("LEV2"));

      assertNotNull(lev2);

      assertEquals(lev2V, lev2.getVersion());

      TestVersion lev3V = new TestVersion("Lev3-v");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(lev3V);
      lev2.addChild(Fqn.fromString("LEV3"));

      NodeSPI<String, String> lev3 = (NodeSPI<String, String>) lev2.getChild(Fqn.fromString("LEV3"));

      assertNotNull(lev3);

      assertEquals(lev3V, lev3.getVersion());

      TestVersion lev4V = new TestVersion("Lev4-v");
      cache.getInvocationContext().getOptionOverrides().setDataVersion(lev4V);
      lev3.addChild(Fqn.fromString("LEV4"));

      NodeSPI<String, String> lev4 = (NodeSPI<String, String>) lev3.getChild(Fqn.fromString("LEV4"));

      assertNotNull(lev4);

      assertEquals(lev4V, lev4.getVersion());
   }

   public void testExplicitVersionOnParentAndChild() throws Exception
   {
      TestVersion vParent = new TestVersion("Parent-Version");

      cache.getTransactionManager().begin();
      cache.getInvocationContext().getOptionOverrides().setDataVersion(vParent);
      cache.put(Fqn.fromString("/parent"), "k", "v");
      cache.getTransactionManager().commit();

      assertEquals(0, ((DefaultDataVersion) ((NodeSPI) cache.getNode("/")).getVersion()).getRawVersion());
      assertEquals(vParent, cache.getNode("/parent").getVersion());

      TestVersion vChild = new TestVersion("Child-Version");

      cache.getTransactionManager().begin();
      cache.getInvocationContext().getOptionOverrides().setDataVersion(vChild);
      cache.put(Fqn.fromString("/parent/child"), "k", "v");
      cache.getTransactionManager().commit();

      assertEquals(0, ((DefaultDataVersion) ((NodeSPI) cache.getNode("/")).getVersion()).getRawVersion());
      assertEquals(vParent, cache.getNode("/parent").getVersion());
      assertEquals(vChild, cache.getNode("/parent/child").getVersion());
   }

}
