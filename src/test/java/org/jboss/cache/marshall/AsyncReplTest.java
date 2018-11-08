/*
 *
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.marshall;


import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.marshall.data.Address;
import org.jboss.cache.marshall.data.Person;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.*;
import java.lang.reflect.Method;

/**
 * Test marshalling for async mode.
 *
 * @author Ben Wang
 * @version $Revision: 7422 $
 */
@Test(groups = {"functional", "jgroups"}, testName = "marshall.AsyncReplTest")
public class AsyncReplTest extends RegionBasedMarshallingTestBase
{
   CacheSPI<Object, Object> cache1, cache2;
   String props = null;
   Person ben;
   Address addr;
   Throwable ex;
   private Fqn aop = Fqn.fromString("/aop");
   protected boolean useMarshalledValues = false;
   ReplicationListener replListener1;
   ReplicationListener replListener2;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      super.setUp();
      cache1 = createCache("TestCache");

      cache2 = createCache("TestCache");

      replListener1 = ReplicationListener.getReplicationListener(cache1);
      replListener2 = ReplicationListener.getReplicationListener(cache2);
      addr = new Address();
      addr.setCity("San Jose");
      ben = new Person();
      ben.setName("Ben");
      ben.setAddress(addr);

      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{cache1, cache2}, 60000);
   }

   private CacheSPI<Object, Object> createCache(String name)
   {
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_ASYNC);
      c.setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      c.setClusterName(name + "-" + Thread.currentThread().getName());
      // Use marshaller
      c.setUseLazyDeserialization(useMarshalledValues);
      c.setUseRegionBasedMarshalling(!useMarshalledValues);
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      cache.create();
      cache.start();
      return cache;
   }

   @AfterMethod(alwaysRun = true)
   @Override public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache1, cache2);
      super.tearDown();
      cache1 = null;
      cache2 = null;
   }

   /**
    * Test replication with classloaders.
    *
    * @throws Exception
    */
   public void testCLSet2() throws Exception
   {
      ClassLoader cla = getClassLoader();
      ClassLoader clb = getClassLoader();

      if (!useMarshalledValues)
      {
         Region existing = cache1.getRegion(aop, true);
         existing.registerContextClassLoader(cla);
         existing = cache2.getRegion(aop, true);
         existing.registerContextClassLoader(clb);
      }

      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(cla);
      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(aop, "person", ben);
      replListener2.waitForReplicationToOccur();

      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(Fqn.fromString("/alias"), "person", ben);
      replListener2.waitForReplicationToOccur();

      if (useMarshalledValues) resetContextClassLoader();

      Object ben2 = null;
      // Can't cast it to Person. CCE will resutl.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      ben2 = cache2.get(aop, "person");
      if (useMarshalledValues) resetContextClassLoader();
      assertNotNull(ben2);
      assertEquals(ben.toString(), ben2.toString());

      Class<?> claz = clb.loadClass(ADDRESS_CLASSNAME);
      Object add = claz.newInstance();
      Method setValue = claz.getMethod("setCity", String.class);
      setValue.invoke(add, "Sunnyvale");
      Class<?> clasz1 = clb.loadClass(PERSON_CLASSNAME);
      setValue = clasz1.getMethod("setAddress", claz);
      setValue.invoke(ben2, add);

      // Set it back to the cache
      // Can't cast it to Person. CCE will resutl.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      replListener1.expect(PutKeyValueCommand.class);
      cache2.put(aop, "person", ben2);
      replListener1.waitForReplicationToOccur();
      if (useMarshalledValues) resetContextClassLoader();
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(cla);
      Object ben3 = cache1.get(aop, "person");
      if (useMarshalledValues) resetContextClassLoader();
      assertEquals(ben2.toString(), ben3.toString());
   }

   public void testPuts() throws Exception
   {
      ClassLoader cla = getClassLoader();
      ClassLoader clb = getClassLoader();

      if (!useMarshalledValues)
      {
         Region r1 = cache1.getRegion(aop, false) == null ? cache1.getRegion(aop, true) : cache1.getRegion(aop, false);
         r1.registerContextClassLoader(cla);
         Region r2 = cache2.getRegion(aop, false) == null ? cache2.getRegion(aop, true) : cache2.getRegion(aop, false);
         r2.registerContextClassLoader(clb);
      }

      // Create an empty Person loaded by this classloader
      Object scopedBen1 = getPersonFromClassloader(cla);
      // Create another empty Person loaded by this classloader
      Object scopedBen2 = getPersonFromClassloader(clb);

      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(cla);

      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(Fqn.fromString("/aop/1"), "person", ben);
      replListener2.waitForReplicationToOccur();

      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(Fqn.fromString("/aop/2"), "person", scopedBen1);
      replListener2.waitForReplicationToOccur();

      if (useMarshalledValues) resetContextClassLoader();

      Object ben2 = null;
      // Can't cast it to Person. CCE will resutl.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      ben2 = cache2.get(Fqn.fromString("/aop/1"), "person");
      assertEquals(ben.toString(), ben2.toString());

      ben2 = cache2.get(Fqn.fromString("/aop/2"), "person");
      assertFalse("cache2 deserialized with scoped classloader", ben2 instanceof Person);
      assertFalse("cache2 deserialized with cache2 classloader", scopedBen1.equals(ben2));
      assertEquals("scopedBen deserialized properly", scopedBen2, ben2);
   }

   public void testTxPut() throws Exception
   {
      replListener2.expect(PutKeyValueCommand.class);
      beginTransaction();
      cache1.put(aop, "person", ben);
      commit();
      replListener2.waitForReplicationToOccur();
      Person ben2 = (Person) cache2.get(aop, "person");
      assertNotNull("Person from 2nd cache should not be null ", ben2);
      assertEquals(ben.toString(), ben2.toString());
   }

   public void testTxCLSet2() throws Exception
   {
      ClassLoader cla = getClassLoader();
      ClassLoader clb = getClassLoader();

      if (!useMarshalledValues)
      {
         Region r1 = cache1.getRegion(aop, false) == null ? cache1.getRegion(aop, true) : cache1.getRegion(aop, false);
         r1.registerContextClassLoader(cla);
         Region r2 = cache2.getRegion(aop, false) == null ? cache2.getRegion(aop, true) : cache2.getRegion(aop, false);
         r2.registerContextClassLoader(clb);
      }

      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(cla);
      replListener2.expect(PutKeyValueCommand.class);
      beginTransaction();
      cache1.put(aop, "person", ben);
      commit();
      replListener2.waitForReplicationToOccur();
      if (useMarshalledValues) resetContextClassLoader();


      Object ben2 = null;
      // Can't cast it to Person. CCE will resutl.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      ben2 = cache2.get(aop, "person");
      if (useMarshalledValues) resetContextClassLoader();
      assertEquals(ben.toString(), ben2.toString());

      Class<?> claz = clb.loadClass(ADDRESS_CLASSNAME);
      Object add = claz.newInstance();
      Method setValue = claz.getMethod("setCity", String.class);
      setValue.invoke(add, "Sunnyvale");
      Class<?> clasz1 = clb.loadClass(PERSON_CLASSNAME);
      setValue = clasz1.getMethod("setAddress", claz);
      setValue.invoke(ben2, add);

      // Set it back to the cache
      // Can't cast it to Person. CCE will resutl.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      replListener1.expect(PutKeyValueCommand.class);
      cache2.put(aop, "person", ben2);
      if (useMarshalledValues) resetContextClassLoader();
      replListener1.waitForReplicationToOccur();
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(cla);
      Object ben3 = cache1.get(aop, "person");
      if (useMarshalledValues) resetContextClassLoader();
      assertEquals(ben2.toString(), ben3.toString());
   }

   public void testCustomFqn() throws Exception
   {
      FooClassLoader cl1 = new FooClassLoader(Thread.currentThread().getContextClassLoader());
      Region r1 = cache1.getRegion(aop, false) == null ? cache1.getRegion(aop, true) : cache1.getRegion(aop, false);
      r1.registerContextClassLoader(cl1);

      FooClassLoader cl2 = new FooClassLoader(Thread.currentThread().getContextClassLoader());
      Region r2 = cache2.getRegion(aop, false) == null ? cache2.getRegion(aop, true) : cache2.getRegion(aop, false);
      r2.registerContextClassLoader(cl2);

      Class<?> clazz = cl1.loadFoo();
      Object custom1 = clazz.newInstance();

      clazz = cl2.loadFoo();
      Object custom2 = clazz.newInstance();

      Fqn base = Fqn.fromString("/aop");
      Fqn fqn = Fqn.fromRelativeElements(base, custom1);
      replListener2.expect(PutKeyValueCommand.class);
      cache1.put(fqn, "key", "value");
      replListener2.waitForReplicationToOccur();

      Fqn fqn2 = Fqn.fromRelativeElements(base, custom2);
      Object val = cache2.get(fqn2, "key");
      assertEquals("value", val);
   }

   private Transaction beginTransaction() throws SystemException, NotSupportedException
   {
      TransactionManager mgr = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();
      return mgr.getTransaction();
   }

   private void commit() throws SecurityException, IllegalStateException, RollbackException, HeuristicMixedException, HeuristicRollbackException, SystemException
   {
      cache1.getConfiguration().getRuntimeConfig().getTransactionManager().commit();
   }

   protected Object getPersonFromClassloader(ClassLoader cl) throws Exception
   {
      Class<?> clazz = cl.loadClass(PERSON_CLASSNAME);
      return clazz.newInstance();
   }
}
