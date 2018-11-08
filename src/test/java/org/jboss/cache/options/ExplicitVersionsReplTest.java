package org.jboss.cache.options;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.optimistic.DataVersion;
import org.jboss.cache.optimistic.DefaultDataVersion;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.TransactionManager;

/**
 * Tests the passing in of explicit {@see DataVersion} instances when using optimistic locking + replication.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "options.ExplicitVersionsReplTest")
public class ExplicitVersionsReplTest
{
   private CacheSPI<Object, Object> cache[];
   private Fqn fqn = Fqn.fromString("/a");
   private String key = "key";

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      if (cache != null)
         tearDown();
      cache = new CacheSPI[2];
      cache[0] = createCache();
      cache[1] = createCache();
      TestingUtil.blockUntilViewsReceived(cache, 20000);
   }

   private CacheSPI<Object, Object> createCache()
   {
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setNodeLockingScheme("OPTIMISTIC");
      // give us lots of time to trace and debug shit
      c.setSyncCommitPhase(true);
      c.setSyncRollbackPhase(true);
      c.setSyncReplTimeout(1000);
      c.setLockAcquisitionTimeout(1000);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");


      return (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (cache != null)
      {
         for (CacheSPI aCache : cache)
            destroyCache(aCache);
         cache = null;
      }
   }

   private void destroyCache(CacheSPI c)
   {
      TransactionManager tm = c.getTransactionManager();
      try
      {
         if (tm != null && tm.getTransaction() != null)
            tm.rollback();
      }
      catch (Exception e)
      {
      }
      c.stop();
   }

   /**
    * This test sets a custom data version first, expects it to replicate, and then does a put on the remote
    * cache using an implicit data version.  Should fail with a CCE.
    *
    * @throws Exception
    */
   public void testIncompatibleVersionTypes1() throws Exception
   {
      DataVersion version = new TestVersion("99");
      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache[0].put(fqn, key, "value");// TestVersion-99 should be on both caches now

      TransactionManager mgr = cache[0].getTransactionManager();
      mgr.begin();
      // don't explicitly set a data version.

      // force an IC scrub
      //cache[1].getInvocationContext().setOptionOverrides(null);
      cache[1].put(fqn, key, "value2");
      try
      {
         mgr.commit();

         assertTrue("expected to fail", false);
      }
      catch (RollbackException e)
      {
         // should fail.
         assertTrue("expected to fail with a nested ClassCastException", true);
      }
   }

   /**
    * This test sets a custom data version first, expects it to replicate, and then does a put on the remote
    * cache using a higher custom data version.  Should pass and not throw any exceptions.
    *
    * @throws Exception
    */
   public void testCompatibleVersionTypes1() throws Exception
   {
      DataVersion version = new TestVersion("99");
      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache[0].put(fqn, key, "value");// TestVersion-99 should be on both caches now

      TransactionManager mgr = cache[0].getTransactionManager();
      mgr.begin();

      version = new TestVersion("999");
      cache[1].getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache[1].put(fqn, key, "value2");
      mgr.commit();
   }

   /**
    * This test sets a custom data version first, expects it to replicate, and then does a put on the remote
    * cache using a lower custom data version.  Should fail.
    *
    * @throws Exception
    */
   public void testCompatibleVersionTypesOutDatedVersion1() throws Exception
   {
      DataVersion version = new TestVersion("99");
      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache[0].put(fqn, key, "value");// TestVersion-99 should be on both caches now

      TransactionManager mgr = cache[0].getTransactionManager();
      mgr.begin();

      version = new TestVersion("29");
      cache[1].getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache[1].put(fqn, key, "value2");
      try
      {
         mgr.commit();
         assertTrue("expected to fail", false);
      }
      catch (RollbackException e)
      {
         // should fail.
         assertTrue("expected to fail with a CacheException to do with a versioning mismatch", true);
      }
   }

   /**
    * This test sets an implicit data version first, expects it to replicate, and then does a put on the remote
    * cache using a custom data version.  Should fail with a CCE.
    *
    * @throws Exception
    */
   public void testIncompatibleVersionTypes2() throws Exception
   {
      cache[0].put(fqn, key, "value");// default data version should be on both caches now

      TransactionManager mgr = cache[0].getTransactionManager();
      mgr.begin();

      // explicitly set data version
      DataVersion version = new TestVersion("99");
      cache[1].getInvocationContext().getOptionOverrides().setDataVersion(version);

      try
      {
         cache[1].put(fqn, key, "value2");
         mgr.commit();
         assertTrue("expected to fail", false);
      }
      catch (Exception e)
      {
         // should fail.
         assertTrue("expected to fail", true);
      }
   }

   /**
    * This test sets an implicit data version first, expects it to replicate, and then does a put on the remote
    * cache using a higher implicit data version.  Should pass and not throw any exceptions.
    *
    * @throws Exception
    */
   public void testCompatibleVersionTypes2() throws Exception
   {
      cache[0].put(fqn, key, "value");// TestVersion-99 should be on both caches now

      TransactionManager mgr = cache[0].getTransactionManager();
      mgr.begin();

      DataVersion version = new DefaultDataVersion(300);
      cache[1].getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache[1].put(fqn, key, "value2");
      mgr.commit();
   }

   /**
    * This test sets an implicit data version first, expects it to replicate, and then does a put on the remote
    * cache using a lower implicit data version.  Should fail.
    *
    * @throws Exception
    */
   public void testCompatibleVersionTypesOutDatedVersion2() throws Exception
   {
      DataVersion version = new DefaultDataVersion(200);
      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache[0].put(fqn, key, "value");// TestVersion-99 should be on both caches now

      TransactionManager mgr = cache[0].getTransactionManager();
      mgr.begin();

      version = new DefaultDataVersion(100);
      cache[1].getInvocationContext().getOptionOverrides().setDataVersion(version);
      cache[1].put(fqn, key, "value2");
      try
      {
         // this call will use implicit versioning and will hence fail.
         mgr.commit();
         assertTrue("expected to fail", false);
      }
      catch (Exception e)
      {
         // should fail.
         assertTrue("expected to fail with a CacheException to do with a versioning mismatch", true);
      }
   }

   public void testPropagationOfDefaultVersions() throws Exception
   {
      DefaultDataVersion expected = new DefaultDataVersion();
      expected = (DefaultDataVersion) expected.increment();

      cache[0].put(fqn, key, "value");

      assertEquals("value", cache[0].get(fqn, key));
      assertEquals("value", cache[1].get(fqn, key));
      assertEquals(expected, cache[0].getNode(fqn).getVersion());
      assertEquals(expected, cache[1].getNode(fqn).getVersion());

      cache[1].put(fqn, key, "value2");
      expected = (DefaultDataVersion) expected.increment();

      assertEquals("value2", cache[0].get(fqn, key));
      assertEquals("value2", cache[1].get(fqn, key));
      assertEquals(expected, cache[0].getNode(fqn).getVersion());
      assertEquals(expected, cache[1].getNode(fqn).getVersion());
   }

   public void testPropagationOfCustomVersions() throws Exception
   {
      TestVersion expected = new TestVersion("100");
      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(expected);
      cache[0].put(fqn, key, "value");

      assertEquals("value", cache[0].get(fqn, key));
      assertEquals("value", cache[1].get(fqn, key));
      assertEquals(expected, cache[0].getNode(fqn).getVersion());
      assertEquals(expected, cache[1].getNode(fqn).getVersion());

      expected = new TestVersion("200");
      cache[1].getInvocationContext().getOptionOverrides().setDataVersion(expected);
      cache[1].put(fqn, key, "value2");

      assertEquals("value2", cache[0].get(fqn, key));
      assertEquals("value2", cache[1].get(fqn, key));
      assertEquals(expected, cache[0].getNode(fqn).getVersion());
      assertEquals(expected, cache[1].getNode(fqn).getVersion());
   }

   public void testExplicitVersionOnRoot() throws Exception
   {
      TestVersion newVersion = new TestVersion("100");

      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(newVersion);
      cache[0].getTransactionManager().begin();
      cache[0].put(Fqn.ROOT, "k", "v");

      try
      {
         cache[0].getTransactionManager().commit();
         fail("Should have barfed");
      }
      catch (RollbackException rbe)
      {
         // should barf since by default ROOT uses a default DV
      }
   }

   public void testExplicitVersionOnLeaf() throws Exception
   {
      cache[0].put("/org/domain/Entity", null);
      assertEquals(1, ((DefaultDataVersion) ((NodeSPI) cache[0].getNode("/org/domain/Entity")).getVersion()).getRawVersion());
      assertEquals(1, ((DefaultDataVersion) ((NodeSPI) cache[1].getNode("/org/domain/Entity")).getVersion()).getRawVersion());

      TestVersion v = new TestVersion("Arse");
      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(v);

      cache[0].put(Fqn.fromString("/org/domain/Entity/EntityInstance#1"), "k", "v");

      assertEquals(1, ((DefaultDataVersion) ((NodeSPI) cache[0].getNode("/org/domain/Entity")).getVersion()).getRawVersion());
      assertEquals(v, cache[0].getNode("/org/domain/Entity/EntityInstance#1").getVersion());
      assertEquals(1, ((DefaultDataVersion) ((NodeSPI) cache[1].getNode("/org/domain/Entity")).getVersion()).getRawVersion());
      assertEquals(v, cache[1].getNode("/org/domain/Entity/EntityInstance#1").getVersion());

   }

   public void testExplicitVersionOnLeafImplicitParentCreation() throws Exception
   {
      TestVersion v = new TestVersion("Arse");
      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(v);

      cache[0].put(Fqn.fromString("/org/domain/Entity/EntityInstance#1"), "k", "v");

      assertEquals(0, ((DefaultDataVersion) ((NodeSPI) cache[0].getNode("/org/domain/Entity")).getVersion()).getRawVersion());
      assertEquals(v, cache[0].getNode("/org/domain/Entity/EntityInstance#1").getVersion());
      assertEquals(0, ((DefaultDataVersion) ((NodeSPI) cache[1].getNode("/org/domain/Entity")).getVersion()).getRawVersion());
      assertEquals(v, cache[1].getNode("/org/domain/Entity/EntityInstance#1").getVersion());

   }

   public void testExplicitVersionOnParentAndChild() throws Exception
   {
      TestVersion vParent = new TestVersion("Parent-Version");

      cache[0].getTransactionManager().begin();
      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(vParent);
      cache[0].put(Fqn.fromString("/parent"), "k", "v");
      cache[0].getTransactionManager().commit();

      assertEquals(0, ((DefaultDataVersion) ((NodeSPI) cache[0].getRoot()).getVersion()).getRawVersion());
      assertEquals(vParent, cache[0].getNode("/parent").getVersion());
      assertEquals(0, ((DefaultDataVersion) ((NodeSPI) cache[1].getRoot()).getVersion()).getRawVersion());
      assertEquals(vParent, cache[1].getNode("/parent").getVersion());

      TestVersion vChild = new TestVersion("Child-Version");

      cache[0].getTransactionManager().begin();
      cache[0].getInvocationContext().getOptionOverrides().setDataVersion(vChild);
      cache[0].put(Fqn.fromString("/parent/child"), "k", "v");
      cache[0].getTransactionManager().commit();

      assertEquals(0, ((DefaultDataVersion) ((NodeSPI) cache[0].getRoot()).getVersion()).getRawVersion());
      assertEquals(vParent, cache[0].getNode("/parent").getVersion());
      assertEquals(vChild, cache[0].getNode("/parent/child").getVersion());
      assertEquals(0, ((DefaultDataVersion) ((NodeSPI) cache[1].getRoot()).getVersion()).getRawVersion());
      assertEquals(vParent, cache[1].getNode("/parent").getVersion());
      assertEquals(vChild, cache[1].getNode("/parent/child").getVersion());

   }
}
