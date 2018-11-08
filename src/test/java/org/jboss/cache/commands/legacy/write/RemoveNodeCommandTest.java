package org.jboss.cache.commands.legacy.write;

import static org.easymock.EasyMock.expect;
import org.jboss.cache.commands.write.AbstractVersionedDataCommand;
import org.jboss.cache.commands.write.AbstractVersionedDataCommandTest;
import org.jboss.cache.transaction.GlobalTransaction;
import org.testng.annotations.Test;

/**
 * tester for  {@link org.jboss.cache.commands.write.RemoveNodeCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.legacy.write.RemoveNodeCommandTest")
public class RemoveNodeCommandTest extends AbstractVersionedDataCommandTest
{
   PessRemoveNodeCommand command;

   public AbstractVersionedDataCommand moreSetUp()
   {
      command = new PessRemoveNodeCommand(globalTransaction, fqn);
      command.setDataVersion(dataVersion);
      return command;
   }

   public void testNonExistentNode()
   {
      expect(container.peek(fqn)).andReturn(null);
      // again
      expect(container.peek(fqn)).andReturn(null);
      control.replay();
      assert Boolean.FALSE == command.perform(ctx) : "nonexistent node was not remove; false expected";
   }

   public void testRemovalNoNotificationsValidNode()
   {
      //aditional setup
      command.setSkipSendingNodeEvents(true); //no notification
      nodes.adfNode.put("akey", "avalue");
      nodes.adfNode.setVersion(dataVersion);
      ctx.setGlobalTransaction(new GlobalTransaction());

      //check perform
      expect(container.peek(fqn)).andReturn(nodes.adfNode);
      // again
      expect(container.peek(fqn)).andReturn(nodes.adfNode);
      control.replay();
      assert Boolean.TRUE == command.perform(ctx);
      assert nodes.adfgNode.isDeleted();
      assert nodes.adfhNode.isDeleted();
      assert command.originalData != null;
      control.verify();

      //check rollback
      control.reset();
      nodes.adNode.removeChild("f");
      expect(container.peek(nodes.ad)).andReturn(nodes.adNode);
      control.replay();
      command.rollback();
      assert nodes.adNode.hasChild("f");
   }

   public void testRemovalNoNotificationsInvalidNode()
   {
      command.setSkipSendingNodeEvents(true); //no notification
      nodes.adfNode.setValid(false, false);   //invalid node
      nodes.adfNode.setVersion(dataVersion);

      expect(container.peek(fqn)).andReturn(nodes.adfNode);
      // again
      expect(container.peek(fqn)).andReturn(nodes.adfNode);
      control.replay();
      assert Boolean.FALSE == command.perform(ctx);
      assert nodes.adfgNode.isDeleted();
      assert nodes.adfhNode.isDeleted();
      control.verify();
   }

   public void testRemovalWithNotificationsInvalidNode()
   {
      nodes.adfNode.setValid(false, false);   //invalid node
      nodes.adfNode.setVersion(dataVersion);

      expect(container.peek(fqn)).andReturn(nodes.adfNode);
      // again
      expect(container.peek(fqn)).andReturn(nodes.adfNode);
      notifier.notifyNodeRemoved(fqn, true, nodes.adfNode.getDataDirect(), ctx);
      notifier.notifyNodeRemoved(fqn, false, null, ctx);
      control.replay();
      assert Boolean.FALSE == command.perform(ctx);
      assert nodes.adfgNode.isDeleted();
      assert nodes.adfhNode.isDeleted();
      control.verify();
   }
}
