package org.jboss.cache.commands.write;

import static org.easymock.EasyMock.createStrictControl;
import org.easymock.IMocksControl;
import org.jboss.cache.DataContainer;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.commands.TestContextBase;
import org.jboss.cache.mock.MockNodesFixture;
import org.jboss.cache.notifications.Notifier;
import org.jboss.cache.optimistic.DataVersion;
import org.jboss.cache.optimistic.DefaultDataVersion;
import org.jboss.cache.transaction.GlobalTransaction;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Base class for testing {@link AbstractVersionedDataCommand}.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit")
public abstract class AbstractVersionedDataCommandTest extends TestContextBase
{
   protected Notifier notifier;
   protected DataContainer container;
   protected IMocksControl control;

   protected MockNodesFixture nodes;
   protected DataVersion dataVersion;
   protected GlobalTransaction globalTransaction;
   protected Fqn fqn = Fqn.fromString("/test/fqn");
   protected InvocationContext ctx;

   @BeforeMethod
   public final void setUp()
   {
      control = createStrictControl();
      container = control.createMock(DataContainer.class);
      notifier = control.createMock(Notifier.class);
      nodes = new MockNodesFixture();
      globalTransaction = new GlobalTransaction();
      dataVersion = new DefaultDataVersion(10);
      ctx = createLegacyInvocationContext(container);

      AbstractVersionedDataCommand command = moreSetUp();
      command.initialize(notifier, container);
   }

   public abstract AbstractVersionedDataCommand moreSetUp();
}
