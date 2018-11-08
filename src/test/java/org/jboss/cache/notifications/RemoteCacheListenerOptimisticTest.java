package org.jboss.cache.notifications;

import org.testng.annotations.Test;

/**
 * optimistic counterpart of {@link org.jboss.cache.notifications.RemoteCacheListenerTest}
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional", "optimistic"}, testName = "notifications.RemoteCacheListenerOptimisticTest")
public class RemoteCacheListenerOptimisticTest extends RemoteCacheListenerTest
{
   public RemoteCacheListenerOptimisticTest()
   {
      optLocking = true;
   }

   @Override
   public void testStateTransfer() throws Exception
   {
      // no op
   }

}
