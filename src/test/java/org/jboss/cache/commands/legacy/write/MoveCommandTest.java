package org.jboss.cache.commands.legacy.write;

import static org.easymock.EasyMock.createStrictControl;
import static org.easymock.EasyMock.expect;
import org.easymock.IMocksControl;
import org.jboss.cache.DataContainer;
import org.jboss.cache.Fqn;
import org.jboss.cache.NodeNotExistsException;
import org.jboss.cache.commands.read.AbstractDataCommandTest;
import org.jboss.cache.commands.write.MoveCommand;
import org.jboss.cache.mock.MockNodesFixture;
import org.jboss.cache.notifications.Notifier;
import org.testng.annotations.Test;

/**
 * Tester class for {@link org.jboss.cache.commands.write.MoveCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", testName = "commands.legacy.write.MoveCommandTest")
public class MoveCommandTest extends AbstractDataCommandTest
{
   MoveCommand command;
   Notifier notifier;
   IMocksControl control;
   MockNodesFixture nodes;

   Fqn source = Fqn.fromString("/source");
   Fqn destination = Fqn.fromString("/destination");

   protected void moreSetup()
   {
      control = createStrictControl();
      notifier = control.createMock(Notifier.class);
      container = control.createMock(DataContainer.class);
      command = new PessMoveCommand(source, destination);
      command.initialize(notifier, container);
      nodes = new MockNodesFixture();
   }

   public void testFailsOnMissingSource()
   {
      control.checkOrder(false);
      expect(container.peek(source)).andReturn(null);
      expect(container.peek(destination)).andReturn(nodes.adfgNode);
      control.replay();
      try
      {
         command.perform(ctx);
         assert false : "should have thrown an exception as the source is null";
      }
      catch (NodeNotExistsException e)
      {
         //expected
      }
   }

   public void testFailsOnMissingDestination()
   {
      control.checkOrder(false);
      expect(container.peek(source)).andReturn(nodes.adfgNode);
      expect(container.peek(destination)).andReturn(null);
      control.replay();
      try
      {
         command.perform(ctx);
         assert false : "should have thrown an exception as the source is null";
      }
      catch (NodeNotExistsException e)
      {
         //expected
      }
   }
}
