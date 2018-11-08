package org.jboss.cache.api.batch;

import org.jboss.cache.Cache;
import org.jboss.cache.AbstractSingleCacheTest;

import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractBatchTest extends AbstractSingleCacheTest<String, String>
{
   protected String getOnDifferentThread(final Cache<String, String> cache, final String fqn, final String key) throws InterruptedException
   {
      final AtomicReference<String> ref = new AtomicReference<String>();
      Thread t = new Thread()
      {
         public void run()
         {
            ref.set(cache.get(fqn, key));
         }
      };

      t.start();
      t.join();
      return ref.get();
   }
}
