package org.jboss.cache.statetransfer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.loader.FileCacheLoader;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class CorruptedFileCacheLoader extends FileCacheLoader
{
   private Log log = LogFactory.getLog(CorruptedFileCacheLoader.class);
   private CacheLoaderConfig.IndividualCacheLoaderConfig cfg;

   @Override
   public void setConfig(CacheLoaderConfig.IndividualCacheLoaderConfig base)
   {
      this.cfg = base;
      super.setConfig(base);
   }

   @Override
   public CacheLoaderConfig.IndividualCacheLoaderConfig getConfig()
   {
      return cfg;
   }

   @Override
   public void loadState(Fqn subtree, ObjectOutputStream os) throws Exception
   {
      throw new IOException("see StateTransfer200Test#testCacheLoaderFailure()");
   }
}
