package org.jboss.cache.commands.legacy.write;

import static org.easymock.EasyMock.*;
import org.jboss.cache.commands.read.AbstractDataCommandTest;
import org.jboss.cache.mock.MockNodesFixture;
import org.testng.annotations.Test;

import java.util.ArrayList;

/**
 * Tester class for {@link CreateNodeCommand}
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.legacy.write.CreateNodeCommandTest")
public class CreateNodeCommandTest extends AbstractDataCommandTest
{
   CreateNodeCommand command;
   private Object[] result;
   private ArrayList createdNodes;

   protected void moreSetup()
   {
      command = new CreateNodeCommand(testFqn);
      command.initialize(container);
      createdNodes = new ArrayList();
      result = new Object[2];
      result[0] = this.createdNodes;
   }

   public void testPerformNoNodesCreated()
   {
      expect(container.createNodes(testFqn)).andReturn(result);
      replay(container);
      assert null == command.perform(ctx);
      assert command.getNewlyCreated().isEmpty();
   }

   public void testPerformWithCreatedNodes()
   {
      MockNodesFixture nodes = new MockNodesFixture();
      createdNodes.add(nodes.aNode);
      createdNodes.add(nodes.abNode);
      createdNodes.add(nodes.abcNode);
      result[1] = nodes.abcNode;

      expect(container.createNodes(testFqn)).andReturn(result);
      replay(container);
      assert nodes.abcNode == command.perform(ctx);
      assert command.getNewlyCreated().size() == 3;
      assert command.getNewlyCreated().contains(nodes.a);
      assert command.getNewlyCreated().contains(nodes.ab);
      assert command.getNewlyCreated().contains(nodes.abc);
   }

   public void testRollback()
   {
      MockNodesFixture nodes = new MockNodesFixture();
      createdNodes.add(nodes.aNode);
      createdNodes.add(nodes.abNode);
      createdNodes.add(nodes.abcNode);
      expect(container.createNodes(testFqn)).andReturn(result);
      expect(container.removeFromDataStructure(nodes.a, true)).andReturn(Boolean.TRUE);
      expect(container.removeFromDataStructure(nodes.ab, true)).andReturn(Boolean.TRUE);
      expect(container.removeFromDataStructure(nodes.abc, true)).andReturn(Boolean.TRUE);
      replay(container);
      command.perform(ctx);
      command.rollback();
      verify(container);
   }

}
