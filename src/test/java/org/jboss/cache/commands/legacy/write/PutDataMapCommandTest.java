package org.jboss.cache.commands.legacy.write;

import static org.easymock.EasyMock.createStrictControl;
import static org.easymock.EasyMock.expect;
import org.easymock.IMocksControl;
import org.jboss.cache.DataContainer;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.commands.TestContextBase;
import org.jboss.cache.mock.NodeSpiMock;
import org.jboss.cache.notifications.Notifier;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import org.jboss.cache.transaction.GlobalTransaction;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tester class for {@link org.jboss.cache.commands.write.PutDataMapCommand}
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", testName = "commands.legacy.write.PutDataMapCommandTest")
public class PutDataMapCommandTest extends TestContextBase
{
   Fqn testFqn = Fqn.fromString("/testfqn");
   PessPutDataMapCommand command;
   GlobalTransaction gtx;
   Notifier notifier;
   DataContainer container;
   Map dataMap;
   IMocksControl control;
   NodeSpiMock node;
   InvocationContext ctx;


   @BeforeMethod
   protected void setUp()
   {
      gtx = new GlobalTransaction();
      dataMap = new HashMap();
      command = new PessPutDataMapCommand(gtx, testFqn, dataMap);
      control = createStrictControl();
      notifier = control.createMock(Notifier.class);
      container = control.createMock(DataContainer.class);
      command.initialize(notifier, container);
      node = new NodeSpiMock(testFqn);
      node.put("k", "v");
      ctx = createLegacyInvocationContext(container);
   }

   public void testAddDataNoErase()
   {
      // will happen twice - once in the Pess subclass.
      expect(container.peek(testFqn)).andReturn(node);
      expect(container.peek(testFqn)).andReturn(node);
      dataMap.put("k2", "v2");
      Map expected = new HashMap(dataMap);
      expected.putAll(node.getDataDirect());
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(testFqn, true, NodeModifiedEvent.ModificationType.PUT_MAP, node.getData(), ctx);
      expect(notifier.shouldNotifyOnNodeModified()).andReturn(true);
      notifier.notifyNodeModified(testFqn, false, NodeModifiedEvent.ModificationType.PUT_MAP, expected, ctx);

      control.replay();
      assert null == command.perform(ctx) : "null result is always expected";
      assert command.oldData.size() == 1;
      assert command.oldData.get("k").equals("v");
      control.verify();
   }


   public void testRollbackNonexistentNode()
   {
      expect(container.peek(testFqn, false, true)).andReturn(null);
      control.replay();
      command.rollback();
      control.verify();
   }
}
