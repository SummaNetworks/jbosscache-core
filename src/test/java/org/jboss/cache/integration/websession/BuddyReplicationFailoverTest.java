/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.cache.integration.websession;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheManager;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.buddyreplication.BuddyReplicationTestsBase;
import org.jboss.cache.commands.write.PutDataMapCommand;
import org.jboss.cache.commands.write.RemoveNodeCommand;
import org.jboss.cache.commands.remote.DataGravitationCleanupCommand;
import org.jboss.cache.integration.websession.util.*;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.FileCacheLoaderConfig;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author Brian Stansberry
 *
 * This test is disabled because of following:
 * There seem to be some assumptions in the above tests that do not look right to me.
 * I have an example I've investigated(logs etc), haven't studied all possible failure scenarios, though I can imagine some others.
 * One of them is the next one, on testInvalidateOnFailoverToBackup:

 * This is the code I am talking about:
 *     InvalidationServlet invs = new InvalidationServlet();
 *     MultipleActionServlet mas = new MultipleActionServlet(sas, invs);
 *     (....)
 *     req = new Request(mgr0, sessionId, mas);
 *     *1+2* req.execute();          (...)
 *     *3*BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr0.getCache());

 *And some explanation

 *1) manager0.invalidatesSession (i.e. cache0.remove)
 *2) manager0 tries to data gravitate session (i.e. cache0.get with forceDataGrav set to true). This is done last line in Request.execute().
 *3) asserts that session is no longer present on cache0

 *Now, the assumption at 3 is not necessarily valid. With some funny threading and using ASYNC replication, you might have the remote removeNode command originated from cache0 to run *after* gravitateData originated at step2. I.e. RemoveNodeCommand being executed after GravitateDataCommand on cache*1* (buddy of cache0).
 */
@Test(groups = "integration",enabled = false, testName = "integration.websession.BuddyReplicationFailoverTest")
public class BuddyReplicationFailoverTest extends WebSessionTestBase
{
   public static final String KEY = "key";
   public static final String FILE_CL_ROOT_DIR = "./testFiles/BuddyReplicationFailoverTest";
   private static int FOLDER_INDEX = 0;

   ReplicationListener[] replListeners;

   @Override
   protected String getCacheConfigName()
   {
      return "br-standard-session-cache";
   }

   @Override
   protected int getNumCacheManagers()
   {
      return 4;
   }
   
   @Override
   protected boolean getCreateManagersInSetup()
   {
      return true;
   }

   @BeforeClass(alwaysRun = true)
   public void beforeClass() throws Exception
   {
      super.beforeClass();

      replListeners = new ReplicationListener[getNumCacheManagers()];
      /* Make sure that the buddy group is formed before starting the tests */
      List<Cache> createdCaches = new ArrayList<Cache>();
      for (int i = 0; i <  getCacheManagers().size(); i++)
      {
         Cache cache = getCacheManagers().get(i).getCache(getCacheConfigName(), false);
         createdCaches.add(cache);
         replListeners[i] = ReplicationListener.getReplicationListener(cache);
      }
      BuddyReplicationTestsBase.waitForSingleBuddy(createdCaches);
   }

   protected void amendCacheBeforeStartup(Cache<Object, Object> cache)
   {
      FileCacheLoaderConfig config = (FileCacheLoaderConfig) cache.getConfiguration().getCacheLoaderConfig().getFirstCacheLoaderConfig();
      config.setLocation(FILE_CL_ROOT_DIR + "/session" + FOLDER_INDEX ++);
   }

   @BeforeMethod
   public void clearCacheLoader() throws Exception
   {
      for (CacheManager mgr: getCacheManagers())
      {
         CacheSPI cache = (CacheSPI) mgr.getCache(getCacheConfigName(), false);
         CacheLoader cl = cache.getCacheLoaderManager().getCacheLoader();
         cl.remove(Fqn.ROOT);
      }
   }

