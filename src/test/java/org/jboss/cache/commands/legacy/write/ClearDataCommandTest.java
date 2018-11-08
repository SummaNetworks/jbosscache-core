package org.jboss.cache.commands.legacy.write;

import static org.easymock.EasyMock.expect;
import org.jboss.cache.commands.write.AbstractVersionedDataCommand;
import org.jboss.cache.commands.write.AbstractVersionedDataCommandTest;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import org.testng.annotations.Test;

import java.util.Collections;

/**
 * tester class for {@link org.jboss.cache.commands.write.ClearDataCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.legacy.write.ClearDataCommandTest")
public class ClearDataCommandTest extends AbstractVersionedDataCommandTest
{

   PessClearDataCommand command;

   public AbstractVersionedDataCommand moreSetUp()
   {
      command = new PessClearDataCommand(globalTransaction, fqn);
      command.setDataVersion(dataVersion);
      return command;
   }

   public void testNonexistentNode()
   {
      // will happen twice - once in the Pess subclass.
      expect(container.peek(fqn)).andReturn(null);
      expect(container.peek(fqn)).andReturn(null);
      control.replay();
      assert null == command.perform(ctx);
      control.verify();
   }

   public void testExistentDataVersioned()
   {
      nodes.adfgNode.put("key", "value");
      nodes.adfgNode.setVersion(dataVersion);
      // will happen twice - once in the Pess subclass.
      expect(container.peek(fqn)).andReturn(nodes.adfgNode);
      expect(container.peek(fqn)).andReturn(nodes.adfgNode);
      notifier.notifyNodeModified(fqn, true, NodeModifiedEvent.ModificationType.REMOVE_DATA, nodes.adfgNode.getDataDirect(), ctx);
      notifier.notifyNodeModified(fqn, false, NodeModifiedEvent.ModificationType.REMOVE_DATA, Collections.EMPTY_MAP, ctx);
      control.replay();
      assert null == command.perform(ctx);
      assert nodes.adfgNode.getData().isEmpty();
      control.verify();

      //now do a rollback
      control.reset();
      expect(container.peek(fqn, false, true)).andReturn(nodes.aNode);
      control.replay();
      command.rollback();
      assert nodes.aNode.dataSize() == 1;
      assert nodes.aNode.getData().get("key").equals("value");
   }

   public void testExistentDataUnversioned()
   {
      command.setDataVersion(null);
      nodes.adfgNode.put("key", "value");
      // will happen twice - once in the Pess subclass.
      expect(container.peek(fqn)).andReturn(nodes.adfgNode);
      expect(container.peek(fqn)).andReturn(nodes.adfgNode);
      notifier.notifyNodeModified(fqn, true, NodeModifiedEvent.ModificationType.REMOVE_DATA, nodes.adfgNode.getDataDirect(), ctx);
      notifier.notifyNodeModified(fqn, false, NodeModifiedEvent.ModificationType.REMOVE_DATA, Collections.EMPTY_MAP, ctx);
      control.replay();
      assert null == command.perform(ctx);
      assert nodes.adfgNode.getData().isEmpty();
      control.verify();

      //now do a rollback
      control.reset();
      expect(container.peek(fqn, false, true)).andReturn(nodes.aNode);
      control.replay();
      command.rollback();
      assert nodes.aNode.dataSize() == 1;
      assert nodes.aNode.getData().get("key").equals("value");
   }

   /**
    * If clearing data on an inexistent node, the rollback should not fail
    */
   public void testNoOpRollback()
   {
      expect(container.peek(fqn, false, true)).andReturn(null);
      control.replay();
      try
      {
         command.rollback();
      }
      catch (Exception e)
      {
         assert false : "should not fail but expect this scenarion";
      }
   }
}
