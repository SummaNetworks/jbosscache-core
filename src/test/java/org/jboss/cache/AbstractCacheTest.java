package org.jboss.cache;

import org.jboss.cache.loader.CacheLoaderManager;

/**
 * @author Mircea.Markus@jboss.com
 */
public class AbstractCacheTest<K, V>
{
   /**
    * Importnat: do not reset RegionManager (rm.reset()) here, as this woulfd cause eviction region to be clered.
    * If that is needed, do it in @BeforeMethods
    */
   public void clearContent(CacheSPI<K, V> cache)
   {
      clearRunningTx(cache);
      if (!cache.getCacheStatus().allowInvocations()) return;
      removeInMemoryData(cache);
      clearCacheLoader(cache);
      //impoortant!!! keep invocation ctxt cleanup as the last line in the cleanup process, prev calls modify
      // OptionOverrides
      cache.getInvocationContext().reset();
   }

   private void clearCacheLoader(CacheSPI<K, V> cache)
   {
      CacheLoaderManager cacheLoaderManager = cache.getCacheLoaderManager();
      if (cacheLoaderManager != null && cacheLoaderManager.getCacheLoader() != null)
      {
         try
         {
            cacheLoaderManager.getCacheLoader().remove(Fqn.ROOT);
         } catch (Exception e)
         {
            throw new RuntimeException(e);
         }
      }
   }

   private void removeInMemoryData(CacheSPI<K, V> cache)
   {
      if (cache.getRoot() != null)
      {
         cache.getRoot().clearDataDirect();
         cache.getRoot().removeChildrenDirect();
      }
   }

   private void clearRunningTx(CacheSPI<K, V> cache)
   {
      if (cache != null && cache.getTransactionManager() != null)
      {
         try
         {
            cache.getTransactionManager().rollback();
         }
         catch (Exception e)
         {
            // don't care
         }
      }
   }
}