   @AfterTest
   public void removeFileClassLoaderDirs()
   {
      TestingUtil.recursiveFileRemove(FILE_CL_ROOT_DIR);
   }

   public void testFailoverAndImmediateInvalidate() throws Exception
   {
      int attr = 0;
      
      SessionManager mgr0 = getSessionManagers().get(0);
      SessionManager mgr1 = getSessionManagers().get(1);
      SessionManager mgr2 = getSessionManagers().get(2);
      SessionManager mgr3 = getSessionManagers().get(3);
      
      String contextHostName = mgr0.getContextHostName();

      // Create the session
      SetAttributesServlet sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      replListeners[1].expectWithTx(PutDataMapCommand.class);
      
      Request req = new Request(mgr0, null, sas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      
      String sessionId = sas.getSessionId();
      assert sessionId != null : "session id is null";
      // validate cache contents
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr0.getCache(), mgr1.getCache());
      
      // Modify the session
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++))); 
      replListeners[1].expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr0, sessionId, sas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      
      // Fail over; request reads the session and then modifies the session
      GetAttributesServlet gas = new GetAttributesServlet(Collections.singleton(KEY));
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));   
      MultipleActionServlet mas = new MultipleActionServlet(gas, sas);
      replListeners[0].expectWithTx(PutDataMapCommand.class);
      replListeners[0].expect(DataGravitationCleanupCommand.class);
      replListeners[1].expect(DataGravitationCleanupCommand.class);
      
      req = new Request(mgr3, sessionId, mas);
      req.execute();
      replListeners[0].waitForReplicationToOccur();
      replListeners[1].waitForReplicationToOccur();

      assert sessionId.equals(mas.getSessionId()) : "wrong session id; expected " + sessionId + " got " + mas.getSessionId();
      Integer integer = (Integer) gas.getReadAttributes().get(KEY);
      assert integer != null : "null attribute value";
      assert integer.intValue() == (attr - 2) : "wrong val " + integer + " expected " + (attr -2);
      
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr3.getCache(), mgr0.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr1.getCache());
      
      // Invalidate the session
      InvalidationServlet invs = new InvalidationServlet();
      replListeners[0].expectWithTx(RemoveNodeCommand.class);
      
      req = new Request(mgr3, sessionId, invs);
      req.execute();
      replListeners[0].waitForReplicationToOccur();

      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr0.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr3.getCache());
   }
   
   public void testFailoverToOwnerAndImmediatelyInvalidate() throws Exception
   {
      int attr = 0;
      
      SessionManager mgr0 = getSessionManagers().get(0);
      SessionManager mgr1 = getSessionManagers().get(1);
      SessionManager mgr2 = getSessionManagers().get(2);
      SessionManager mgr3 = getSessionManagers().get(3);
      
      String contextHostName = mgr0.getContextHostName();

      // Create the session
      SetAttributesServlet sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      replListeners[2].expectWithTx(PutDataMapCommand.class);
      
      Request req = new Request(mgr1, null, sas);
      req.execute();      
      replListeners[2].waitForReplicationToOccur();
      
      String sessionId = sas.getSessionId();
      assert sessionId != null : "session id is null";
      // validate cache contents
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr1.getCache(), mgr2.getCache());
      
      // Modify the session
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++))); 
      replListeners[2].expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr1, sessionId, sas);
      req.execute();      
      replListeners[2].waitForReplicationToOccur();
      
      // Fail over; request reads the session and then modifies the session
      GetAttributesServlet gas = new GetAttributesServlet(Collections.singleton(KEY));
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));   
      MultipleActionServlet mas = new MultipleActionServlet(gas, sas);
      replListeners[1].expectWithTx(PutDataMapCommand.class);
      replListeners[1].expect(DataGravitationCleanupCommand.class);//data is removed from owner
      replListeners[2].expect(DataGravitationCleanupCommand.class);//backup tree is removed

      req = new Request(mgr0, sessionId, mas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      replListeners[2].waitForReplicationToOccur();

      assert sessionId.equals(mas.getSessionId()) : "wrong session id; expected " + sessionId + " got " + mas.getSessionId();
      Integer integer = (Integer) gas.getReadAttributes().get(KEY);
      assert integer != null : "null attribute value";
      assert integer.intValue() == (attr - 2) : "wrong val " + integer + " expected " + (attr -2);
      
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr0.getCache(), mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      
      // Invalidate the session
      InvalidationServlet invs = new InvalidationServlet();
      replListeners[1].expectWithTx(RemoveNodeCommand.class); 

      req = new Request(mgr0, sessionId, invs);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();

      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr0.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr3.getCache());
   }
   
   public void testFailoverAndInvalidate() throws Exception
   {
      int attr = 0;
      
      SessionManager mgr0 = getSessionManagers().get(0);
      SessionManager mgr1 = getSessionManagers().get(1);
      SessionManager mgr2 = getSessionManagers().get(2);
      SessionManager mgr3 = getSessionManagers().get(3);
      
      String contextHostName = mgr0.getContextHostName();

      // Create the session
      SetAttributesServlet sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      replListeners[1].expectWithTx(PutDataMapCommand.class);
      
      Request req = new Request(mgr0, null, sas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      
      String sessionId = sas.getSessionId();
      assert sessionId != null : "session id is null";
      // validate cache contents
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr0.getCache(), mgr1.getCache());
      
      // Modify the session
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++))); 
      replListeners[1].expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr0, sessionId, sas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      
      // Fail over; request reads the session and then modifies the session
      GetAttributesServlet gas = new GetAttributesServlet(Collections.singleton(KEY));
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));   
      MultipleActionServlet mas = new MultipleActionServlet(gas, sas);
      replListeners[0].expectWithTx(PutDataMapCommand.class); 
      replListeners[0].expect(DataGravitationCleanupCommand.class);
      replListeners[1].expect(DataGravitationCleanupCommand.class);

      req = new Request(mgr3, sessionId, mas);
      req.execute();      
      replListeners[0].waitForReplicationToOccur();
      replListeners[1].waitForReplicationToOccur();

      assert sessionId.equals(mas.getSessionId()) : "wrong session id; expected " + sessionId + " got " + mas.getSessionId();
      Integer integer = (Integer) gas.getReadAttributes().get(KEY);
      assert integer != null : "null attribute value";
      assert integer.intValue() == (attr - 2) : "wrong val " + integer + " expected " + (attr -2);
      
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr3.getCache(), mgr0.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr1.getCache());
      
      // Modify the session again
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      replListeners[0].expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr3, sessionId, sas);
      req.execute();      
      replListeners[0].waitForReplicationToOccur();
      
      // Invalidate the session
      InvalidationServlet invs = new InvalidationServlet();
      replListeners[0].expectWithTx(RemoveNodeCommand.class);
      
      req = new Request(mgr3, sessionId, invs);
      req.execute();      
      replListeners[0].waitForReplicationToOccur();

      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr0.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr3.getCache());
   }
   
   public void testFailoverToOwnerAndInvalidate() throws Exception
   {
      int attr = 0;
      
      SessionManager mgr0 = getSessionManagers().get(0);
      SessionManager mgr1 = getSessionManagers().get(1);
      SessionManager mgr2 = getSessionManagers().get(2);
      SessionManager mgr3 = getSessionManagers().get(3);
      
      String contextHostName = mgr0.getContextHostName();

      // Create the session
      SetAttributesServlet sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      replListeners[2].expectWithTx(PutDataMapCommand.class);
      
      Request req = new Request(mgr1, null, sas);
      req.execute();      
      replListeners[2].waitForReplicationToOccur();
      
      String sessionId = sas.getSessionId();
      assert sessionId != null : "session id is null";
      // validate cache contents
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr1.getCache(), mgr2.getCache());
      
      // Modify the session
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++))); 
      replListeners[2].expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr1, sessionId, sas);
      req.execute();      
      replListeners[2].waitForReplicationToOccur();
      
      // Fail over; request reads the session and then modifies the session
      GetAttributesServlet gas = new GetAttributesServlet(Collections.singleton(KEY));
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));   
      MultipleActionServlet mas = new MultipleActionServlet(gas, sas);
      replListeners[1].expectWithTx(PutDataMapCommand.class); 
      replListeners[2].expect(DataGravitationCleanupCommand.class);

      req = new Request(mgr0, sessionId, mas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      replListeners[2].waitForReplicationToOccur();

      assert sessionId.equals(mas.getSessionId()) : "wrong session id; expected " + sessionId + " got " + mas.getSessionId();
      Integer integer = (Integer) gas.getReadAttributes().get(KEY);
      assert integer != null : "null attribute value";
      assert integer.intValue() == (attr - 2) : "wrong val " + integer + " expected " + (attr -2);
      
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr0.getCache(), mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      
      // Modify the session again
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      replListeners[1].expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr0, sessionId, sas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      
      // Invalidate the session
      InvalidationServlet invs = new InvalidationServlet();
      replListeners[1].expectWithTx(RemoveNodeCommand.class);
      
      req = new Request(mgr0, sessionId, invs);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();

      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr0.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr3.getCache());
   }

   public void testFailoverAndFailBack() throws Exception
   {
      int attr = 0;
      
      SessionManager mgr0 = getSessionManagers().get(0);
      SessionManager mgr1 = getSessionManagers().get(1);
      SessionManager mgr2 = getSessionManagers().get(2);
      SessionManager mgr3 = getSessionManagers().get(3);
      
      String contextHostName = mgr0.getContextHostName();

      // Create the session
      SetAttributesServlet sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      replListeners[1].expectWithTx(PutDataMapCommand.class);
      
      Request req = new Request(mgr0, null, sas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      System.out.println("First put on 0, attr=" + attr);
      TestingUtil.dumpCacheContents(mgr0.getCache(), mgr1.getCache(), mgr2.getCache(), mgr3.getCache());

      String sessionId = sas.getSessionId();
      assert sessionId != null : "session id is null";
      // validate cache contents
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr0.getCache(), mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr3.getCache());

      // Modify the session
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++))); 
      replListeners[1].expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr0, sessionId, sas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      System.out.println("Second put on 0, attr=" + attr);
      TestingUtil.dumpCacheContents(mgr0.getCache(), mgr1.getCache(), mgr2.getCache(), mgr3.getCache());

      // Fail over; request reads the session and then modifies the session
      GetAttributesServlet gas = new GetAttributesServlet(Collections.singleton(KEY));
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));   
      MultipleActionServlet mas = new MultipleActionServlet(gas, sas);
      replListeners[0].expectWithTx(PutDataMapCommand.class);
      replListeners[1].expect(DataGravitationCleanupCommand.class);

      req = new Request(mgr3, sessionId, mas);
      req.execute();
      replListeners[0].waitForReplicationToOccur();
      replListeners[1].waitForReplicationToOccur();
      System.out.println("First put on 3, attr=" + attr);
      TestingUtil.dumpCacheContents(mgr0.getCache(), mgr1.getCache(), mgr2.getCache(), mgr3.getCache());

      assert sessionId.equals(mas.getSessionId()) : "wrong session id; expected " + sessionId + " got " + mas.getSessionId();
      Integer integer = (Integer) gas.getReadAttributes().get(KEY);
      assert integer != null : "null attribute value";
      assert integer.intValue() == (attr - 2) : "wrong val " + integer + " expected " + (attr -2);
      
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr3.getCache(), mgr0.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      
      // Modify the session again
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      replListeners[0].expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr3, sessionId, sas);
      req.execute();      
      replListeners[0].waitForReplicationToOccur();
      
      // Fail back; request reads the session and then modifies the session
      gas = new GetAttributesServlet(Collections.singleton(KEY));
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));   
      mas = new MultipleActionServlet(gas, sas);
      replListeners[1].expectWithTx(PutDataMapCommand.class);
      replListeners[3].expect(DataGravitationCleanupCommand.class);

      req = new Request(mgr0, sessionId, mas);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();
      replListeners[3].waitForReplicationToOccur();

      assert sessionId.equals(mas.getSessionId()) : "wrong session id; expected " + sessionId + " got " + mas.getSessionId();
      integer = (Integer) gas.getReadAttributes().get(KEY);
      assert integer != null : "null attribute value";
      assert integer.intValue() == attr - 2 : "wrong val " + integer + " expected " + (attr -2);
      
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr0.getCache(), mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr3.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      
      // Invalidate the session
      InvalidationServlet invs = new InvalidationServlet();
      replListeners[1].expectWithTx(RemoveNodeCommand.class);
      
      req = new Request(mgr0, sessionId, invs);
      req.execute();      
      replListeners[1].waitForReplicationToOccur();

      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr0.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr3.getCache());
      
      gas = new GetAttributesServlet(Collections.singleton(KEY));
      req = new Request(mgr0, sessionId, gas);
      assert gas.getReadAttributes().get(KEY) == null : "session not cleaned up";
   }
   
   public void testInvalidateOnFailoverToBackup() throws Exception
   {
      int attr = 0;
      
      SessionManager mgr0 = getSessionManagers().get(0);
      SessionManager mgr1 = getSessionManagers().get(1);
      SessionManager mgr2 = getSessionManagers().get(2);
      SessionManager mgr3 = getSessionManagers().get(3);
      
      String contextHostName = mgr0.getContextHostName();

      // Create the session
      SetAttributesServlet sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      ReplicationListener replListener0 = replListeners[0];
      replListener0.expectWithTx(PutDataMapCommand.class);
      
      Request req = new Request(mgr3, null, sas);
      req.execute();      
      replListener0.waitForReplicationToOccur();      
      
      String sessionId = sas.getSessionId();
      assert sessionId != null : "session id is null";
      // validate cache contents
      BuddyReplicationAssertions.assertBuddyBackup(contextHostName, sessionId, mgr3.getCache(), mgr0.getCache());
      
      // Modify the session
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++))); 
      replListener0.expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr3, sessionId, sas);
      req.execute();      
      replListener0.waitForReplicationToOccur();      

      // Passivate the session
      mgr0.passivate(sessionId);
      mgr1.passivate(sessionId);
      mgr2.passivate(sessionId);
      mgr3.passivate(sessionId);   
      
      // Reactivate the session
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++))); 
      replListener0.expectWithTx(PutDataMapCommand.class);
      
      req = new Request(mgr3, sessionId, sas);
      req.execute();
      replListener0.waitForReplicationToOccur();      

      // Invalidate the session
      sas = new SetAttributesServlet(Collections.singletonMap(KEY, getAttributeValue(attr++)));
      InvalidationServlet invs = new InvalidationServlet();
      MultipleActionServlet mas = new MultipleActionServlet(sas, invs);
      ReplicationListener replListener1 = replListeners[1];
      replListener1.expectWithTx(PutDataMapCommand.class, RemoveNodeCommand.class); 
      replListener1.expect(DataGravitationCleanupCommand.class); 

      req = new Request(mgr0, sessionId, mas);
      req.execute();      
      replListener1.waitForReplicationToOccur();

      TestingUtil.dumpCacheContents(mgr0.getCache(), mgr1.getCache(), mgr2.getCache(), mgr3.getCache());

      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr0.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr1.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr2.getCache());
      BuddyReplicationAssertions.assertUnrelated(contextHostName, sessionId, mgr3.getCache());
      
      GetAttributesServlet gas = new GetAttributesServlet(Collections.singleton(KEY));
      req = new Request(mgr0, sessionId, gas);
      assert gas.getReadAttributes().get(KEY) == null : "session not cleaned up";
   }
}
