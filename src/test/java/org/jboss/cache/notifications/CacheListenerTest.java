/*****************************************
 *                                       *
 *  JBoss Portal: The OpenSource Portal  *
 *                                       *
 *   Distributable under LGPL license.   *
 *   See terms of license at gnu.org.    *
 *                                       *
 *****************************************/
package org.jboss.cache.notifications;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Node;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Option;
import org.jboss.cache.lock.IsolationLevel;
import org.jboss.cache.notifications.event.Event;
import static org.jboss.cache.notifications.event.Event.Type.*;
import org.jboss.cache.notifications.event.EventImpl;
import static org.jboss.cache.notifications.event.NodeModifiedEvent.ModificationType.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
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
import org.jboss.cache.util.TestingUtil;

/**
 * Note that this is significantly different from the old <b>TreeCacheListenerTest</b> of the JBoss Cache 1.x series, and
 * exercises the new CacheListener annotation.
 *
 * @since 2.0.0
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
@Test(groups = "functional", sequential = true, testName = "notifications.CacheListenerTest")
public class CacheListenerTest
{
   protected boolean optLocking = false;

   private Cache<Object, Object> cache;
   private TransactionManager tm;
   private EventLog eventLog = new EventLog();
   private Fqn fqn = Fqn.fromString("/test");

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration c = new Configuration();
      c.setCacheMode(Configuration.CacheMode.LOCAL);
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      c.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      if (optLocking)
         c.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      cache = new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
      tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();
      eventLog.events.clear();
      cache.addCacheListener(eventLog);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      Transaction t = tm.getTransaction();
      if (t != null)
         tm.rollback();
      TestingUtil.killCaches(cache);
      cache = null;

   }

   // simple tests first

   public void testCreation()
   {
      creation(false);
      eventLog.events.clear();
      creation(true);
   }
   
   protected void creation(boolean supressEventNotification)
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      cache.put(fqn, "key", "value");
      Map<Object, Object> data = new HashMap<Object, Object>();
      data.put("key", "value");

      //expected
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      {
         if (optLocking)
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, null, null, fqn, null, true, null, false, null, NODE_CREATED));
         expected.add(new EventImpl(false, cache, null, null, fqn, null, true, null, false, null, NODE_CREATED));
         expected.add(new EventImpl(true, cache, PUT_DATA, Collections.emptyMap(), fqn, null, true, null, false, null, NODE_MODIFIED));
         expected.add(new EventImpl(false, cache, PUT_DATA, data, fqn, null, true, null, false, null, NODE_MODIFIED));
         if (optLocking)
         {
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
            eventLog.scrubImplicitTransactions();
         }         
      }
      assertEquals(expected, eventLog.events);
      assertEquals("value", cache.get(fqn, "key"));      
   }

   public void testOnlyModification()
   {
      onlyModification(false);
      eventLog.events.clear();
      onlyModification(true);
   }
   
   protected void onlyModification(boolean supressEventNotification)
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      Map<Object, Object> oldData = new HashMap<Object, Object>();
      oldData.put("key", "value");

      // clear Event log
      eventLog.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      // modify existing node
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      cache.put(fqn, "key", "value2");
      Map<Object, Object> newData = new HashMap<Object, Object>();
      newData.put("key", "value2");

      //expected
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      {
         if (optLocking)
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, PUT_DATA, oldData, fqn, null, true, null, false, null, NODE_MODIFIED));
         expected.add(new EventImpl(false, cache, PUT_DATA, newData, fqn, null, true, null, false, null, NODE_MODIFIED));
         if (optLocking)
         {
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
            eventLog.scrubImplicitTransactions();
         }
      }

      assertEquals(expected.size(), eventLog.events.size());
      assertEquals(expected, eventLog.events);
   }

   public void testOnlyRemoval()
   {
      onlyRemoval(false);
      eventLog.events.clear();
      onlyRemoval(true);
   }
   
   protected void onlyRemoval(boolean supressEventNotification)
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      Map<Object, Object> oldData = new HashMap<Object, Object>();
      oldData.put("key", "value");

      assertEquals("value", cache.get(fqn, "key"));

      // clear Event log
      eventLog.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      // modify existing node
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      cache.removeNode(fqn);

      //expected
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      {
         if (optLocking)
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, null, oldData, fqn, null, true, null, false, null, NODE_REMOVED));
         expected.add(new EventImpl(false, cache, null, null, fqn, null, true, null, false, null, NODE_REMOVED));
         if (optLocking)
         {
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
            eventLog.scrubImplicitTransactions();
         }         
      }

      assertEquals(expected, eventLog.events);

      // test that the node has in fact been removed.
      assertNull("Should be null", cache.getRoot().getChild(fqn));
   }

   public void testNonexistentRemove()
   {
      nonexistentRemove(false);
      eventLog.events.clear();
      nonexistentRemove(true);
   }
   
   protected void nonexistentRemove(boolean supressEventNotification)
   {
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      cache.removeNode("/does/not/exist");
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      {
         if (optLocking)
         {
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
            eventLog.scrubImplicitTransactions();
         }
      }
      assertEquals(expected, eventLog.events);
   }

   public void testRemoveData()
   {
      removeData(false);
      eventLog.events.clear();
      removeData(true);
   }
   
   protected void removeData(boolean supressEventNotification)
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      cache.put(fqn, "key2", "value2");
      Map<Object, Object> oldData = new HashMap<Object, Object>();
      oldData.put("key", "value");
      oldData.put("key2", "value2");

      // clear Event log
      eventLog.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      // modify existing node
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      cache.remove(fqn, "key2");
      Map<Object, Object> removedData = new HashMap<Object, Object>();
      removedData.put("key2", "value2");

      //expected
      List<Event> expected = new ArrayList<Event>();

      if (!supressEventNotification)
      {
         if (optLocking)
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, REMOVE_DATA, oldData, fqn, null, true, null, false, null, NODE_MODIFIED));
         expected.add(new EventImpl(false, cache, REMOVE_DATA, removedData, fqn, null, true, null, false, null, NODE_MODIFIED));
         if (optLocking)
         {
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
            eventLog.scrubImplicitTransactions();
         }
      }

      assertEquals(expected, eventLog.events);
   }

   public void testPutMap() throws Exception
   {
      putMap(false);
      eventLog.events.clear();
      putMap(true);      
   }
   
   protected void putMap(boolean supressEventNotification)
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      Map<Object, Object> oldData = new HashMap<Object, Object>();
      oldData.put("key", "value");
      oldData.put("key2", "value2");

      // clear Event log
      eventLog.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      // modify existing node
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      cache.put(fqn, oldData);

      //expected
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      {      
         if (optLocking)
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, null, null, fqn, null, true, null, false, null, NODE_CREATED));
         expected.add(new EventImpl(false, cache, null, null, fqn, null, true, null, false, null, NODE_CREATED));
         expected.add(new EventImpl(true, cache, PUT_MAP, Collections.emptyMap(), fqn, null, true, null, false, null, NODE_MODIFIED));
         expected.add(new EventImpl(false, cache, PUT_MAP, oldData, fqn, null, true, null, false, null, NODE_MODIFIED));
         if (optLocking)
         {
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
            eventLog.scrubImplicitTransactions();
         }
      }

      assertEquals(expected, eventLog.events);
   }

   public void testMove()
   {
      move(false);
      eventLog.events.clear();
      move(true);      
   }
   
   protected void move(boolean supressEventNotification)
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      Fqn newParent = Fqn.fromString("/a");
      cache.put(fqn, "key", "value");
      cache.put(newParent, "key", "value");

      Node<Object, Object> n1 = cache.getRoot().getChild(fqn);
      Node<Object, Object> n2 = cache.getRoot().getChild(newParent);
      eventLog.events.clear();// clear events
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      cache.move(n1.getFqn(), n2.getFqn());
      //expected
      Fqn newFqn = Fqn.fromRelativeElements(newParent, fqn.getLastElement());
      List<Event> expected = new ArrayList<Event>();

      if (!supressEventNotification)
      { 
         if (optLocking)
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, null, null, fqn, null, true, newFqn, false, null, NODE_MOVED));
         expected.add(new EventImpl(false, cache, null, null, fqn, null, true, newFqn, false, null, NODE_MOVED));
         if (optLocking)
         {
            expected.add(new EventImpl(false, cache, null, null, null, null, true, null, true, null, TRANSACTION_COMPLETED));
            eventLog.scrubImplicitTransactions();
         }
      }

      assertEquals(expected, eventLog.events);
   }

   // -- now the transactional ones

   public void testTxNonexistentRemove() throws Exception
   {
      txNonexistentRemove(false);
      eventLog.events.clear();
      txNonexistentRemove(true);
   }

   protected void txNonexistentRemove(boolean supressEventNotification) throws Exception
   {
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      tm.begin();
      Transaction tx = tm.getTransaction();
      cache.removeNode("/does/not/exist");
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      tm.commit();
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      { 
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      }
      assertEquals(expected, eventLog.events);
   }
   
   public void testTxCreationCommit() throws Exception
   {
      txCreationCommit(false);
      eventLog.events.clear();
      txCreationCommit(true);
   }
   
   protected void txCreationCommit(boolean supressEventNotification) throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      tm.begin();
      Transaction tx = tm.getTransaction();
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      cache.put(fqn, "key", "value");
      //expected
      Map<Object, Object> data = new HashMap<Object, Object>();
      data.put("key", "value");
      List<Event> expected = new ArrayList<Event>();

      if (!supressEventNotification)
      { 
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
         expected.add(new EventImpl(false, cache, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
         expected.add(new EventImpl(true, cache, PUT_DATA, Collections.emptyMap(), fqn, tx, true, null, false, null, NODE_MODIFIED));
         expected.add(new EventImpl(false, cache, PUT_DATA, data, fqn, tx, true, null, false, null, NODE_MODIFIED));
      }
      assertEquals(expected, eventLog.events);
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      tm.commit();
      if (!supressEventNotification)
      { 
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      }
      assertEquals(expected, eventLog.events);
      assertEquals("value", cache.get(fqn, "key"));
   }

   public void testTxCreationRollback() throws Exception
   {
      txCreationRollback(false);
      eventLog.events.clear();
      txCreationRollback(true);
   }
   
   protected void txCreationRollback(boolean supressEventNotification) throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      tm.begin();
      Transaction tx = tm.getTransaction();
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      cache.put(fqn, "key", "value");
      //expected
      Map<Object, Object> data = new HashMap<Object, Object>();
      data.put("key", "value");
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      {       
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
         expected.add(new EventImpl(false, cache, null, null, fqn, tx, true, null, false, null, NODE_CREATED));
         expected.add(new EventImpl(true, cache, PUT_DATA, Collections.emptyMap(), fqn, tx, true, null, false, null, NODE_MODIFIED));
         expected.add(new EventImpl(false, cache, PUT_DATA, data, fqn, tx, true, null, false, null, NODE_MODIFIED));
      }

      assertEquals(expected, eventLog.events);
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      tm.rollback();
      if (!supressEventNotification)
      {       
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_COMPLETED));
      }
      assertEquals(expected, eventLog.events);      
   }

   public void testTxOnlyModification() throws Exception
   {
      txOnlyModification(false);
      eventLog.events.clear();
      txOnlyModification(true);
   }
   
   protected void txOnlyModification(boolean supressEventNotification) throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      Map<Object, Object> oldData = new HashMap<Object, Object>();
      oldData.put("key", "value");

      // clear Event log
      eventLog.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      // modify existing node
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }        
      tm.begin();
      Transaction tx = tm.getTransaction();
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      cache.put(fqn, "key", "value2");
      Map<Object, Object> newData = new HashMap<Object, Object>();
      newData.put("key", "value2");

      //expected
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      {        
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, PUT_DATA, oldData, fqn, tx, true, null, false, null, NODE_MODIFIED));
         expected.add(new EventImpl(false, cache, PUT_DATA, newData, fqn, tx, true, null, false, null, NODE_MODIFIED));
      }

      assertEquals(expected, eventLog.events);
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      tm.commit();
      if (!supressEventNotification)
      {        
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      }
      assertEquals(expected, eventLog.events);
   }   

   public void testTxOnlyRemoval() throws Exception
   {
      txOnlyRemoval(false);
      eventLog.events.clear();
      txOnlyRemoval(true);
   }

   protected void txOnlyRemoval(boolean supressEventNotification) throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      Map<Object, Object> oldData = new HashMap<Object, Object>();
      oldData.put("key", "value");

      assertEquals("value", cache.get(fqn, "key"));

      // clear Event log
      eventLog.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      // modify existing node
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      tm.begin();
      Transaction tx = tm.getTransaction();
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      cache.removeNode(fqn);
      //expected
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      {        
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, null, oldData, fqn, tx, true, null, false, null, NODE_REMOVED));
         expected.add(new EventImpl(false, cache, null, null, fqn, tx, true, null, false, null, NODE_REMOVED));
      }

      assertEquals(expected, eventLog.events);
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      tm.commit();
      if (!supressEventNotification)
      {              
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      }
      assertEquals(expected, eventLog.events);
      // test that the node has in fact been removed.
      assertNull("Should be null", cache.getRoot().getChild(fqn));
   }

   public void testTxRemoveData() throws Exception
   {
      txRemoveData(false);
      eventLog.events.clear();
      txRemoveData(true);
   }
   
   protected void txRemoveData(boolean supressEventNotification) throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      cache.put(fqn, "key", "value");
      cache.put(fqn, "key2", "value2");
      Map<Object, Object> oldData = new HashMap<Object, Object>();
      oldData.put("key", "value");
      oldData.put("key2", "value2");

      // clear Event log
      eventLog.events.clear();
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      // modify existing node
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }       
      tm.begin();
      Transaction tx = tm.getTransaction();
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }       
      cache.remove(fqn, "key2");
      Map<Object, Object> removedData = new HashMap<Object, Object>();
      removedData.put("key2", "value2");

      //expected
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      { 
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, REMOVE_DATA, oldData, fqn, tx, true, null, false, null, NODE_MODIFIED));
         expected.add(new EventImpl(false, cache, REMOVE_DATA, removedData, fqn, tx, true, null, false, null, NODE_MODIFIED));
      }

      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      tm.commit();
      if (!supressEventNotification)
      { 
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      }
      assertEquals(expected, eventLog.events);

      assertEquals(expected, eventLog.events);
   }

   public void testTxMove() throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      Fqn newParent = Fqn.fromString("/a");
      cache.put(fqn, "key", "value");
      cache.put(newParent, "key", "value");

      Node<Object, Object> n1 = cache.getRoot().getChild(fqn);
      Node<Object, Object> n2 = cache.getRoot().getChild(newParent);
      eventLog.events.clear();// clear events
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      tm.begin();
      Transaction tx = tm.getTransaction();
      cache.move(n1.getFqn(), n2.getFqn());
      //expected
      Fqn newFqn = Fqn.fromRelativeElements(newParent, fqn.getLastElement());
      List<Event> expected = new ArrayList<Event>();
      expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
      expected.add(new EventImpl(true, cache, null, null, fqn, tx, true, newFqn, false, null, NODE_MOVED));
      expected.add(new EventImpl(false, cache, null, null, fqn, tx, true, newFqn, false, null, NODE_MOVED));

      assertEquals(expected, eventLog.events);
      tm.commit();
      expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      assertEquals(expected, eventLog.events);
   }
   
   protected void txMove(boolean supressEventNotification) throws Exception
   {
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);
      Fqn newParent = Fqn.fromString("/a");
      cache.put(fqn, "key", "value");
      cache.put(newParent, "key", "value");

      Node<Object, Object> n1 = cache.getRoot().getChild(fqn);
      Node<Object, Object> n2 = cache.getRoot().getChild(newParent);
      eventLog.events.clear();// clear events
      assertEquals("Event log should be empty", Collections.emptyList(), eventLog.events);

      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      tm.begin();
      Transaction tx = tm.getTransaction();
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }      
      cache.move(n1.getFqn(), n2.getFqn());
      //expected
      Fqn newFqn = Fqn.fromRelativeElements(newParent, fqn.getLastElement());
      List<Event> expected = new ArrayList<Event>();
      if (!supressEventNotification)
      {       
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, false, null, TRANSACTION_REGISTERED));
         expected.add(new EventImpl(true, cache, null, null, fqn, tx, true, newFqn, false, null, NODE_MOVED));
         expected.add(new EventImpl(false, cache, null, null, fqn, tx, true, newFqn, false, null, NODE_MOVED));
      }

      assertEquals(expected, eventLog.events);
      if (supressEventNotification)
      {
         setSuppressEventNotification();
      }
      tm.commit();
      if (!supressEventNotification)
      {       
         expected.add(new EventImpl(false, cache, null, null, null, tx, true, null, true, null, TRANSACTION_COMPLETED));
      }
      assertEquals(expected, eventLog.events);
   }
   
   protected void setSuppressEventNotification()
   {
      Option option = new Option();
      option.setSuppressEventNotification(true);
      cache.getInvocationContext().setOptionOverrides(option);      
   }
}
