package org.jboss.cache.api.mvcc;

import org.jboss.cache.api.CacheSPITest;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.CacheSPIMVCCTest")
public class CacheSPIMVCCTest extends CacheSPITest
{
   public CacheSPIMVCCTest()
   {
      nodeLockingScheme = NodeLockingScheme.MVCC;
   }
}
