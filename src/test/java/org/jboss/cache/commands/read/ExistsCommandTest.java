package org.jboss.cache.commands.read;

import org.jboss.cache.InvocationContext;
import org.jboss.cache.invocation.MVCCInvocationContext;
import org.jboss.cache.mock.NodeSpiMock;
import org.testng.annotations.Test;

/**
 * Tester class for {@link org.jboss.cache.commands.read.ExistsCommand}
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", testName = "commands.read.ExistsCommandTest")
public class ExistsCommandTest extends AbstractDataCommandTest
{
   private ExistsCommand command;

   protected void moreSetup()
   {
      command = new ExistsCommand(testFqn);
      command.initialize(container);
   }

   public void testPerform()
   {
      InvocationContext ctx = new MVCCInvocationContext();
      ctx.putLookedUpNode(testFqn, null);
      assert !((Boolean) command.perform(ctx));

      ctx.putLookedUpNode(testFqn, new NodeSpiMock(testFqn));

      assert Boolean.TRUE == command.perform(ctx);
   }
}
