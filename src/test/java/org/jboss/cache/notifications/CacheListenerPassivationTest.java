package org.jboss.cache.notifications;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.notifications.event.Event;
import static org.jboss.cache.notifications.event.Event.Type.*;
import org.jboss.cache.notifications.event.EventImpl;
import static org.jboss.cache.notifications.event.NodeModifiedEvent.ModificationType.PUT_DATA;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.cache.UnitTestCacheFactory;

@Test(groups = "functional", sequential = true, testName = "notifications.CacheListenerPassivationTest")
public class CacheListenerPassivationTest
{
   private Cache<Object, Object> cache;
   private EventLog eventLog = new EventLog();
   private TransactionManager tm;
   private Fqn fqn = Fqn.fromString("/test");

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration c = new Configuration();
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());

      CacheLoaderConfig clc = new CacheLoaderConfig();
      IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
      iclc.setClassName(DummyInMemoryCacheLoader.class.getName());
      clc.addIndividualCacheLoaderConfig(iclc);
      clc.setPassivation(true);

      c.setCacheLoaderConfig(clc);
      cache = new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
      eventLog.events.clear();
      cache.addCacheListener(eventLog);
      tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testActivationAndPassivation() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      Map<Object, Object> data = new HashMap<Object, Object>();
      data.put("key", "value");

      List<Event> expected = new ArrayList<Event>();

      // now evict the node - which should cause a passivation
      eventLog.events.clear();
      cache.evict(fqn);
      expected.add(new EventImpl(true, cache, null, data, fqn, null, true, null, false, null, NODE_PASSIVATED));
      expected.add(new EventImpl(false, cache, null, Collections.emptyMap(), fqn, null, true, null, false, null, NODE_PASSIVATED));
      expected.add(new EventImpl(true, cache, null, null, fqn, null, true, null, false, null, NODE_EVICTED));
      expected.add(new EventImpl(false, cache, null, null, fqn, null, true, null, false, null, NODE_EVICTED));
      assertEquals(expected, eventLog.events);

      // now load the node.
      expected.clear();
      eventLog.events.clear();
      cache.get(fqn, "DOES_NOT_EXIST");
      expected.add(new EventImpl(true, cache, null, null, fqn, null, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache, null, null, fqn, null, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(true, cache, null, Collections.emptyMap(), fqn, null, true, null, false, null, NODE_ACTIVATED));
      expected.add(new EventImpl(false, cache, null, data, fqn, null, true, null, false, null, NODE_ACTIVATED));
      expected.add(new EventImpl(true, cache, null, null, fqn, null, true, null, false, null, NODE_VISITED));
      expected.add(new EventImpl(false, cache, null, null, fqn, null, true, null, false, null, NODE_VISITED));

      assertEquals(expected, eventLog.events);
   }

   public void testActivationAndPassivationTxNoMods() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      Map<Object, Object> data = new HashMap<Object, Object>();
      data.put("key", "value");

      List<Event> expected = new ArrayList<Event>();

      // now evict the node - which should cause a passivation
      eventLog.events.clear();
      cache.evict(fqn);
      expected.add(new EventImpl(true, cache, null, data, fqn, null, true, null, false, null, NODE_PASSIVATED));
      expected.add(new EventImpl(false, cache, null, Collections.emptyMap(), fqn, null, true, null, false, null, NODE_PASSIVATED));
      expected.add(new EventImpl(true, cache, null, null, fqn, null, true, null, false, null, NODE_EVICTED));
      expected.add(new EventImpl(false, cache, null, null, fqn, null, true, null, false, null, NODE_EVICTED));
      assertEquals(expected, eventLog.events);

      // now load the node.
      expected.clear();
      eventLog.events.clear();
      tm.begin();
      Transaction tx = tm.getTransaction();
      cache.get(fqn, "DOES_NOT_EXIST");
      tm.commit();
      expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(true, cache, null, Collections.emptyMap(), fqn, tx, true, null, false, null, NODE_ACTIVATED));
      expected.add(new EventImpl(false, cache, null, data, fqn, tx, true, null, false, null, NODE_ACTIVATED));
      expected.add(new EventImpl(true, cache, null, null, fqn, tx, true, null, false, null, NODE_VISITED));
      expected.add(new EventImpl(false, cache, null, null, fqn, tx, true, null, false, null, NODE_VISITED));
      expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog.events);
   }

   public void testActivationAndPassivationTxMods() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      Map<Object, Object> data = new HashMap<Object, Object>();
      data.put("key", "value");

      List<Event> expected = new ArrayList<Event>();

      // now evict the node - which should cause a passivation
      eventLog.events.clear();
      cache.evict(fqn);
      expected.add(new EventImpl(true, cache, null, data, fqn, null, true, null, false, null, NODE_PASSIVATED));
      expected.add(new EventImpl(false, cache, null, Collections.emptyMap(), fqn, null, true, null, false, null, NODE_PASSIVATED));
      expected.add(new EventImpl(true, cache, null, null, fqn, null, true, null, false, null, NODE_EVICTED));
      expected.add(new EventImpl(false, cache, null, null, fqn, null, true, null, false, null, NODE_EVICTED));
      assertEquals(expected, eventLog.events);

      // now load the node.
      expected.clear();
      eventLog.events.clear();
      tm.begin();
      Transaction tx = tm.getTransaction();
      cache.put(fqn, "key2", "value2");
      tm.commit();
      expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(true, cache, null, Collections.emptyMap(), fqn, tx, true, null, false, null, NODE_ACTIVATED));
      expected.add(new EventImpl(false, cache, null, data, fqn, tx, true, null, false, null, NODE_ACTIVATED));
      expected.add(new EventImpl(true, cache, PUT_DATA, data, fqn, tx, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache, PUT_DATA, Collections.singletonMap("key2", "value2"), fqn, tx, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog.events);

      assert cache.get(fqn, "key").equals("value");
      assert cache.get(fqn, "key2").equals("value2");
   }

   public void testActivationAndPassivationTxModsWithChildren() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      cache.put(Fqn.fromRelativeElements(fqn, "child"), "key3", "value3");
      Map<Object, Object> data = new HashMap<Object, Object>();
      data.put("key", "value");

      List<Event> expected = new ArrayList<Event>();

      // now evict the node - which should cause a passivation
      eventLog.events.clear();
      cache.evict(fqn);
      expected.add(new EventImpl(true, cache, null, data, fqn, null, true, null, false, null, NODE_PASSIVATED));
      expected.add(new EventImpl(false, cache, null, Collections.emptyMap(), fqn, null, true, null, false, null, NODE_PASSIVATED));
      expected.add(new EventImpl(true, cache, null, null, fqn, null, true, null, false, null, NODE_EVICTED));
      expected.add(new EventImpl(false, cache, null, null, fqn, null, true, null, false, null, NODE_EVICTED));
      assertEquals(expected, eventLog.events);

      // now load the node.
      expected.clear();
      eventLog.events.clear();
      tm.begin();
      Transaction tx = tm.getTransaction();
      cache.put(fqn, "key2", "value2");
      tm.commit();
      expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache, null, Collections.emptyMap(), fqn, tx, true, null, false, null, NODE_ACTIVATED));
      expected.add(new EventImpl(false, cache, null, data, fqn, tx, true, null, false, null, NODE_ACTIVATED));
      expected.add(new EventImpl(true, cache, PUT_DATA, data, fqn, tx, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache, PUT_DATA, Collections.singletonMap("key2", "value2"), fqn, tx, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog.events);

      assert cache.get(fqn, "key").equals("value");
      assert cache.get(fqn, "key2").equals("value2");
   }
}
