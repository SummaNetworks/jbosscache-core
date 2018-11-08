package org.jboss.cache.transaction.isolationlevels;

import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = "functional", testName = "transaction.isolationlevels.ReadUncommittedTest")
public class ReadUncommittedTest extends IsolationLevelTestBase
{
   public ReadUncommittedTest()
   {
      isolationLevel = IsolationLevel.READ_UNCOMMITTED;
   }
}
