package org.jboss.cache.loader.testloaders;

public class DummySharedInMemoryCacheLoaderConfig extends DummyInMemoryCacheLoaderConfig
{
   public DummySharedInMemoryCacheLoaderConfig()
   {
      setClassName(DummySharedInMemoryCacheLoader.class.getName());
   }

   public DummySharedInMemoryCacheLoaderConfig(String storeName)
   {
      super(storeName);
      setClassName(DummySharedInMemoryCacheLoader.class.getName());
   }
}
