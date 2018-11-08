package org.jboss.cache.commands.legacy.read;

import static org.easymock.EasyMock.createStrictControl;
import static org.easymock.EasyMock.expect;
import org.easymock.IMocksControl;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.DataContainer;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.buddyreplication.BuddyFqnTransformer;
import org.jboss.cache.buddyreplication.BuddyManager;
import org.jboss.cache.buddyreplication.GravitateResult;
import org.jboss.cache.invocation.LegacyInvocationContext;
import org.jboss.cache.mock.MockNodesFixture;
import org.jboss.cache.mock.NodeSpiMock;
import org.jgroups.stack.IpAddress;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Tester class for {@link org.jboss.cache.commands.read.GravitateDataCommand}
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.legacy.read.GravitateDataCommandTest")
public class GravitateDataCommandTest
{
   LegacyGravitateDataCommand command;
   DataContainer containerMock;
   CacheSPI spiMock;
   IMocksControl control;
   Fqn fqn = Fqn.fromString("/dummy");
   InvocationContext ctx;
   BuddyFqnTransformer fqnTransformer = new BuddyFqnTransformer();

   @BeforeMethod
   public void setUp()
   {
      control = createStrictControl();
      containerMock = control.createMock(DataContainer.class);
      spiMock = control.createMock(CacheSPI.class);
      command = new LegacyGravitateDataCommand(fqn, true, new IpAddress());
      command.initialize(containerMock, spiMock, new BuddyFqnTransformer());
      ctx = new LegacyInvocationContext(containerMock);
   }

   public void testNonexistentNode()
   {
      command.setSearchSubtrees(false);
      expect(spiMock.getNode(fqn)).andReturn(null);
      control.replay();
      assert GravitateResult.noDataFound().equals(command.perform(ctx));
   }

   public void testExistentNodeInTheCache()
   {
      MockNodesFixture nodes = new MockNodesFixture();
      command.setSearchSubtrees(false);

      expect(spiMock.getNode(fqn)).andReturn(nodes.adfNode);
      ArrayList arrayList = new ArrayList();
      expect(containerMock.buildNodeData(Collections.EMPTY_LIST, nodes.adfNode, false)).andReturn(arrayList);
      control.replay();
      GravitateResult result = (GravitateResult) command.perform(ctx);
      control.verify();

      assert result != null;
      assert result.getNodeData() == arrayList;
   }

   public void testNodeDoesExistsInBackupAndNoDead()
   {
      MockNodesFixture nodes = new MockNodesFixture();
      expect(spiMock.getNode(fqn)).andReturn(null);
      expect(containerMock.peek(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN)).andReturn(nodes.abNode);

      Fqn firstSearch = Fqn.fromString(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN + "/c/dummy");
      expect(spiMock.getNode(firstSearch)).andReturn(nodes.abcNode);

      ArrayList listReference = new ArrayList();
      expect(containerMock.buildNodeData(Collections.EMPTY_LIST, nodes.abcNode, false)).andReturn(listReference);

      control.replay();
      GravitateResult result = (GravitateResult) command.perform(ctx);
      assert result.getNodeData() == listReference;
      control.verify();
   }

   public void testNodeDoesNotExistsInBackupAndNoDead()
   {
      MockNodesFixture nodes = new MockNodesFixture();
      expect(spiMock.getNode(fqn)).andReturn(null);
      expect(containerMock.peek(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN)).andReturn(nodes.adfNode);

      control.checkOrder(false);
      Fqn firstSearch = Fqn.fromString(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN + "/g/dummy");
      expect(spiMock.getNode(firstSearch)).andReturn(null);
      Fqn secondSearch = Fqn.fromString(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN + "/h/dummy");
      expect(spiMock.getNode(secondSearch)).andReturn(null);
      control.checkOrder(true);

      control.replay();
      assert GravitateResult.noDataFound().equals(command.perform(ctx));
      control.verify();
   }

   public void testDeadBeackup() throws Exception
   {
      NodeSpiMock root = new NodeSpiMock(Fqn.ROOT);

      //add first dead child
      IpAddress firstAddress = new IpAddress("127.0.0.1", 1234);
      NodeSpiMock firstDeadNode = (NodeSpiMock) root.addChildDirect(fqnTransformer.getDeadBackupRoot(firstAddress));
      firstDeadNode.addChildDirect(Fqn.fromElements(0));
      firstDeadNode.addChildDirect(Fqn.fromElements(1));
      firstDeadNode.addChildDirect(Fqn.fromElements(2));

      //add second dead child
      IpAddress secondAddress = new IpAddress("127.0.0.1", 4321);
      NodeSpiMock secondDeadNode = (NodeSpiMock) root.addChildDirect(fqnTransformer.getDeadBackupRoot(secondAddress));
      secondDeadNode.addChildDirect(Fqn.fromElements(0));


      expect(spiMock.getNode(fqn)).andReturn(null);
      NodeSPI buddyBackupRoot = (NodeSPI) root.getChild(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN);
      expect(containerMock.peek(BuddyManager.BUDDY_BACKUP_SUBTREE_FQN)).andReturn(buddyBackupRoot);

      control.checkOrder(false);
      expect(spiMock.getChildrenNames(firstDeadNode.getFqn())).andReturn(firstDeadNode.getChildrenNames());
      Object fqnElement = fqn.getLastElement();//this is only composed from one element
      expect(spiMock.peek(Fqn.fromRelativeElements(firstDeadNode.getFqn(), 0, fqnElement), false)).andReturn(null);
      expect(spiMock.peek(Fqn.fromRelativeElements(firstDeadNode.getFqn(), 1, fqnElement), false)).andReturn(null);
      expect(spiMock.peek(Fqn.fromRelativeElements(firstDeadNode.getFqn(), 2, fqnElement), false)).andReturn(null);

      expect(spiMock.getChildrenNames(secondDeadNode.getFqn())).andReturn(secondDeadNode.getChildrenNames());
      expect(spiMock.peek(Fqn.fromRelativeElements(secondDeadNode.getFqn(), 0, fqnElement), false)).andReturn(null);
      control.checkOrder(true);

      control.replay();
      assert GravitateResult.noDataFound().equals(command.perform(ctx));
      control.verify();
   }
}
