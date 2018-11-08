package org.jboss.cache.commands;

import org.jboss.cache.DataContainer;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.invocation.LegacyInvocationContext;
import org.jboss.cache.invocation.MVCCInvocationContext;

public class TestContextBase
{
   protected InvocationContext createMVCCInvocationContext()
   {
      return new MVCCInvocationContext();
   }

   protected InvocationContext createLegacyInvocationContext(DataContainer dc)
   {
      return new LegacyInvocationContext(dc);
   }
}
