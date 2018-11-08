package org.jboss.cache.api.mvcc;

import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "api.mvcc.PersistingTransientStateTest")
public class PersistingTransientStateTest extends org.jboss.cache.statetransfer.PersistingTransientStateTest
{
   public PersistingTransientStateTest()
   {
      nls = NodeLockingScheme.MVCC;
   }
}