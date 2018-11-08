package org.jboss.cache.commands.read;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import org.jboss.cache.mock.NodeSpiMock;
import org.testng.annotations.Test;

import java.util.Set;

/**
 * Tester class for {@link GetKeysCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.read.GetKeysCommandTest")
public class GetKeysCommandTest extends AbstractDataCommandTest
{

   GetKeysCommand command;

   protected void moreSetup()
   {
      command = new GetKeysCommand(testFqn);
      command.initialize(container);
   }

   public void testForNonexistentNode()
   {
      expect(container.peek(testFqn)).andReturn(null);
      replay(container);
      assert null == command.perform(ctx);
   }

   public void testForExistingNode()
   {
      NodeSpiMock node = new NodeSpiMock(testFqn);
      node.putDirect("k1", "v1");
      node.putDirect("k2", "v2");
      expect(container.peek(testFqn)).andReturn(node);
      replay(container);
      Set result = (Set) command.perform(ctx);
      assert 2 == result.size();
      assert result.contains("k1");
      assert result.contains("k2");
   }
}
