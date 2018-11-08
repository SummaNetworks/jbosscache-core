package org.jboss.cache.loader.testloaders;

import org.jboss.cache.config.CacheLoaderConfig;

import java.util.Properties;
import java.io.IOException;

public class DummyInMemoryCacheLoaderConfig extends CacheLoaderConfig.IndividualCacheLoaderConfig
{
   String storeName;
   public DummyInMemoryCacheLoaderConfig()
   {
      setClassName(DummyInMemoryCacheLoader.class.getName());
   }

   public DummyInMemoryCacheLoaderConfig(String storeName)
   {
      setClassName(DummyInMemoryCacheLoader.class.getName());
      this.storeName = storeName;
   }

   public void setProperties(Properties p)
   {
      if (storeName != null)
      {
         if (p == null) p = new Properties();
         p.setProperty("bin", storeName);
      }
      super.setProperties(p);
   }

   public void setProperties(String s) throws IOException
   {
      super.setProperties(s);
      if (storeName != null)
      {
         this.properties.setProperty("bin", storeName);
      }
   }
}
