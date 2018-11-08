package org.jboss.cache.loader.testloaders;

import org.jboss.cache.Fqn;
import org.jboss.cache.config.CacheLoaderConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An extension of the {@link DummyInMemoryCacheLoader} that uses static maps for data, children,
 * etc. so it can be shared across instances, emulating a shared database or filesystem cache loader.
 * <p/>
 * Since 2.1.0, this dummy cache loader will take an optional parameter, "bin", which contains the name of the "bin" to use
 * in the static field to store the content.  This allows for tests to mimic multiple shared cache loaders in the same cache.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
public class DummySharedInMemoryCacheLoader extends DummyInMemoryCacheLoader
{
   protected static final Map<String, Map<Fqn, DummyNode>> BINS = new ConcurrentHashMap<String, Map<Fqn, DummyNode>>();
   private String bin = "_default_bin_";

   @Override
   public void setConfig(CacheLoaderConfig.IndividualCacheLoaderConfig cfg)
   {
      super.setConfig(cfg);

      if (config != null && config.getProperties() != null)
      {
         bin = config.getProperties().getProperty("bin");
         if (bin == null)
         {
            throw new IllegalStateException("bin MUST be present for shared state CL. This is because tests might run concurrently!");
         }
      }

      if (!BINS.containsKey(bin)) BINS.put(bin, new ConcurrentHashMap<Fqn, DummyNode>());
      nodes = null; // set this to null so any method in superclass that uses this directly will barf with an NPE 
   }

   @Override
   protected Map<Fqn, DummyNode> getNodesMap()
   {
      return BINS.get(bin);
   }

   @Override
   public void wipe()
   {
      BINS.clear();
   }
   
   public void wipeBin() {
      BINS.remove(bin);
   }
}
