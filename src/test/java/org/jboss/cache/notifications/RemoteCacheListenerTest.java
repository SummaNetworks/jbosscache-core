/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.cache.notifications;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.notifications.event.Event;
import static org.jboss.cache.notifications.event.Event.Type.*;
import org.jboss.cache.notifications.event.EventImpl;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import static org.jboss.cache.notifications.event.NodeModifiedEvent.ModificationType.*;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.*;

/**
 * Remote conterpart of CacheListenerTest. Main difference is event is originating as local.
 *
 * @since 2.0.0
 */
@Test(groups = "functional", sequential = true, testName = "notifications.RemoteCacheListenerTest")
public class RemoteCacheListenerTest
{
   protected boolean optLocking = false;

   private Cache<String, String> cache1, cache2;
   @SuppressWarnings("unused")
   private TransactionManager tm1;
   private EventLog eventLog1 = new EventLog(), eventLog2 = new EventLog();
   private final Fqn fqn = Fqn.fromString("/test");

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      if (optLocking)
         c.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      else
         c.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");

      // we need this because notifications emitted by the notification interceptor are done during the commit call.
      // If we want to check notifications on remote nodes we need to make sure the commit completes before we test anything.
      c.setSyncCommitPhase(true);

      // more time to help with debugging
      c.setSyncReplTimeout(60000);

      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache1 = instance.createCache(c, getClass());
      cache2 = instance.createCache(c.clone(), getClass());

      eventLog1.events.clear();
      eventLog2.events.clear();

      cache1.addCacheListener(eventLog1);
      cache2.addCacheListener(eventLog2);

