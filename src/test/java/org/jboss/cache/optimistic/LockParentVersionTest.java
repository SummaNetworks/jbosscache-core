package org.jboss.cache.optimistic;

import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional", "optimistic"}, testName = "optimistic.LockParentVersionTest")
public class LockParentVersionTest extends ParentVersionTest
{
   public LockParentVersionTest()
   {
      lockParentForChildInsertRemove = true;
   }
}
