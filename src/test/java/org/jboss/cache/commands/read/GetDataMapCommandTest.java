package org.jboss.cache.commands.read;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import org.jboss.cache.mock.NodeSpiMock;
import org.testng.annotations.Test;

import java.util.Map;

/**
 * Tester class for {@link GetDataMapCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.read.GetDataMapCommandTest")
public class GetDataMapCommandTest extends AbstractDataCommandTest
{
   GetDataMapCommand command;

   protected void moreSetup()
   {
      command = new GetDataMapCommand(testFqn);
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
      Map result = (Map) command.perform(ctx);
      assert 2 == result.entrySet().size();
      assert result.get("k1").equals("v1");
      assert result.get("k2").equals("v2");

      try
      {
         result.put("k3", "v3");
         assert false : "map should be immutable";
      }
      catch (RuntimeException ex)
      {
         //expected
      }
   }
}
