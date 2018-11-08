/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.buddyreplication;

import org.jboss.cache.Fqn;
import org.jboss.cache.commands.CommandsFactoryImpl;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.remote.ReplicateCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.BuddyReplicationConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.element.BuddyElementParser;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests the BuddyManager class
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = "functional", testName = "buddyreplication.BuddyManagerTest")
public class BuddyManagerTest
{
   private static final String DUMMY_LOCAL_ADDRESS = "myLocalAddress:12345";

   /**
    * Constructs a buddy manager using the default buddy locator but with some specific properties.
    *
    * @throws Exception
    */
   public void testConstruction1() throws Exception
   {
      String xmlConfig =
            "   <buddy enabled=\"true\" poolName=\"groupOne\">\n" +
                  "      <locator>\n" +
                  "         <properties>\n" +
                  "            numBuddies = 3\n" +
                  "         </properties>\n" +
                  "      </locator>\n" +
                  "   </buddy>";
      BuddyReplicationConfig config = getBuddyReplicationConfig(xmlConfig);
      BuddyManager mgr = new BuddyManager(config);

      assertTrue(mgr.isEnabled());
      assertEquals("groupOne", mgr.getBuddyPoolName());
      assertEquals(NextMemberBuddyLocator.class, mgr.buddyLocator.getClass());
      NextMemberBuddyLocatorConfig blc = (NextMemberBuddyLocatorConfig) mgr.buddyLocator.getConfig();
      assertEquals(3, blc.getNumBuddies());
      assertTrue(blc.isIgnoreColocatedBuddies());
   }

   private BuddyReplicationConfig getBuddyReplicationConfig(String xmlConfig)
         throws Exception
   {
      Element element = XmlConfigHelper.stringToElementInCoreNS(xmlConfig);
      BuddyElementParser replicationElementParser = new BuddyElementParser();
      return replicationElementParser.parseBuddyElement(element);
   }

   /**
    * Constructs a buddy manager using a nonexistent buddy locator but with some specific properties.
    *
    * @throws Exception
    */
   public void testConstruction2() throws Exception
   {
      String xmlConfig =
            "   <buddy enabled=\"true\" poolName=\"groupOne\">\n" +
                  "      <locator class=\"org.i.dont.exist.PhantomBuddyLocator\">\n" +
                  "         <properties>\n" +
                  "            numBuddies = 3\n" +
                  "         </properties>\n" +
                  "      </locator>\n" +
                  "   </buddy>";
      BuddyReplicationConfig config = getBuddyReplicationConfig(xmlConfig);
      BuddyManager mgr = new BuddyManager(config);

      assertTrue(mgr.isEnabled());
      assertEquals("groupOne", mgr.getBuddyPoolName());
      assertEquals(NextMemberBuddyLocator.class, mgr.buddyLocator.getClass());

      // since the properties are not passed on to the next member buddy locator - they were obviously meant for a different impl.
      NextMemberBuddyLocatorConfig blc = (NextMemberBuddyLocatorConfig) mgr.buddyLocator.getConfig();
      assertEquals(1, blc.getNumBuddies());
      assertTrue(blc.isIgnoreColocatedBuddies());
   }

   /**
    * Constructs a disabled buddy manager
    *
    * @throws Exception
    */
   public void testConstruction3() throws Exception
   {
      String xmlConfig = "<buddy enabled=\"false\"/>";
      BuddyReplicationConfig config = getBuddyReplicationConfig(xmlConfig);
      BuddyManager mgr = new BuddyManager(config);
      assertTrue(!mgr.isEnabled());
   }

   /**
    * Constructs a buddy manager using a minimal config set
    *
    * @throws Exception
    */
   public void testConstruction4() throws Exception
   {
      String xmlConfig = "<buddy enabled=\"true\"/>";

      BuddyReplicationConfig config = getBuddyReplicationConfig(xmlConfig);
      BuddyManager mgr = new BuddyManager(config);

      assertTrue(mgr.isEnabled());
      assertNull(mgr.getBuddyPoolName());
      assertEquals(NextMemberBuddyLocator.class, mgr.buddyLocator.getClass());
      NextMemberBuddyLocatorConfig blc = (NextMemberBuddyLocatorConfig) mgr.buddyLocator.getConfig();
      assertEquals(1, blc.getNumBuddies());
      assertTrue(blc.isIgnoreColocatedBuddies());
   }

