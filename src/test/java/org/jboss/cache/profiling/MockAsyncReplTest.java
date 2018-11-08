package org.jboss.cache.profiling;

import org.jboss.cache.RPCManager;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.marshall.CommandAwareRpcDispatcher;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.reflect.ReflectionUtil;
import org.jgroups.Address;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.blocks.RspFilter;
import org.jgroups.util.RspList;
import org.testng.annotations.Test;

import java.io.NotSerializableException;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Importnat - make sure you inly enable these tests locally!
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 3.0
 */
@Test(groups = "profiling", enabled = false, testName="profiling.MockAsyncReplTest")
public class MockAsyncReplTest extends ProfileTest
{
   @Override
   @Test(enabled = false)
   public void testReplAsync() throws Exception
   {
      // same as superclass, except that we use a mock RpcDispatcher that does nothing.  Measure throughput to test speed of JBC stack.
      super.testReplAsync();
   }

   @Override
   protected void startup()
   {
      long startTime = System.currentTimeMillis();
      log.warn("Starting cache");
      cache.start();
      // now remove the existing RpcDispatcher and replace with one that is a noop.
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache);
      RPCManager rpcManager = cr.getComponent(RPCManager.class);
      RpcDispatcher d = (RpcDispatcher) TestingUtil.extractField(rpcManager, "rpcDispatcher");
      d.stop();
      RpcDispatcher replacement = new NoopDispatcher();
      replacement.setRequestMarshaller(d.getRequestMarshaller());
      replacement.setResponseMarshaller(d.getResponseMarshaller());
      ReflectionUtil.setValue(rpcManager, "rpcDispatcher", replacement);

      long duration = System.currentTimeMillis() - startTime;
      log.warn("Started cache.  " + printDuration(duration));
   }

   public static class NoopDispatcher extends CommandAwareRpcDispatcher
   {
      AtomicInteger ai = new AtomicInteger();
      Marshaller m;
      Marshaller2 m2;

      @Override
      public RspList invokeRemoteCommands(Vector<Address> dests, ReplicableCommand command, int mode, long timeout,
                                          boolean anycasting, boolean oob, RspFilter filter) throws NotSerializableException
      {
         // make sure we do the marshalling though
//         if (m == null && m2 == null)
//         {
//            m = getRequestMarshaller();
//            if (m instanceof Marshaller2)
//            {
//               m2 = (Marshaller2) m;
//               m = null;
//            }
//         }
//
//         try
//         {
//            if (m2 == null) m.objectToByteBuffer(command);
//            else m2.objectToBuffer(command);
//         }
//         catch (Exception e)
//         {
//            e.printStackTrace();
//            throw new NotSerializableException(e.getMessage());
//         }

         int i = ai.incrementAndGet();
         if (i % 1000 == 0) log.warn("Dispatching operation #" + i);
         // no-op
         return null;
      }
   }
}
