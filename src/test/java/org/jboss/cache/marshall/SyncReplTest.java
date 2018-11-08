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
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.marshall.data.Address;
import org.jboss.cache.marshall.data.Person;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.NotSupportedException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import java.lang.reflect.Method;
import java.util.HashMap;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Test case for marshalling using Sync mode.
 *
 * @author Ben Wang
 * @version $Revision: 7422 $
 */
@Test(groups = {"functional", "jgroups"}, testName = "marshall.SyncReplTest")
public class SyncReplTest extends RegionBasedMarshallingTestBase
{
   
   protected class SyncReplTestTL {
      CacheSPI<Object, Object> cache1, cache2;
      String props = null;
      Person ben_;
      Address addr_;
      Throwable ex_;
   }
   protected ThreadLocal<SyncReplTestTL> threadLocal = new ThreadLocal<SyncReplTestTL>();
   
   private Fqn aop = Fqn.fromString("/aop");
   protected boolean useMarshalledValues = false;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {      
      super.setUp();
      SyncReplTestTL srtl = new SyncReplTestTL();
      threadLocal.set(srtl);
      srtl.cache1 = createCache("TestCache");

      srtl.cache2 = createCache("TestCache");
      srtl.addr_ = new Address();
      srtl.addr_.setCity("San Jose");
      srtl.ben_ = new Person();
      srtl.ben_.setName("Ben");
      srtl.ben_.setAddress(srtl.addr_);

      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{srtl.cache1, srtl.cache2}, 60000);
   }

   private CacheSPI<Object, Object> createCache(String name)
   {
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      cache.getConfiguration().setClusterName(name + "-" + Thread.currentThread().getName());
      // Use marshaller
      cache.getConfiguration().setUseLazyDeserialization(useMarshalledValues);
      cache.getConfiguration().setUseRegionBasedMarshalling(!useMarshalledValues);

      cache.create();
      cache.start();
      return cache;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;
      cache1.removeNode(Fqn.ROOT);
      TestingUtil.killCaches(cache1, cache2);
      super.tearDown();
      threadLocal.set(null);
   }

   public void testPlainPut() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;
      cache1.put(aop, "person", srtl.ben_);
      Person ben2 = (Person) cache2.get(aop, "person");
      assertNotNull("Person from 2nd cache should not be null ", ben2);
      assertEquals(srtl.ben_.toString(), ben2.toString());
   }

   public void testCCE() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;

      ClassLoader c1 = getClassLoader();
      ClassLoader c2 = getClassLoader();

      if (!useMarshalledValues)
      {
         Region r1 = cache1.getRegion(aop, false) == null ? cache1.getRegion(aop, true) : cache1.getRegion(aop, false);
         r1.registerContextClassLoader(c1);


         Region r2 = cache2.getRegion(aop, false) == null ? cache2.getRegion(aop, true) : cache2.getRegion(aop, false);
         r2.registerContextClassLoader(c2);
      }

      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(c1);
      cache1.put(aop, "person", srtl.ben_);
      if (useMarshalledValues) resetContextClassLoader();
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(getFailingClassLoader());
      try
      {
         if (useMarshalledValues) Thread.currentThread().setContextClassLoader(c2);
         @SuppressWarnings("unused")
         Person person = (Person) cache2.get(aop, "person");
      }
      catch (ClassCastException ex)
      {
         // That's ok.
         return;
      }
      fail("Should have thrown an exception");
   }

   public void testPut() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;

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
      cache1.put(aop, "person", srtl.ben_);

      Object ben2;
      // Can't cast it to Person. CCE will result.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      ben2 = cache2.get(aop, "person");
      assertEquals(srtl.ben_.toString(), ben2.toString());
   }

   public void testCLSet() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;

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
      cache1.put(aop, "person", srtl.ben_);

      Object ben2;
      // Can't cast it to Person. CCE will result.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      ben2 = cache2.get(aop, "person");
      assertEquals(srtl.ben_.toString(), ben2.toString());

      Class claz = clb.loadClass(ADDRESS_CLASSNAME);
      Object add = claz.newInstance();
      Method setValue = claz.getMethod("setCity", String.class);
      setValue.invoke(add, "Sunnyvale");
      Class clasz1 = clb.loadClass(PERSON_CLASSNAME);
      setValue = clasz1.getMethod("setAddress", claz);
      setValue.invoke(ben2, add);
   }

   /**
    * Test replication with classloaders.
    *
    * @throws Exception
    */
   public void testCLSet2() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;

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
      cache1.put(aop, "person", srtl.ben_);

      Object ben2;
      // Can't cast it to Person. CCE will result.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      ben2 = cache2.get(aop, "person");
      assertEquals(srtl.ben_.toString(), ben2.toString());

      Class claz = clb.loadClass(ADDRESS_CLASSNAME);
      Object add = claz.newInstance();
      Method setValue = claz.getMethod("setCity", String.class);
      setValue.invoke(add, "Sunnyvale");
      Class clasz1 = clb.loadClass(PERSON_CLASSNAME);
      setValue = clasz1.getMethod("setAddress", claz);
      setValue.invoke(ben2, add);

      // Set it back to the cache
      // Can't cast it to Person. CCE will resutl.
      cache2.put(aop, "person", ben2);

      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(cla);
      Object ben3 = cache1.get(aop, "person");
      assertEquals(ben2.toString(), ben3.toString());
   }

   public void testPuts() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;

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
      cache1.put(Fqn.fromString("/aop/1"), "person", srtl.ben_);
      cache1.put(Fqn.fromString("/aop/2"), "person", scopedBen1);
      if (useMarshalledValues) resetContextClassLoader();
      Object ben2;
      // Can't cast it to Person. CCE will resutl.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      ben2 = cache2.get(Fqn.fromString("/aop/1"), "person");
      if (useMarshalledValues) resetContextClassLoader();
      assertEquals(srtl.ben_.toString(), ben2.toString());

      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      ben2 = cache2.get(Fqn.fromString("/aop/2"), "person");
      if (useMarshalledValues) resetContextClassLoader();
      assertFalse("cache2 deserialized with scoped classloader", ben2 instanceof Person);
      assertFalse("cache2 deserialized with cache2 classloader", scopedBen1.equals(ben2));
      assertEquals("scopedBen deserialized properly", scopedBen2, ben2);
   }

   public void testMethodCall() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;

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
      cache1.put(Fqn.fromString("/aop/1"), "person", srtl.ben_);
      cache1.remove(Fqn.fromString("/aop/1"), "person");
      HashMap<Object, Object> map = new HashMap<Object, Object>();
      map.put("1", "1");
      map.put("2", "2");

      cache1.put(Fqn.fromString("/aop/2"), map);
      cache1.removeNode(Fqn.fromString("/aop/2"));
      if (useMarshalledValues) resetContextClassLoader();
