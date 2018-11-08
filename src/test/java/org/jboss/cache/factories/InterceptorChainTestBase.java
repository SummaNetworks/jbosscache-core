package org.jboss.cache.factories;

import org.jboss.cache.interceptors.base.CommandInterceptor;
import static org.testng.AssertJUnit.assertEquals;

import java.util.List;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 */
public abstract class InterceptorChainTestBase
{
   protected void assertInterceptorLinkage(List<CommandInterceptor> list)
   {
      CommandInterceptor previous = null;
      for (CommandInterceptor i : list)
      {
         if (previous == null)
         {
            previous = i;
            continue;
         }

         assertEquals("Expecting the next interceptor after " + previous + " to be " + i, i, previous.getNext());

         previous = i;
      }
   }
}