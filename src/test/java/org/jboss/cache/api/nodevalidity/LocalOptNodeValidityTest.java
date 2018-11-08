package org.jboss.cache.api.nodevalidity;

import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "optimistic"}, testName = "api.nodevalidity.LocalOptNodeValidityTest")
public class LocalOptNodeValidityTest extends LocalPessNodeValidityTest
{
   public LocalOptNodeValidityTest()
   {
      nodeLockingScheme = NodeLockingScheme.OPTIMISTIC;
   }
}