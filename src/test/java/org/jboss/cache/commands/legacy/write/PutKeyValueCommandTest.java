package org.jboss.cache.commands.legacy.write;

import static org.easymock.EasyMock.expect;
import org.jboss.cache.NodeNotExistsException;
import org.jboss.cache.commands.write.AbstractVersionedDataCommand;
import org.jboss.cache.commands.write.AbstractVersionedDataCommandTest;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * tester class for {@link org.jboss.cache.commands.write.PutKeyValueCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.legacy.write.PutKeyValueCommandTest")
public class PutKeyValueCommandTest extends AbstractVersionedDataCommandTest
{
   PessPutForExternalReadCommand command;

   public AbstractVersionedDataCommand moreSetUp()
   {
      command = new PessPutForExternalReadCommand(globalTransaction, fqn, "k", "v");
      return command;
   }

   public void testInexistentNode()
   {
      expect(container.peek(fqn)).andReturn(null); // simulate node not existing.
      control.replay();
      try
      {
         command.perform(ctx);
         assert false : "exception should have been thrown as data does not exists.";
      }
      catch (NodeNotExistsException e)
      {
         //expected
      }
      control.verify();
   }

   public void testAddNewData()
   {
      nodes.adfNode.put("existingKey", "existingValue");
      expect(container.peek(fqn)).andReturn(nodes.adfNode);
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(fqn, true, NodeModifiedEvent.ModificationType.PUT_DATA, nodes.adfNode.getDataDirect(), ctx);
      Map expected = new HashMap();
      expected.put("k", "v");
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(fqn, false, NodeModifiedEvent.ModificationType.PUT_DATA, expected, ctx);
      control.replay();
      assert null == command.perform(ctx) : "no pre existing value";
      assert nodes.adfNode.getData().size() == 2;
      assert "v".equals(nodes.adfNode.getData().get("k"));
      assert "existingValue".equals(nodes.adfNode.getData().get("existingKey"));
      control.verify();

      control.reset();
      expect(container.peek(fqn, false, false)).andReturn(nodes.adfNode);
      control.replay();
      command.rollback();
      assert nodes.adfNode.getData().size() == 1;
      assert "existingValue".equals(nodes.adfNode.getData().get("existingKey"));
      control.verify();
   }

   public void testOverWriteData()
   {
      nodes.adfNode.put("k", "oldValue");
      expect(container.peek(fqn)).andReturn(nodes.adfNode);
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(fqn, true, NodeModifiedEvent.ModificationType.PUT_DATA, nodes.adfNode.getDataDirect(), ctx);
      Map expected = new HashMap();
      expected.put("k", "v");
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(fqn, false, NodeModifiedEvent.ModificationType.PUT_DATA, expected, ctx);
      control.replay();
      assert "oldValue".equals(command.perform(ctx)) : "no pre existing value";
      assert nodes.adfNode.getData().size() == 1;
      assert "v".equals(nodes.adfNode.getData().get("k"));
      control.verify();

      control.reset();
      expect(container.peek(fqn, false, false)).andReturn(nodes.adfNode);
      control.replay();
      command.rollback();
      assert nodes.adfNode.getData().size() == 1;
      assert "oldValue".equals(nodes.adfNode.getData().get("k"));
      control.verify();
   }
}
