package org.jboss.cache.marshall;

import org.testng.annotations.Test;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = {"functional", "jgroups"}, testName = "marshall.AsyncReplMarshalledValuesTest")
public class AsyncReplMarshalledValuesTest extends AsyncReplTest
{
   public AsyncReplMarshalledValuesTest()
   {
      useMarshalledValues = true;
   }

   @Override
   public void testCustomFqn()
   {
      // don't test this case
   }
}