      tm1 = cache1.getConfiguration().getRuntimeConfig().getTransactionManager();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
   }

   /**
    * Make sure the 2 caches have different component instances
    */
   public void testSeparateNotifiersAndListeners()
   {
      assert cache1 != cache2;
      ComponentRegistry cr1, cr2;
      cr1 = TestingUtil.extractComponentRegistry(cache1);
      cr2 = TestingUtil.extractComponentRegistry(cache2);
      assert cr1 != cr2;
      assert cr1.getComponent(Notifier.class) != cr2.getComponent(Notifier.class);
      assert eventLog1 != eventLog2;
      assert cache1.getLocalAddress() != cache2.getLocalAddress();
      CacheSPI spi1, spi2;
      spi1 = (CacheSPI) cache1;
      spi2 = (CacheSPI) cache2;

      assert spi1.getRPCManager() != spi2.getRPCManager();
      assert TestingUtil.extractField(spi1.getRPCManager(), "channel") != TestingUtil.extractField(spi2.getRPCManager(), "channel");
   }

   // simple tests first

   public void testCreation() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      cache1.put(fqn, "key", "value");
      Map<String, String> data = new HashMap<String, String>();
      data.put("key", "value");

      //expectedRemote
      List<Event> expected = new ArrayList<Event>();

      if (optLocking)
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, null, null, fqn, null, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache1, null, null, fqn, null, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(true, cache1, PUT_DATA, Collections.emptyMap(), fqn, null, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache1, PUT_DATA, data, fqn, null, true, null, false, null, NODE_MODIFIED));
      if (optLocking)
      {
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
         eventLog1.scrubImplicitTransactions();
         eventLog2.scrubImplicitTransactions();
      }

      assertEquals("Local events not as expected", expected, eventLog1.events);

      //expectedRemote
      setCache(cache2, expected);
      markOriginRemote(expected);

      assertEquals("Remote events not as expected", expected, eventLog2.events);
      assertEquals("value", cache1.get(fqn, "key"));
   }

   public void testNonexistentRemove() throws Exception
   {
      cache1.removeNode("/does/not/exist");
      List<Event> expected = new ArrayList<Event>();

      if (optLocking)
      {
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
         eventLog1.scrubImplicitTransactions();
         eventLog2.scrubImplicitTransactions();
      }
      assertEquals(expected, eventLog1.events);
      setCache(cache2, expected);
      markOriginRemote(expected);
      assertEquals(expected, eventLog2.events);
   }


   public void testOnlyModification() throws Exception
   {
      assertNull(cache1.get(fqn, "key"));
      assertNull(cache2.get(fqn, "key"));

      cache1.put(fqn, "key", "value");
      Map<String, String> oldData = new HashMap<String, String>();
      oldData.put("key", "value");

      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals("value", cache2.get(fqn, "key"));

      // clear event log
      eventLog1.events.clear();
      eventLog2.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog1.events);
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);

      // modify existing node
      cache1.put(fqn, "key", "value2");
      Map<String, String> newData = new HashMap<String, String>();
      newData.put("key", "value2");

      List<Event> expected = new ArrayList<Event>();
      if (optLocking)
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, PUT_DATA, oldData, fqn, null, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache1, PUT_DATA, newData, fqn, null, true, null, false, null, NODE_MODIFIED));
      if (optLocking)
      {
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
         eventLog1.scrubImplicitTransactions();
         eventLog2.scrubImplicitTransactions();
      }

      assertEquals("Local events not as expected", expected, eventLog1.events);

      //expectedRemote
      setCache(cache2, expected);
      markOriginRemote(expected);

      assertEquals("Remote events not as expected", expected, eventLog2.events);
   }

   public void testOnlyRemoval() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      cache1.put(fqn, "key", "value");
      Map<String, String> oldData = new HashMap<String, String>();
      oldData.put("key", "value");

      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals("value", cache2.get(fqn, "key"));

      // clear event log
      eventLog1.events.clear();
      eventLog2.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog1.events);
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);

      // modify existing node
      cache1.removeNode(fqn);

      List<Event> expected = new ArrayList<Event>();
      if (optLocking)
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, null, oldData, fqn, null, true, null, false, null, NODE_REMOVED));
      expected.add(new EventImpl(false, cache1, null, null, fqn, null, true, null, false, null, NODE_REMOVED));
      if (optLocking)
      {
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
         eventLog1.scrubImplicitTransactions();
         eventLog2.scrubImplicitTransactions();
      }

      assertEquals("Local events not as expected", expected, eventLog1.events);

      //expectedRemote
      setCache(cache2, expected);
      markOriginRemote(expected);

      assertEquals("Remote events not as expected", expected, eventLog2.events);

      // test that the node has in fact been removed.
      assertNull("Should be null", cache1.getRoot().getChild(fqn));
      assertNull("Should be null", cache2.getRoot().getChild(fqn));
   }

   public void testRemoveData() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      cache1.put(fqn, "key", "value");
      cache1.put(fqn, "key2", "value2");
      Map<String, String> oldData = new HashMap<String, String>();
      oldData.put("key", "value");
      oldData.put("key2", "value2");

      // clear event log
      eventLog1.events.clear();
      eventLog2.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog1.events);
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);

      // modify existing node
      cache1.remove(fqn, "key2");
      Map<String, String> removed = new HashMap<String, String>();
      removed.put("key2", "value2");

      List<Event> expected = new ArrayList<Event>();
      if (optLocking)
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, REMOVE_DATA, oldData, fqn, null, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache1, REMOVE_DATA, removed, fqn, null, true, null, false, null, NODE_MODIFIED));
      if (optLocking)
      {
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
         eventLog1.scrubImplicitTransactions();
         eventLog2.scrubImplicitTransactions();
      }

      assertEquals("Local events not as expected", expected, eventLog1.events);

      //expectedRemote
      setCache(cache2, expected);
      markOriginRemote(expected);

      assertEquals("Remote events not as expected", expected, eventLog2.events);
   }

   public void testPutMap() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      Map<String, String> oldData = new HashMap<String, String>();
      oldData.put("key", "value");
      oldData.put("key2", "value2");

      assertNull(cache1.getRoot().getChild(fqn));
      assertNull(cache2.getRoot().getChild(fqn));

      // clear event log
      eventLog1.events.clear();
      eventLog2.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog1.events);
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);

      cache1.put(fqn, oldData);

      List<Event> expected = new ArrayList<Event>();
      if (optLocking)
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, null, null, fqn, null, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache1, null, null, fqn, null, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(true, cache1, PUT_MAP, Collections.emptyMap(), fqn, null, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache1, PUT_MAP, oldData, fqn, null, true, null, false, null, NODE_MODIFIED));
      if (optLocking)
      {
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
         eventLog1.scrubImplicitTransactions();
         eventLog2.scrubImplicitTransactions();
      }

      assertEquals("Local events not as expected", expected, eventLog1.events);

      //expectedRemote
      setCache(cache2, expected);
      markOriginRemote(expected);

      assertEquals("Remote events not as expected", expected, eventLog2.events);
   }

   public void testMove()
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      Fqn newParent = Fqn.fromString("/a");
      cache1.put(fqn, "key", "value");
      cache1.put(newParent, "key", "value");

      Node<String, String> n1 = cache1.getRoot().getChild(fqn);
      Node<String, String> n2 = cache1.getRoot().getChild(newParent);

      eventLog1.events.clear();
      eventLog2.events.clear();// clear events
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog1.events);
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);

      cache1.move(n1.getFqn(), n2.getFqn());
      Fqn newFqn = Fqn.fromRelativeElements(newParent, fqn.getLastElement());

      List<Event> expected = new ArrayList<Event>();
      if (optLocking)
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, null, null, fqn, null, true, newFqn, false, null, NODE_MOVED));
      expected.add(new EventImpl(false, cache1, null, null, fqn, null, true, newFqn, false, null, NODE_MOVED));
      if (optLocking)
      {
         expected.add(new EventImpl(false, cache1, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
         eventLog1.scrubImplicitTransactions();
         eventLog2.scrubImplicitTransactions();
      }

      assertEquals("Local events not as expected", expected, eventLog1.events);

      //expectedRemote
      setCache(cache2, expected);
      markOriginRemote(expected);

      assertEquals("Remote events not as expected", expected, eventLog2.events);
   }

   // -- now the transactional ones

   public void testTxCreationCommit() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      tm1.begin();
      Transaction tx = tm1.getTransaction();
      cache1.put(fqn, "key", "value");
      Map<String, String> data = new HashMap<String, String>();
      data.put("key", "value");

      List<Event> expected = new ArrayList<Event>();
      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache1, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(true, cache1, PUT_DATA, Collections.emptyMap(), fqn, tx, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache1, PUT_DATA, data, fqn, tx, true, null, false, null, NODE_MODIFIED));

      assertEquals(expected, eventLog1.events);
      assertTrue(eventLog2.events.isEmpty());

      tm1.commit();

      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog1.events);

      setCache(cache2, expected);
      markOriginRemote(expected);
      scrubTransactions(expected);
      eventLog2.scrubImplicitTransactions();
      assertEquals(expected, eventLog2.events);

      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals("value", cache2.get(fqn, "key"));
   }

   public void testTxNonexistentRemove() throws Exception
   {
      tm1.begin();
      Transaction tx = tm1.getTransaction();
      cache1.removeNode("/does/not/exist");
      tm1.commit();
      List<Event> expected = new ArrayList<Event>();
      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog1.events);
      setCache(cache2, expected);
      markOriginRemote(expected);
      scrubTransactions(expected);
      eventLog2.scrubImplicitTransactions();
      assertEquals(expected, eventLog2.events);
   }

   public void testTxCreationRollback() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      tm1.begin();
      Transaction tx = tm1.getTransaction();
      cache1.put(fqn, "key", "value");
      Map<String, String> data = new HashMap<String, String>();
      data.put("key", "value");
      List<Event> expected = new ArrayList<Event>();
      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache1, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(true, cache1, PUT_DATA, Collections.emptyMap(), fqn, tx, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache1, PUT_DATA, data, fqn, tx, true, null, false, null, NODE_MODIFIED));

      assertEquals(expected, eventLog1.events);
      assertTrue(eventLog2.events.isEmpty());
      tm1.rollback();

      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, false, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog1.events);
      assertTrue(eventLog2.events.isEmpty());

      assertNull(cache1.get(fqn, "key"));
      assertNull(cache2.get(fqn, "key"));
   }

   public void testTxOnlyModification() throws Exception
   {
      assertNull(cache1.get(fqn, "key"));
      assertNull(cache2.get(fqn, "key"));

      cache1.put(fqn, "key", "value");
      Map<String, String> oldData = new HashMap<String, String>();
      oldData.put("key", "value");

      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals("value", cache2.get(fqn, "key"));

      // clear event log
      eventLog1.events.clear();
      eventLog2.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog1.events);
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);

      // modify existing node
      tm1.begin();
      Transaction tx = tm1.getTransaction();
      Map<String, String> newData = new HashMap<String, String>();
      newData.put("key", "value2");
      cache1.put(fqn, "key", "value2");
      List<Event> expected = new ArrayList<Event>();
      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, PUT_DATA, oldData, fqn, tx, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache1, PUT_DATA, newData, fqn, tx, true, null, false, null, NODE_MODIFIED));
      assertEquals(expected, eventLog1.events);

      assertEquals("Events log should be empty until commit time", 0, eventLog2.events.size());
      tm1.commit();

      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog1.events);

      setCache(cache2, expected);
      markOriginRemote(expected);
      scrubTransactions(expected);
      eventLog2.scrubImplicitTransactions();
      assertEquals(expected, eventLog2.events);
   }

   public void testTxOnlyRemoval() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      cache1.put(fqn, "key", "value");
      Map<String, String> oldData = new HashMap<String, String>();
      oldData.put("key", "value");

      assertEquals("value", cache1.get(fqn, "key"));
      assertEquals("value", cache2.get(fqn, "key"));

      // clear event log
      eventLog1.events.clear();
      eventLog2.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog1.events);
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);

      // modify existing node
      tm1.begin();
      Transaction tx = tm1.getTransaction();
      cache1.removeNode(fqn);
      List<Event> expected = new ArrayList<Event>();
      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, null, oldData, fqn, tx, true, null, false, null, NODE_REMOVED));
      expected.add(new EventImpl(false, cache1, null, null, fqn, tx, true, null, false, null, NODE_REMOVED));
      assertEquals(expected, eventLog1.events);
      assertEquals("Events log should be empty until commit time", 0, eventLog2.events.size());
      tm1.commit();

      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog1.events);

      setCache(cache2, expected);
      markOriginRemote(expected);
      scrubTransactions(expected);
      eventLog2.scrubImplicitTransactions();
      assertEquals(expected, eventLog2.events);

      // test that the node has in fact been removed.
      assertNull("Should be null", cache1.getRoot().getChild(fqn));
      assertNull("Should be null", cache2.getRoot().getChild(fqn));
   }

   public void testTxRemoveData() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      cache1.put(fqn, "key", "value");
      cache1.put(fqn, "key2", "value2");
      Map<String, String> oldData = new HashMap<String, String>();
      oldData.put("key", "value");
      oldData.put("key2", "value2");

      // clear event log
      eventLog1.events.clear();
      eventLog2.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog1.events);
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);

      // modify existing node
      tm1.begin();
      Transaction tx = tm1.getTransaction();
      Map<String, String> removed = new HashMap<String, String>();
      removed.put("key2", "value2");
      cache1.remove(fqn, "key2");
      List<Event> expected = new LinkedList<Event>();
      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, REMOVE_DATA, oldData, fqn, tx, true, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache1, REMOVE_DATA, removed, fqn, tx, true, null, false, null, NODE_MODIFIED));
      assertEquals(expected, eventLog1.events);
      assertEquals("Events log should be empty until commit time", 0, eventLog2.events.size());
      tm1.commit();

      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog1.events);

      setCache(cache2, expected);
      markOriginRemote(expected);
      scrubTransactions(expected);
      eventLog2.scrubImplicitTransactions();
      assertEquals(expected, eventLog2.events);

   }

   public void testTxMove() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);
      Fqn newParent = Fqn.fromString("/a");
      cache1.put(fqn, "key", "value");
      cache1.put(newParent, "key", "value");

      Node<String, String> n1 = cache1.getRoot().getChild(fqn);
      Node<String, String> n2 = cache1.getRoot().getChild(newParent);

      eventLog1.events.clear();
      eventLog2.events.clear();// clear events
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog1.events);
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog2.events);

      tm1.begin();
      Transaction tx = tm1.getTransaction();
      Fqn newFqn = Fqn.fromRelativeElements(newParent, fqn.getLastElement());
      cache1.move(n1.getFqn(), n2.getFqn());
      List<Event> expected = new ArrayList<Event>();
      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache1, null, null, fqn, tx, true, newFqn, false, null, NODE_MOVED));
      expected.add(new EventImpl(false, cache1, null, null, fqn, tx, true, newFqn, false, null, NODE_MOVED));
      assertEquals(expected.size(), eventLog1.events.size());
      assertEquals(expected, eventLog1.events);
      assertEquals("Events log should be empty until commit time", 0, eventLog2.events.size());
      tm1.commit();

      expected.add(new EventImpl(false, cache1, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog1.events);

      setCache(cache2, expected);
      markOriginRemote(expected);
      scrubTransactions(expected);
      eventLog2.scrubImplicitTransactions();
      assertEquals(expected, eventLog2.events);
   }

   public void testStateTransfer() throws Exception
   {
      // first stop cache2
      TestingUtil.killCaches(cache2);
      // wait till cache2 has disappeared.
      TestingUtil.blockUntilViewsReceived(5000, false, cache1);

      // get some state in cache1
      Fqn fqnA = Fqn.fromString("/a");
      Fqn fqnB = Fqn.fromString("/a/b");
      Map<String, String> data = Collections.singletonMap("k", "v");
      cache1.put(fqnA, data);
      cache1.put(fqnB, data);

      // create cache2
      UnitTestCacheFactory<String, String> instance = new UnitTestCacheFactory<String, String>();
      cache2 = instance.createCache(cache1.getConfiguration().clone(), false, getClass());
      cache2.create();
      eventLog2.events.clear();
      cache2.addCacheListener(eventLog2);
      cache2.start(); // should initiate a state transfer

      // wait until cache2 has joined the cluster
      TestingUtil.blockUntilViewsReceived(5000, cache1, cache2);

      List<Event> expected = new ArrayList<Event>();
      expected.add(new EventImpl(true, cache2, null, null, Fqn.ROOT, null, false, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache2, null, null, Fqn.ROOT, null, false, null, false, null, NODE_CREATED));

      expected.add(new EventImpl(true, cache2, null, null, fqnA, null, false, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache2, null, null, fqnA, null, false, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(true, cache2, NodeModifiedEvent.ModificationType.PUT_MAP, Collections.emptyMap(), fqnA, null, false, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache2, NodeModifiedEvent.ModificationType.PUT_MAP, data, fqnA, null, false, null, false, null, NODE_MODIFIED));

      expected.add(new EventImpl(true, cache2, null, null, fqnB, null, false, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(false, cache2, null, null, fqnB, null, false, null, false, null, NODE_CREATED));
      expected.add(new EventImpl(true, cache2, NodeModifiedEvent.ModificationType.PUT_MAP, Collections.emptyMap(), fqnB, null, false, null, false, null, NODE_MODIFIED));
      expected.add(new EventImpl(false, cache2, NodeModifiedEvent.ModificationType.PUT_MAP, data, fqnB, null, false, null, false, null, NODE_MODIFIED));


      scrubTransactions(expected);
      assertEquals(expected, eventLog2.events);
   }

   private void setCache(Cache<String, String> c, List<Event> l)
   {
      for (Event e : l)
         ((EventImpl) e).setCache(c);
   }

   private void markOriginRemote(List<Event> l)
   {
      for (Event e : l)
         ((EventImpl) e).setOriginLocal(false);
   }

   private void scrubTransactions(List<Event> l)
   {
      for (Event e : l)
         ((EventImpl) e).setTransaction(null);
   }
}
