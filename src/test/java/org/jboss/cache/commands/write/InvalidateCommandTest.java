package org.jboss.cache.commands.write;

import static org.easymock.EasyMock.createStrictControl;
import static org.easymock.EasyMock.expect;
import org.easymock.IMocksControl;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.DataContainer;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.read.AbstractDataCommandTest;
import org.jboss.cache.mock.MockNodesFixture;
import org.jboss.cache.notifications.Notifier;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;

/**
 * Tester class for {@link org.jboss.cache.commands.write.InvalidateCommand}
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.write.InvalidateCommandTest")
public class InvalidateCommandTest extends AbstractDataCommandTest
{
   InvalidateCommand command;
   Notifier notifier;
   IMocksControl control;
   MockNodesFixture nodes;
   TransactionManager tmMock;
   CacheSPI spiMock;

   protected void moreSetup()
   {
      control = createStrictControl();
      notifier = control.createMock(Notifier.class);
      container = control.createMock(DataContainer.class);
      tmMock = control.createMock(TransactionManager.class);
      spiMock = control.createMock(CacheSPI.class);

      command = new InvalidateCommand(testFqn);
      command.initialize(spiMock, container, notifier);
      nodes = new MockNodesFixture();
   }

   public void testNullNode()
   {
      expect(spiMock.getNode(testFqn)).andReturn(null);
      control.replay();
      assert null == command.perform(ctx);
      control.verify();
   }

   public void testExistingNode()
   {
      expect(spiMock.getNode(testFqn)).andReturn(nodes.adfNode);
      notifier.notifyNodeInvalidated(testFqn, true, ctx);
      expect(container.evict(testFqn)).andReturn(Boolean.TRUE);
      notifier.notifyNodeInvalidated(testFqn, false, ctx);
      control.replay();
      assert null == command.perform(ctx);
      assert !nodes.adfNode.isValid() : "node should had been invalidated";
      assert !nodes.adfgNode.isValid() : "child should had been invalidated";
      assert !nodes.adfhNode.isValid() : "child should had been invalidated";
      control.verify();
   }

   public void testRootNodeInvalidation()
   {
      command.setFqn(Fqn.ROOT);
      nodes.adfgNode.put("key", "value");
      expect(spiMock.getNode(Fqn.ROOT)).andReturn(nodes.root);
      notifier.notifyNodeInvalidated(Fqn.ROOT, true, ctx);
      expect(container.evict(Fqn.ROOT)).andReturn(Boolean.TRUE);
      notifier.notifyNodeInvalidated(Fqn.ROOT, false, ctx);
      control.replay();
      assert null == command.perform(ctx);
      assert nodes.root.isValid() : "root should NOT had been invalidated";
      assert !nodes.adfgNode.isValid() : "node should had been invalidated";
      control.verify();
   }

   public void testInvalidateResidentNode()
   {
      nodes.adfNode.setResident(true);
      testExistingNode();
   }
}
