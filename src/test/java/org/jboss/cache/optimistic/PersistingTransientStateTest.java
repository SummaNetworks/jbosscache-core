package org.jboss.cache.optimistic;

import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "optimistic.PersistingTransientStateTest")
public class PersistingTransientStateTest extends org.jboss.cache.statetransfer.PersistingTransientStateTest
{
   public PersistingTransientStateTest()
   {
      nls = NodeLockingScheme.OPTIMISTIC;
   }
}
