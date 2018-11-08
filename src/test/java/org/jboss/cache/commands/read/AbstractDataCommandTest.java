package org.jboss.cache.commands.read;

import static org.easymock.EasyMock.createMock;
import org.jboss.cache.DataContainer;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.commands.TestContextBase;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Class that has convinience fixture for all tests that operated on {@link AbstractDataCommand}
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit")
public abstract class AbstractDataCommandTest extends TestContextBase
{
   protected Fqn testFqn = Fqn.fromString("/testfqn");
   protected DataContainer container;
   protected InvocationContext ctx;

   @BeforeMethod
   final public void setUp()
   {
      container = createMock(DataContainer.class);
      moreSetup();
      ctx = createLegacyInvocationContext(container);
   }

   /**
    * called by setUp after initializing the testFqn and containeMock.
    */
   protected abstract void moreSetup();

}
