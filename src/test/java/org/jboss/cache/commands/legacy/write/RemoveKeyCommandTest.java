package org.jboss.cache.commands.legacy.write;

import static org.easymock.EasyMock.expect;
import org.jboss.cache.commands.write.AbstractVersionedDataCommand;
import org.jboss.cache.commands.write.AbstractVersionedDataCommandTest;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * tester class for {@link org.jboss.cache.commands.write.RemoveKeyCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.legacy.write.RemoveKeyCommandTest")
public class RemoveKeyCommandTest extends AbstractVersionedDataCommandTest
{
   PessRemoveKeyCommand command;
   private String key;

   public AbstractVersionedDataCommand moreSetUp()
   {
      key = "key";
      command = new PessRemoveKeyCommand(globalTransaction, fqn, key);
      return command;
   }

   public void testNonexistentNode()
   {
      expect(container.peek(fqn)).andReturn(null);
      control.replay();
      assert null == command.perform(ctx);
      control.verify();
   }

   public void testRemoveNonexistentPair()
   {
      Map expected = new HashMap();
      expected.put("newKey", "newValue");
      nodes.adfgNode.putAll(expected);
      expect(container.peek(fqn)).andReturn(nodes.adfgNode);
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(fqn, true, NodeModifiedEvent.ModificationType.REMOVE_DATA, expected, ctx);
      expected = new HashMap();
      expected.put(key, null);
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(fqn, false, NodeModifiedEvent.ModificationType.REMOVE_DATA, expected, ctx);
      control.replay();
      assert null == command.perform(ctx);
      assert nodes.adfgNode.getData().size() == 1;
      assert "newValue".equals(nodes.adfgNode.getData().get("newKey"));
      control.verify();

      control.reset();
      // won't do the peek if the oldValue was null.  Nothing to set anyway, why peek.
//      expect(container.peek(fqn, false, true)).andReturn(nodes.adfgNode);
      control.replay();
      command.rollback();
      assert nodes.adfgNode.getData().size() == 1;
      assert "newValue".equals(nodes.adfgNode.getData().get("newKey"));
      control.verify();
   }

   public void testRemoveExistentPair()
   {
      Map expected = new HashMap();
      expected.put(key, "newValue");
      nodes.adfgNode.putAll(expected);
      expect(container.peek(fqn)).andReturn(nodes.adfgNode);
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(fqn, true, NodeModifiedEvent.ModificationType.REMOVE_DATA, expected, ctx);
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(fqn, false, NodeModifiedEvent.ModificationType.REMOVE_DATA, expected, ctx);
      control.replay();
      assert "newValue" == command.perform(ctx);
      assert nodes.adfgNode.getData().get(key) == null;
      control.verify();

      control.reset();
      expect(container.peek(fqn, false, true)).andReturn(nodes.adfgNode);
      control.replay();
      command.rollback();
      assert nodes.adfgNode.getData().size() == 1;
      assert "newValue".equals(nodes.adfgNode.getData().get(key));
      control.verify();
   }

   /**
    * On an no-op scenario the user will try to remove a key on an unexisting node.
    * When rollback is being called, the node might not exist in the cache and we should know how to handle that.
    */
   public void testRollbackOnNoOp()
   {
      expect(container.peek(fqn, false, true)).andReturn(null);
      control.replay();
      command.rollback();
   }
}
