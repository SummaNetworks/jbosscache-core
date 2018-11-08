package org.jboss.cache.util.internals;

import org.jboss.cache.Cache;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.cluster.ReplicationQueue;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.interceptors.BaseRpcInterceptor;
import org.jboss.cache.interceptors.InterceptorChain;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.invocation.CacheInvocationDelegate;

import java.util.List;

/**
 * Knows how to notify one whether on certain state changes in the replication queue.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class ReplicationQueueNotifier
{
   private CacheInvocationDelegate cache;
   private Object replicated = new Object();

   public ReplicationQueueNotifier(Cache cache)
   {
      this.cache = (CacheInvocationDelegate) cache;
      if (!isAsync(cache))
      {
         throw new RuntimeException("No  queue events expected on a sync cache!");
      }
      replaceInternal();
   }

   private boolean isAsync(Cache cache)
   {
      return cache.getConfiguration().getCacheMode() == Configuration.CacheMode.INVALIDATION_ASYNC ||
            cache.getConfiguration().getCacheMode() == Configuration.CacheMode.REPL_ASYNC;
   }

   private void replaceInternal()
   {
      ComponentRegistry componentRegistry = TestingUtil.extractComponentRegistry(cache);
      InterceptorChain ic = componentRegistry.getComponent(InterceptorChain.class);
      List<CommandInterceptor> commands = ic.getInterceptorsWhichExtend(BaseRpcInterceptor.class);
      for (CommandInterceptor interceptor: commands)
      {
         ReplicationQueue original = (ReplicationQueue) TestingUtil.extractField(BaseRpcInterceptor.class, interceptor, "replicationQueue");
         TestingUtil.replaceField(new ReplicationQueueDelegate(original),"replicationQueue", interceptor, BaseRpcInterceptor.class);
      }
   }

   public void waitUntillAllReplicated(long timeout)
   {
      synchronized (replicated)
      {
         try
         {
            replicated.wait(timeout);
         } catch (InterruptedException e)
         {
            throw new RuntimeException(e);
         }
      }
   }

   private class ReplicationQueueDelegate extends ReplicationQueue
   {
      ReplicationQueue original;

      private ReplicationQueueDelegate(ReplicationQueue original)
      {
         this.original = original;
      }

      @Override
      public void flush()
      {
         original.flush();
         synchronized (replicated)
         {
            replicated.notifyAll();
         }
      }
   }
}
