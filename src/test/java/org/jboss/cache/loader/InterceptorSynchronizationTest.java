package org.jboss.cache.loader;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Modification;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.Test;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.cache.UnitTestCacheFactory;

/**
 * Test case that proves that the CacheImpl is serialized inside the CacheLoaderInterceptor
 * when the node is empty.  ANY call to retrieve a node that is not loaded will lock
 * up inside CacheLoaderInterceptor while the node is loaded and any other thread
 * that wants a node out of the cache will wait for the first one to finish before it can even _start_ loading.
 *
 * @author paulsmith
 */
@Test(groups = {"functional"}, testName = "loader.InterceptorSynchronizationTest")
public class InterceptorSynchronizationTest
{

   final int CACHELOADER_WAITTIME = 2000;// lets say loading a node takes 2 seconds
   final int numThreadsPerTopLevelNode = 5;// we'll have 5 requests for nodes within a branch


   @SuppressWarnings("deprecation")
   public void testBlockingProblem() throws Exception
   {

      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      //setCacheLoader(new TestSlowCacheLoader());
      CacheLoaderConfig clc = new CacheLoaderConfig();
      IndividualCacheLoaderConfig iclc = new IndividualCacheLoaderConfig();
      //iclc.setClassName(TestSlowCacheLoader.class.getName());
      iclc.setCacheLoader(new TestSlowCacheLoader());
      clc.addIndividualCacheLoaderConfig(iclc);
      cache.getConfiguration().setCacheLoaderConfig(clc);
      cache.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      cache.start();

      long begin = System.currentTimeMillis();
      Collection<Thread> threads = new ArrayList<Thread>();

      /*
      * Create lots of threads all trying to load DIFFERENT fqn's, as well as a set with different top level nodes.
      */

      for (int i = 0; i < numThreadsPerTopLevelNode; i++)
      {
         Thread thread = new Thread(new Retriever(cache, "/Moo/" + i));
         threads.add(thread);
         Thread thread2 = new Thread(new Retriever(cache, "/Meow/" + i));
         threads.add(thread2);
      }
      for (Thread thread : threads)
      {
         thread.start();
      }

      for (Thread thread : threads)
      {
         thread.join();
      }

      long end = System.currentTimeMillis();
      long timeTaken = (end - begin);

      /*
      * My expectation is that if there are 2 top level nodes they should be loaded in parallel at the very least,
      * but even bottom level nodes that are different should be able to be loaded concurrently.
      *
      * In this test, NONE of the threads operate in parallel once entered into CacheLoaderInterceptor.
      */
      int totalTimeExpectedToWaitIfNotSerialized = 3 * CACHELOADER_WAITTIME;// i'm being very generous here.   3 times the wait time for each node is more than enough if it was in parallel.
      assertTrue("If it was parallel, it should have finished quicker than this:" + timeTaken, timeTaken < totalTimeExpectedToWaitIfNotSerialized);
   }

   /**
    * Dummy cache loader that emulates a slow loading of any node from a virtual backing store.
    *
    * @author paulsmith
    */
   public class TestSlowCacheLoader extends AbstractCacheLoader
   {
      public void setConfig(IndividualCacheLoaderConfig config)
      {
      }

      public IndividualCacheLoaderConfig getConfig()
      {
         return null;
      }

      public Set<?> getChildrenNames(Fqn arg0) throws Exception
      {
         return null;
      }

      public Object get(Fqn arg0, Object arg1)
      {
         return null;
      }

      public Map<Object, Object> get(Fqn arg0) throws Exception
      {
         Thread.sleep(CACHELOADER_WAITTIME);
         return Collections.singletonMap((Object) "foo", (Object) "bar");
      }

      public boolean exists(Fqn arg0) throws Exception
      {
         return true;
      }

      public Object put(Fqn arg0, Object arg1, Object arg2) throws Exception
      {
         return null;
      }

      public void put(Fqn arg0, Map arg1) throws Exception
      {

      }

      public void put(List<Modification> modifications) throws Exception
      {
      }

      public Object remove(Fqn arg0, Object arg1) throws Exception
      {
         return null;
      }

      public void remove(Fqn arg0) throws Exception
      {

      }

      public void removeData(Fqn arg0) throws Exception
      {

      }

      public void prepare(Object tx, List<Modification> modifications, boolean one_phase) throws Exception
      {
      }

      public void commit(Object arg0) throws Exception
      {

      }

      public void rollback(Object arg0)
      {
      }

      public void loadEntireState(ObjectOutputStream os) throws Exception
      {
         // nothing to do here
      }

      public void loadState(Fqn subtree, ObjectOutputStream os) throws Exception
      {
         // nothing to do here
      }

      public void storeEntireState(ObjectInputStream is) throws Exception
      {
         // nothing to do here
      }

      public void storeState(Fqn subtree, ObjectInputStream is) throws Exception
      {
         // nothing to do here
      }

      public void create() throws Exception
      {

      }

      public void start() throws Exception
      {

      }

      public void stop()
      {

      }

      public void destroy()
      {

      }

   }


   private static class Retriever implements Runnable
   {
      private final String fqn;
      private CacheSPI<Object, Object> cache;

      private Retriever(CacheSPI<Object, Object> cache, String fqn)
      {
         this.fqn = fqn;
         this.cache = cache;
      }

      public void run()
      {
         try
         {
            cache.get(fqn, "foo");
         }
         catch (CacheException e)
         {
            throw new RuntimeException("Unexpected", e);
         }

      }
   }
}
