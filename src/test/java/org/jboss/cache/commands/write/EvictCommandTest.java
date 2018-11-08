package org.jboss.cache.commands.write;

import static org.easymock.EasyMock.createStrictControl;
import static org.easymock.EasyMock.expect;
import org.easymock.IMocksControl;
import org.jboss.cache.DataContainer;
import org.jboss.cache.Fqn;
import org.jboss.cache.commands.legacy.write.LegacyEvictCommand;
import org.jboss.cache.commands.read.AbstractDataCommandTest;
import org.jboss.cache.mock.MockNodesFixture;
import org.jboss.cache.notifications.Notifier;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * tester class for {@link EvictCommand}
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "commands.write.EvictCommandTest")
public class EvictCommandTest extends AbstractDataCommandTest
{
   EvictCommand command;
   Notifier notifier;
   IMocksControl control;
   MockNodesFixture nodes;

   protected void moreSetup()
   {
      control = createStrictControl();
      notifier = control.createMock(Notifier.class);
      container = control.createMock(DataContainer.class);
      command = new LegacyEvictCommand(testFqn);
      command.initialize(notifier, container);
      nodes = new MockNodesFixture();
   }

   public void testResidentNodesEviction()
   {
      nodes.abNode.setResident(true);
      expect(container.peek(testFqn, false, true)).andReturn(nodes.abNode);
      control.replay();
      assert Boolean.TRUE == command.perform(ctx);
      control.verify();
   }

   public void testShouldReturnTrueIndicatingNodeIsAbsentIfNodeDoesntExist()
   {
      expect(container.peek(testFqn, false, true)).andReturn(null);
      control.replay();
      assert Boolean.TRUE == command.perform(ctx);
      control.verify();
   }

   public void testSimpleEviction()
   {
      expect(container.peek(testFqn, false, true)).andReturn(nodes.abNode);
      notifier.notifyNodeEvicted(testFqn, true, ctx);
      notifier.notifyNodeEvicted(testFqn, false, ctx);
      control.replay();
      assert Boolean.FALSE == command.perform(ctx);
      control.verify();
   }

   public void testRecursiveEviction()
   {
      List<Fqn> nodesToEvict = new ArrayList<Fqn>();
      nodesToEvict.add(nodes.a);
      nodesToEvict.add(nodes.ab);
      command.setRecursive(true);
      expect(container.peek(testFqn, false, true)).andReturn(nodes.aNode);

      expect(container.getNodesForEviction(testFqn, true)).andReturn(nodesToEvict);
      control.checkOrder(false);
      //evict a
      expect(container.peek(nodes.a, false, true)).andReturn(nodes.aNode);
      notifier.notifyNodeEvicted(nodes.a, true, ctx);
      notifier.notifyNodeEvicted(nodes.a, false, ctx);

      //evict b
      expect(container.peek(nodes.ab, false, true)).andReturn(nodes.abNode);
      notifier.notifyNodeEvicted(nodes.ab, true, ctx);
      notifier.notifyNodeEvicted(nodes.ab, false, ctx);

      control.replay();
      assert Boolean.TRUE == command.perform(ctx);
      control.verify();
   }
}
