package org.jboss.cache.commands.read;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import org.jboss.cache.commands.legacy.read.PessGetChildrenNamesCommand;
import org.jboss.cache.mock.MockNodesFixture;
import org.jboss.cache.mock.NodeSpiMock;
import org.testng.annotations.Test;

import java.util.Set;

/**
 * Tester class for {@link  org.jboss.cache.commands.read.GetChildrenNamesCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.read.GetChildrenNamesCommandTest")
public class GetChildrenNamesCommandTest extends AbstractDataCommandTest
{
   private GetChildrenNamesCommand command;
   private MockNodesFixture nodes;

   protected void moreSetup()
   {
      nodes = new MockNodesFixture();
      command = new PessGetChildrenNamesCommand(testFqn);
      command.initialize(container);
   }

   public void testPerformNoChildren()
   {
      NodeSpiMock node = new NodeSpiMock(testFqn);
      expect(container.peek(testFqn)).andReturn(node);
      replay(container);
      Set result = (Set) command.perform(ctx);
      assert result.isEmpty() : "empty result expected";
   }

   public void testPerformInexistingNode()
   {
      expect(container.peek(testFqn)).andReturn(null);
      replay(container);
      Set result = (Set) command.perform(ctx);
      assert result == null : "empty result expected";
   }

   public void testNodeWithChildren()
   {
      expect(container.peek(testFqn)).andReturn(nodes.adfNode);
      replay(container);
      Set result = (Set) command.perform(ctx);
      assert result.size() == 2;
      assert result.contains("h");
      assert result.contains("g");
   }

   public void testNodeInvalidChildren()
   {
      nodes.adfgNode.markAsDeleted(true);
      expect(container.peek(testFqn)).andReturn(nodes.adfNode);
      replay(container);
      Set result = (Set) command.perform(ctx);
      assert result.size() == 1;
      assert result.contains("h");
   }
}
