package org.jboss.cache.marshall;

import org.testng.annotations.Test;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = {"functional", "jgroups"}, testName = "marshall.SyncReplMarshalledValuesTest")
public class SyncReplMarshalledValuesTest extends SyncReplTest
{
   public SyncReplMarshalledValuesTest()
   {
      useMarshalledValues = true;
   }

   @Override
   public void testCustomFqn()
   {
      // meaningless when using marshalled values
   }
}