   private BuddyManager createBasicBuddyManager()
   {
      BuddyManager bm = null;
      try
      {
         String xmlConfig = "<buddy enabled=\"false\"/>";
         BuddyReplicationConfig config = getBuddyReplicationConfig(xmlConfig);
         bm = new BuddyManager(config);
         bm.injectDependencies(null, null, null, null, null, null, null, null, new BuddyFqnTransformer());
         CommandsFactoryImpl commandsFactory = new CommandsFactoryImpl();
         commandsFactory.initialize(null, null, null, null, null, null, null, new Configuration(), null, new BuddyFqnTransformer());
         bm.initFqnTransformer(DUMMY_LOCAL_ADDRESS, commandsFactory);
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
      return bm;
   }

   public void testFqnManipulation()
   {
      Fqn fqn1 = Fqn.fromString("/hello/world");

      PutKeyValueCommand call1 = new PutKeyValueCommand(null, fqn1, "key", "value");
      ReplicateCommand call2 = new ReplicateCommand(call1);

      BuddyManager bm = createBasicBuddyManager();

      ReplicateCommand newReplicatedCall = bm.transformReplicateCommand(call2);
      PutKeyValueCommand newPutCall = (PutKeyValueCommand) newReplicatedCall.getSingleModification();

      // should use object refs to transform the original MethodCall.
      Fqn expected = Fqn.fromString("/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + DUMMY_LOCAL_ADDRESS + "/hello/world");
      assertEquals(expected, newPutCall.getFqn());

   }

   public void testRootFqnManipulation()
   {
      Fqn fqn1 = Fqn.ROOT;

      ReplicableCommand call1 = new PutKeyValueCommand(null, fqn1, "key", "value");
      ReplicateCommand call2 = new ReplicateCommand(call1);

      BuddyManager bm = createBasicBuddyManager();

      ReplicateCommand newReplicatedCall = bm.transformReplicateCommand(call2);
      PutKeyValueCommand newPutCall = (PutKeyValueCommand) newReplicatedCall.getSingleModification();

      // should use object refs to transform the original MethodCall.
      Fqn expected = Fqn.fromString("/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + DUMMY_LOCAL_ADDRESS);
      assertEquals(expected, newPutCall.getFqn());
   }

   public void testMultiFqnManipulation()
   {
      Fqn fqn1 = Fqn.ROOT;
      Fqn fqn2 = Fqn.fromString("/hello/world");
      Fqn fqn3 = Fqn.fromString("/hello/again");
      Fqn fqn4 = Fqn.fromString("/buddy/replication");

      PutKeyValueCommand call1 = new PutKeyValueCommand(null, fqn1, "key", "value");
      PutKeyValueCommand call2 = new PutKeyValueCommand(null, fqn2, "key", "value");
      PutKeyValueCommand call3 = new PutKeyValueCommand(null, fqn3, "key", "value");
      PutKeyValueCommand call4 = new PutKeyValueCommand(null, fqn4, "key", "value");
      List<ReplicableCommand> list = new ArrayList<ReplicableCommand>();
      list.add(call1);
      list.add(call2);
      list.add(call3);
      list.add(call4);

      ReplicateCommand call5 = new ReplicateCommand(list);

      BuddyManager bm = createBasicBuddyManager();

      ReplicateCommand newReplicatedCall = bm.transformReplicateCommand(call5);
      List<ReplicableCommand> l = newReplicatedCall.getModifications();

      // should use object refs to transform the original MethodCall.
      String expected = "/" + BuddyManager.BUDDY_BACKUP_SUBTREE + "/" + DUMMY_LOCAL_ADDRESS;

      int i = 0;
      assertEquals(Fqn.fromString(expected), ((PutKeyValueCommand) l.get(i++)).getFqn());
      assertEquals(Fqn.fromString(expected + "/hello/world"), ((PutKeyValueCommand) l.get(i++)).getFqn());
      assertEquals(Fqn.fromString(expected + "/hello/again"), ((PutKeyValueCommand) l.get(i++)).getFqn());
      assertEquals(Fqn.fromString(expected + "/buddy/replication"), ((PutKeyValueCommand) l.get(i)).getFqn());
   }

   public void testGetActualFqn()
   {
      BuddyFqnTransformer fqnTransformer = new BuddyFqnTransformer();
      Fqn x = Fqn.fromString("/x");
      Fqn backup = fqnTransformer.getBackupFqn("y", x);
      assertEquals(x, fqnTransformer.getActualFqn(backup));
   }
}
