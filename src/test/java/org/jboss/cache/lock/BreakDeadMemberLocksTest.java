/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
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

package org.jboss.cache.lock;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.HashMap;
import java.util.Map;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Tests the breaking of locks held by dead members.
 *
 * @author <a href="mailto://brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7422 $
 */
@Test(groups = {"functional"}, sequential = true, testName = "lock.BreakDeadMemberLocksTest")
public class BreakDeadMemberLocksTest
{
   private Map<String, CacheSPI<Object, Object>> caches;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      caches = new HashMap<String, CacheSPI<Object, Object>>();
   }

   public void testBreakDeadMemberLocks() throws Exception
   {
      CacheSPI<Object, Object> cacheA = createCache("A");

      cacheA.put("/1/A", "1", "A");
      cacheA.put("/1/A", "2", "A");
      cacheA.put("/2/A", "1", "A");
      cacheA.put("/2/A", "2", "A");
      cacheA.put("/1/A/I", "1", "A");
      cacheA.put("/1/A/I", "2", "A");

      CacheSPI<Object, Object> cacheB = createCache("B");

      // Pause to give caches time to see each other
      TestingUtil.blockUntilViewsReceived(new CacheSPI[]{cacheA, cacheB}, 60000);

      final TransactionManager tm = cacheB.getTransactionManager();
      tm.begin();
      final Transaction tx = tm.getTransaction();

      cacheB.put("/1/A", "1", "B");
      cacheB.put("/1/A", "2", "B");
      cacheB.put("/2/A", "1", "B");
      cacheB.put("/2/A", "2", "B");
      cacheB.put("/1/A/I", "1", "B");
      cacheB.put("/1/A/I", "2", "B");
      cacheB.put("/EXISTS", "KEY", "B");

      Object monitor = new Object();
      HangSync sync = new HangSync(monitor);
      tx.registerSynchronization(sync);

      Thread t = new Thread()
      {
         public void run()
         {
            try
            {
               tm.resume(tx);
               tm.commit();
            }
            catch (Exception e)
            {
            }
         }
      };

      synchronized (monitor)
      {
         t.start();

         while (!sync.hung)
         {
            monitor.wait(500);
         }
      }

      tm.suspend();

      // Confirm that B's tx replicated
      assertTrue(cacheA.peek(Fqn.fromString("/EXISTS"), false, false) != null);

      cacheB.stop();
      cacheB.destroy();

      while (cacheA.getMembers().size() > 1)
      {
         try
         {
            Thread.sleep(100);
         }
         catch (InterruptedException e)
         {
         }
      }

      assertEquals("A", cacheA.get("/1/A", "1"));
      assertEquals("A", cacheA.get("/1/A", "2"));
      assertEquals("A", cacheA.get("/2/A", "1"));
      assertEquals("A", cacheA.get("/2/A", "2"));
      assertEquals("A", cacheA.get("/1/A/I", "1"));
      assertEquals("A", cacheA.get("/1/A/I", "2"));

      if (t.isAlive())
      {
         t.interrupt();
      }
   }

   protected CacheSPI<Object, Object> createCache(String cacheID) throws Exception
   {
      if (caches.get(cacheID) != null)
      {
         throw new IllegalStateException(cacheID + " already created");
      }

      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_SYNC), false, getClass());
      cache.getConfiguration().setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);

      cache.create();
      cache.start();

      caches.put(cacheID, cache);

      return cache;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      for (String cacheID : caches.keySet())
      {
         stopCache(cacheID);
      }
      caches.clear();      
   }

   protected void stopCache(String id)
   {
      TestingUtil.killCaches(caches.get(id));
   }

   class HangSync implements Synchronization
   {
      private boolean hung = false;
      private Object monitor;

      HangSync(Object monitor)
      {
         this.monitor = monitor;
      }

      public void afterCompletion(int arg0)
      {
      }

      public void beforeCompletion()
      {
         hung = true;
         synchronized (monitor)
         {
            monitor.notifyAll();
         }
         try
         {
            Thread.sleep(30000);
         }
         catch (InterruptedException e)
         {
         }
      }


   }
}
