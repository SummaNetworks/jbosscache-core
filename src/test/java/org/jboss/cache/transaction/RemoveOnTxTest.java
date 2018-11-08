/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.cache.transaction;

import javax.transaction.TransactionManager;
import javax.transaction.Transaction;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import static org.testng.Assert.*;

/**
 * This is for checking issue: https://jira.jboss.org/jira/browse/JBCACHE-1406
 * This was an issue on 1.4 and 2.x, but didn't reproduce on 3.x.
 * Anyway, the tests is here just in case...
 *
 * @author Mircea.Markus@jboss.com
 */
@Test (groups = "functional", testName = "transaction.RemoveOnTxTest")
public class RemoveOnTxTest
{
   private CacheSPI cache;
   private DataContainerImpl dataContainer;

   @BeforeMethod
   protected void setUp() throws Exception
   {
      Configuration configuration = new Configuration();
      configuration.setCacheMode(Configuration.CacheMode.LOCAL);
      configuration.setTransactionManagerLookupClass("org.jboss.cache.transaction.GenericTransactionManagerLookup");
      configuration.setNodeLockingScheme(Configuration.NodeLockingScheme.PESSIMISTIC);
      cache = (CacheSPI) new UnitTestCacheFactory().createCache(configuration, getClass());
      dataContainer = (DataContainerImpl) cache.getComponentRegistry().getComponent(DataContainer.class);
   }

   @AfterMethod 
   protected void tearDown() throws Exception
   {
      cache.stop();
      cache.destroy();
   }

   public void testFailure() throws Exception
   {

      TransactionManager tm = cache.getTransactionManager();
      try
      {
         tm.begin();
         cache.put("/a/b/c", "test", "test");
         assertTrue(cache.get("/a/b/c", "test").equals("test"));
         cache.removeNode("/a/b");
         assertTrue(!cache.exists("/a/b"));
         assertTrue(!cache.exists("/a/b/c"));
         cache.put("/a/b/d", "test1", "test1");
         assertTrue(!cache.exists("/a/b/c"));
         assertTrue(cache.exists("/a/b/d"));
         tm.commit();
         assertTrue(cache.peek(Fqn.fromString("/a/b/c"), true, true) == null);
         assertTrue(!cache.exists("/a/b/c"));
         assertTrue(cache.exists("/a/b/d"));
         dataContainer.printLockInfo();
      }
      catch (Exception ex) {
         tm.rollback();
         throw ex;
      }
      dataContainer.printLockInfo();
      try
      {
         tm.begin();
         Transaction t = tm.suspend();
         try
         {
            cache.putForExternalRead(Fqn.fromString("/a/b/c"), "test", "test");
         }
         catch (Exception ignore) {
            ignore.printStackTrace();
         }
         tm.resume(t);
         cache.put("/a/b/c", "test", "test");
         tm.commit();
      }
      catch (Exception ex) {
         tm.rollback();
         throw ex;
      }
   }


   public void testReal() throws Exception
   {
      TransactionManager tm = cache.getTransactionManager();
      tm.begin();
      cache.put("/a/b/c", "test", "test");
      assertTrue(cache.get("/a/b/c", "test").equals("test"));
      cache.removeNode("/a/b");
      assertTrue(!cache.exists("/a/b"));
      assertTrue(!cache.exists("/a/b/c"));
      cache.put("/a/b/d", "test1", "test1");
      assertTrue(!cache.exists("/a/b/c"));
      assertTrue(cache.exists("/a/b/d"));
      tm.commit();
      assertNull(cache.peek(Fqn.fromString("/a/b/c"), true, true));
      assertTrue(!cache.exists("/a/b/c"));
      assertTrue(cache.exists("/a/b/d"));
      dataContainer.printLockInfo();
   }

   public void testSimplified() throws Exception
   {
      TransactionManager tm = cache.getTransactionManager();
      tm.begin();
      cache.put("/a/b/c", "test", "test");
      assertTrue(cache.peek(Fqn.fromString("/a/b/c"), true, true) != null);
      cache.removeNode("/a/b");
      tm.commit();
      assertTrue(cache.peek(Fqn.fromString("/a/b/c"), true, true) == null);
   }
}
