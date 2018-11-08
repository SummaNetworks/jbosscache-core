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
package org.jboss.cache.passivation;

import org.jboss.cache.*;
import org.jboss.cache.commands.remote.DataGravitationCleanupCommand;
import org.jboss.cache.buddyreplication.BuddyFqnTransformer;
import org.jboss.cache.buddyreplication.BuddyReplicationTestsBase;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.loader.testloaders.DummySharedInMemoryCacheLoader;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.Set;

@Test(groups = "functional", testName = "passivation.ReplAndStateTransferWithPassivationTest")
public class ReplAndStateTransferWithPassivationTest
{
   public void testStateTransferOfPassivatedState() throws Exception
   {
      doTest(NodeLockingScheme.MVCC, false);
   }

   public void testStateTransferOfPassivatedStatePessimistic() throws Exception
   {
      doTest(NodeLockingScheme.PESSIMISTIC, false);
   }

   public void testStateTransferOfPassivatedPartialState() throws Exception
   {
      doPartialStateTransferTest(NodeLockingScheme.MVCC);
   }

   public void testStateTransferOfPassivatedPartialStatePessimistic() throws Exception
   {
      doPartialStateTransferTest(NodeLockingScheme.PESSIMISTIC);
   }

   public void testStateTransferOfPassivatedPartialStateBR() throws Exception
   {
      doTest(NodeLockingScheme.MVCC, true);
   }

   public void testStateTransferOfPassivatedPartialStateBRPessimistic() throws Exception
   {
      doTest(NodeLockingScheme.PESSIMISTIC, true);
   }

   public void testStateTransferOfPassivatedPartialStateBRForceRemote() throws Exception
   {
      doTest(NodeLockingScheme.MVCC, false);
   }

   public void testStateTransferOfPassivatedPartialStateBRPessimisticForceRemote() throws Exception
   {
      doTest(NodeLockingScheme.PESSIMISTIC, false);
   }


   private void doPartialStateTransferTest(NodeLockingScheme nls) throws Exception
   {
      CacheSPI cache1=null, cache2=null;
      String subtree = "/SESSIONS";
      try
      {
         Set<Object> nameSet = new HashSet<Object>();
         nameSet.add("a");
         nameSet.add("b");
         nameSet.add("c");

         cache1 = (CacheSPI) new UnitTestCacheFactory().createCache(buildConf(nls, "cache1", true, false, true), getClass());
         cache2 = (CacheSPI) new UnitTestCacheFactory().createCache(buildConf(nls, "cache2", true, false, true), getClass());

         Region r1 = cache1.getRegionManager().getRegion(subtree, true);
         Region r2 = cache2.getRegionManager().getRegion(subtree, true);
         r1.registerContextClassLoader(getClass().getClassLoader());
         r2.registerContextClassLoader(getClass().getClassLoader());
         
         r1.activate();

         cache1.put(subtree + "/a", "k", "v");
         cache1.put(subtree + "/b", "k", "v");
         cache1.put(subtree + "/c", "k", "v");

         Node root1 = cache1.getNode(subtree);
         assert root1.getChildrenNames().equals(nameSet) : "Expecting " + nameSet + " but got " + root1.getChildrenNames();

         cache1.evict(Fqn.fromString("/a"));

         r2.activate();
         Node root2 = cache1.getNode(subtree);
         assert root2.getChildrenNames().equals(nameSet) : "Expecting " + nameSet + " but got " + root2.getChildrenNames();
      }
      finally
      {
         TestingUtil.killCaches(cache1, cache2);
      }
   }

   private void doTest(NodeLockingScheme nls, boolean useBR) throws Exception
   {
      Cache cache1=null, cache2=null;
      Fqn fqn = useBR ? Fqn.fromString("/someFqn") : Fqn.ROOT;
      Fqn A = Fqn.fromRelativeElements(fqn, "a"), B = Fqn.fromRelativeElements(fqn, "b"), C = Fqn.fromRelativeElements(fqn, "c");
      try
      {
         Set<Object> nameSet = new HashSet<Object>();
         nameSet.add(A.getLastElement());
         nameSet.add(B.getLastElement());
         nameSet.add(C.getLastElement());
         
         cache1 = new UnitTestCacheFactory().createCache(buildConf(nls, "cache1", false, useBR, true), getClass());

         cache1.put(A, "k", "v");
         cache1.put(B, "k", "v");
         cache1.put(C, "k", "v");
         assert cache1.getNode(fqn).getChildrenNames().equals(nameSet);

         cache1.evict(A);

         cache2 = new UnitTestCacheFactory().createCache(buildConf(nls, "cache2", false, useBR, true), getClass());
         TestingUtil.blockUntilViewReceived((CacheSPI)cache2, 2, 10000);
         Thread.sleep(2000);

         if (useBR)
         {
            BuddyReplicationTestsBase.waitForSingleBuddy(cache1, cache2);
            Set backupNameSet = new HashSet(nameSet);
            backupNameSet.remove(BuddyFqnTransformer.BUDDY_BACKUP_SUBTREE);

            ReplicationListener replListener1 = ReplicationListener.getReplicationListener(cache1);
            replListener1.expect(DataGravitationCleanupCommand.class);
            cache2.getInvocationContext().getOptionOverrides().setForceDataGravitation(true);
            Node backupNode = cache2.getNode(fqn);
            replListener1.waitForReplicationToOccur();

           assert backupNode.getChildrenNames().equals(backupNameSet) : "Expecting " + backupNameSet + " but got " + backupNode.getChildrenNames();
         }
         else
         {
            assert cache2.getRoot().getChildrenNames().equals(nameSet) : "Expecting " + nameSet + " but got " + cache2.getRoot().getChildrenNames();
         }
      }
      finally
      {
         TestingUtil.killCaches(cache1, cache2);
      }
   }

   private Configuration buildConf(NodeLockingScheme nls, String name, boolean regionbased, boolean useBR, boolean brSearchSubtrees) throws Exception
   {
      Configuration c = new Configuration();
      if (regionbased)
      {
         c.setUseRegionBasedMarshalling(true);
         c.setInactiveOnStartup(true);
      }
      c.setCacheMode(CacheMode.REPL_SYNC);
      c.setNodeLockingScheme(nls);
      CacheLoaderConfig clc = UnitTestConfigurationFactory.buildSingleCacheLoaderConfig(false, "", DummySharedInMemoryCacheLoader.class.getName(),
            "bin=" + name, false, true, false, false, false);
      clc.setPassivation(true);
      c.setCacheLoaderConfig(clc);
      if (useBR)
      {
         BuddyReplicationConfig brc = new BuddyReplicationConfig();
         brc.setEnabled(true);
         brc.setAutoDataGravitation(false);
         brc.setDataGravitationSearchBackupTrees(brSearchSubtrees);
         brc.setDataGravitationRemoveOnFind(true);
         c.setBuddyReplicationConfig(brc);
      }
      return c;
   }

}
