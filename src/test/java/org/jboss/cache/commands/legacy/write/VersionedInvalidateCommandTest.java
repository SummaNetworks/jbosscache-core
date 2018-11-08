package org.jboss.cache.commands.legacy.write;

import static org.easymock.EasyMock.createStrictControl;
import static org.easymock.EasyMock.expect;
import org.easymock.IMocksControl;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.DataContainer;
import org.jboss.cache.commands.read.AbstractDataCommandTest;
import org.jboss.cache.mock.MockNodesFixture;
import org.jboss.cache.notifications.Notifier;
import org.jboss.cache.optimistic.DataVersion;
import org.jboss.cache.optimistic.DataVersioningException;
import org.jboss.cache.optimistic.DefaultDataVersion;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.Collections;

/**
 * tester class for {@link VersionedInvalidateCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", testName = "commands.legacy.write.VersionedInvalidateCommandTest")
public class VersionedInvalidateCommandTest extends AbstractDataCommandTest
{
   DataVersion dataVersion;
   VersionedInvalidateCommand command;
   IMocksControl control;

   Notifier notifier;
   TransactionManager tmMock;

   MockNodesFixture nodes;
   CacheSPI spiMock;

   protected void moreSetup()
   {
      control = createStrictControl();
      notifier = control.createMock(Notifier.class);
      container = control.createMock(DataContainer.class);
      tmMock = control.createMock(TransactionManager.class);
      spiMock = control.createMock(CacheSPI.class);
      nodes = new MockNodesFixture();

      command = new VersionedInvalidateCommand(testFqn);
      dataVersion = new DefaultDataVersion(10);
      command.setDataVersion(dataVersion);
      command.initialize(spiMock, container, notifier);
      command.initialize(tmMock);
   }

   public void testWithExistingNode()
   {
      nodes.adfNode.put("key", "value");
      nodes.adfNode.setVersion(dataVersion);
      nodes.adfNode.setDataLoaded(true);
      expect(spiMock.getNode(testFqn)).andReturn(nodes.adfNode);
      notifier.notifyNodeInvalidated(testFqn, true, ctx);
      notifier.notifyNodeInvalidated(testFqn, false, ctx);

      control.replay();
      assert null == command.perform(ctx);
      assert nodes.adfNode.getData().isEmpty();
      assert !nodes.adfNode.isDataLoaded();
      assert !nodes.adfNode.isValid();
      assert nodes.adfNode.getVersion().equals(dataVersion);

      control.verify();
   }

   public void testWithExistingNodeInvalidVersion()
   {
      nodes.adfNode.put("key", "value");
      nodes.adfNode.setDataLoaded(true);
      nodes.adfNode.setVersion(new DefaultDataVersion(100));
      expect(spiMock.getNode(testFqn)).andReturn(nodes.adfNode);
      control.replay();

      try
      {
         command.perform(ctx);
         assert false : "exception expected";
      }
      catch (DataVersioningException e)
      {
         //expected as there is a version mismatch
      }
      assert !nodes.adfNode.getData().isEmpty();
      assert nodes.adfNode.isDataLoaded();
      assert nodes.adfNode.isValid();
      assert !dataVersion.equals(nodes.adfNode.getVersion());

      control.verify();
   }

   public void testExistingTombstone()
   {
      nodes.adfNode.setValid(false, true);
      nodes.adfNode.setVersion(dataVersion);
      expect(spiMock.getNode(testFqn)).andReturn(null);
      expect(container.peek(testFqn, true, true)).andReturn(nodes.adfNode);
      notifier.notifyNodeInvalidated(testFqn, true, ctx);
      notifier.notifyNodeInvalidated(testFqn, false, ctx);

      control.replay();
      assert null == command.perform(ctx);
      assert nodes.adfNode.getData().isEmpty();
      assert !nodes.adfNode.isDataLoaded();
      assert !nodes.adfNode.isValid();
      assert nodes.adfNode.getVersion().equals(dataVersion);
      control.verify();
   }

   public void testCreateTombstone() throws Exception
   {
      Transaction tx = control.createMock(Transaction.class);
      expect(tmMock.suspend()).andReturn(tx);
      spiMock.put(testFqn, Collections.emptyMap());
      tmMock.resume(tx);

      control.replay();
      command.createTombstone(ctx);
      control.verify();
   }

   public void testCreateTombstoneNoTx() throws Exception
   {
      expect(tmMock.suspend()).andReturn(null);
      spiMock.put(testFqn, Collections.EMPTY_MAP);

      control.replay();
      command.createTombstone(ctx);
      control.verify();
   }
}