//      TestingUtil.sleepThread(1000);
   }

   public void testTxMethodCall() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;

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
      TransactionManager tm = beginTransaction();
      cache1.put(Fqn.fromString("/aop/1"), "person", srtl.ben_);
      cache1.remove(Fqn.fromString("/aop/1"), "person");
      HashMap<Object, Object> map = new HashMap<Object, Object>();
      map.put("1", "1");
      map.put("2", "2");
      cache1.put(Fqn.fromString("/aop/2"), map);
      cache1.removeNode(Fqn.fromString("/aop/2"));
      tm.commit();

//      TestingUtil.sleepThread(1000);
      if (useMarshalledValues) resetContextClassLoader();
   }

   public void testTxCLSet2() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;

      ClassLoader cla = getClassLoader();
      if (!useMarshalledValues)
      {
         Region r1 = cache1.getRegion(aop, false) == null ? cache1.getRegion(aop, true) : cache1.getRegion(aop, false);
         r1.registerContextClassLoader(cla);
      }
      ClassLoader clb = getClassLoader();
      if (!useMarshalledValues)
      {
         Region r2 = cache2.getRegion(aop, false) == null ? cache2.getRegion(aop, true) : cache2.getRegion(aop, false);
         r2.registerContextClassLoader(clb);
      }

      TransactionManager tm = beginTransaction();
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(cla);
      cache1.put(aop, "person", srtl.ben_);
      tm.commit();
      if (useMarshalledValues) resetContextClassLoader();

      Object ben2;
      // Can't cast it to Person. CCE will resutl.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      ben2 = cache2.get(aop, "person");
      if (useMarshalledValues) resetContextClassLoader();
      assertEquals(srtl.ben_.toString(), ben2.toString());

      Class claz = clb.loadClass(ADDRESS_CLASSNAME);
      Object add = claz.newInstance();
      Method setValue = claz.getMethod("setCity", String.class);
      setValue.invoke(add, "Sunnyvale");
      Class clasz1 = clb.loadClass(PERSON_CLASSNAME);
      setValue = clasz1.getMethod("setAddress", claz);
      setValue.invoke(ben2, add);

      // Set it back to the cache
      // Can't cast it to Person. CCE will resutl.
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(clb);
      cache2.put(aop, "person", ben2);
      if (useMarshalledValues) resetContextClassLoader();
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(cla);
      Object ben3 = cache1.get(aop, "person");
      if (useMarshalledValues) resetContextClassLoader();
      assertEquals(ben2.toString(), ben3.toString());
   }

   public void testStateTransfer() throws Exception
   {
      // Need to test out if app is not registered with beforehand??
   }

   public void testCustomFqn() throws Exception
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;
      CacheSPI<Object, Object> cache2 = srtl.cache2;

      FooClassLoader cl1 = new FooClassLoader(Thread.currentThread().getContextClassLoader());
      Region r1 = cache1.getRegion(aop, false) == null ? cache1.getRegion(aop, true) : cache1.getRegion(aop, false);
      r1.registerContextClassLoader(cl1);
      FooClassLoader cl2 = new FooClassLoader(Thread.currentThread().getContextClassLoader());
      Region r2 = cache2.getRegion(aop, false) == null ? cache2.getRegion(aop, true) : cache2.getRegion(aop, false);
      r2.registerContextClassLoader(cl2);

      Class clazz = cl1.loadFoo();
      Object custom1 = clazz.newInstance();

      clazz = cl2.loadFoo();
      Object custom2 = clazz.newInstance();

      cache1.put(Fqn.fromElements("aop", custom1), "key", "value");

      try
      {
         Object val = cache2.get(Fqn.fromElements("aop", custom2), "key");
         assertEquals("value", val);
      }
      catch (Exception ex)
      {
         fail("Test fails with exception " + ex);
      }
   }

   private TransactionManager beginTransaction() throws SystemException, NotSupportedException
   {
      SyncReplTestTL srtl = threadLocal.get();
      CacheSPI<Object, Object> cache1 = srtl.cache1;

      TransactionManager mgr = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
      mgr.begin();
      return mgr;
   }

   protected Object getPersonFromClassloader(ClassLoader cl) throws Exception
   {
      Class clazz = cl.loadClass(PERSON_CLASSNAME);
      return clazz.newInstance();
   }
}
