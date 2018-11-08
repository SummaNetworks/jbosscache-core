package org.jboss.cache.commands.read;

import static org.easymock.EasyMock.createStrictControl;
import static org.easymock.EasyMock.expect;
import org.easymock.IMocksControl;
import org.jboss.cache.DataContainer;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.commands.TestContextBase;
import org.jboss.cache.mock.NodeSpiMock;
import org.jboss.cache.notifications.Notifier;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tester class for {@link GetKeyValueCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.read.GetKeyValueCommandTest")
public class GetKeyValueCommandTest extends TestContextBase
{
   private IMocksControl control;
   Notifier notifierMock;
   DataContainer containerMock;
   GetKeyValueCommand command;
   Fqn fqn = Fqn.fromString("/dummy");
   String key = "key";
   InvocationContext ctx;

   @BeforeMethod
   protected void setUup()
   {
      control = createStrictControl();
      containerMock = control.createMock(DataContainer.class);
      notifierMock = control.createMock(Notifier.class);
      command = new GetKeyValueCommand(fqn, key, false);
      command.initialize(containerMock, notifierMock);
      ctx = createLegacyInvocationContext(containerMock);
   }

   public void testNonexistentNodeNoNotifications()
   {
      expect(containerMock.peek(fqn)).andReturn(null);
      control.replay();
      assert null == command.perform(ctx);
   }

   public void testExistentNodeNoNotifications()
   {
      NodeSpiMock node = new NodeSpiMock(fqn);
      String value = "vvv";
      node.put(key, value);
      expect(containerMock.peek(fqn)).andReturn(node);
      control.replay();
      assert value.equals(command.perform(ctx));
   }

   /**
    * Notification should only be triggered if the node exists.
    */
   public void testNonexistentNodeWithNotifications()
   {
      command.sendNodeEvent = true;
      expect(containerMock.peek(fqn)).andReturn(null);
      control.replay();
      assert null == command.perform(ctx);
   }

   public void testExistentNodeWithNotifications()
   {
      command.sendNodeEvent = true;
      NodeSpiMock node = new NodeSpiMock(fqn);
      String value = "vvv";
      node.put(key, value);
      //not ordred because the peek hapens before notification - that is to make sure that no notification
      // is sent for an nonexistent node.
      control.checkOrder(false);
      notifierMock.notifyNodeVisited(fqn, true, ctx);
      expect(containerMock.peek(fqn)).andReturn(node);
      notifierMock.notifyNodeVisited(fqn, false, ctx);
      control.replay();
      assert value.equals(command.perform(ctx));
      control.verify();
   }
}
