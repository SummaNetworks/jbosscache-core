/*
 * JBoss, the OpenSource J2EE webOS
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package org.jboss.cache.util;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.CacheStatus;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.CommandsFactory;
import org.jboss.cache.commands.VisitableCommand;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.interceptors.InterceptorChain;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.invocation.CacheInvocationDelegate;
import org.jboss.cache.loader.CacheLoader;
import org.jboss.cache.loader.CacheLoaderManager;
import org.jboss.cache.lock.LockManager;
import org.jgroups.Channel;
import org.jgroups.JChannel;

import javax.transaction.TransactionManager;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utilities for unit testing JBossCache.
 *
 * @author <a href="mailto://brian.stansberry@jboss.com">Brian Stansberry</a>
 */
public class TestingUtil
{
   private static Random random = new Random();

   public static final String TEST_FILES = "testFiles";

   /**
    *  Holds unique mcast_port for each thread used for JGroups channel construction.
    */
   private static final ThreadLocal<Integer> threadID = new ThreadLocal<Integer>() {

      private final AtomicInteger uniquePort = new AtomicInteger(0);

      @Override protected Integer initialValue() {
         return uniquePort.getAndIncrement();
      }
   };

   private static UnitTestCacheFactory utf = new UnitTestCacheFactory();


   /**
    * Extracts the value of a field in a given target instance using reflection, able to extract private fields as well.
    *
    * @param target    object to extract field from
    * @param fieldName name of field to extract
    * @return field value
    */
   public static Object extractField(Object target, String fieldName)
   {
      return extractField(target.getClass(), target, fieldName);
   }

   public static void replaceField(Object newValue, String fieldName, Object owner, Class baseType)
   {
      Field field;
      try
      {
         field = baseType.getDeclaredField(fieldName);
         field.setAccessible(true);
         field.set(owner, newValue);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);//just to simplify exception handeling
      }
   }


   public static Object extractField(Class type, Object target, String fieldName)
   {
      Field field;
      try
      {
         field = type.getDeclaredField(fieldName);
         field.setAccessible(true);
         return field.get(target);
      }
      catch (Exception e)
      {
         if (type.equals(Object.class))
         {
            e.printStackTrace();
            return null;
         }
         else
         {
            // try with superclass!!
            return extractField(type.getSuperclass(), target, fieldName);
         }
      }
   }

   public static <T extends CommandInterceptor> T findInterceptor(CacheSPI<?, ?> cache, Class<T> interceptorToFind)
   {
      for (CommandInterceptor i : cache.getInterceptorChain())
      {
         if (interceptorToFind.isInstance(i)) return interceptorToFind.cast(i);
      }
      return null;
   }

   /**
    * Injects an interceptor after a specified interceptor in a running cache.  Your new interceptor need not be
    * initialised with pointers to the next interceptor, etc. as this method does all that for you, including calling
    * setCache().
    *
    * @param cache                         running cache instance
    * @param interceptorToInject           interceptor instance to inject.
    * @param interceptorAfterWhichToInject class of interceptor to search for in the chain and after which to add your interceptor
    */
   public static void injectInterceptor(CacheSPI<?, ?> cache, CommandInterceptor interceptorToInject, Class<? extends CommandInterceptor> interceptorAfterWhichToInject)
   {
      cache.addInterceptor(interceptorToInject, interceptorAfterWhichToInject);
   }

   /**
    * Loops, continually calling {@link #areCacheViewsComplete(org.jboss.cache.Cache[])}
    * until it either returns true or <code>timeout</code> ms have elapsed.
    *
    * @param caches  caches which must all have consistent views
    * @param timeout max number of ms to loop
    * @throws RuntimeException if <code>timeout</code> ms have elapse without
    *                          all caches having the same number of members.
    */
   public static void blockUntilViewsReceived(Cache[] caches, long timeout)
   {
      long failTime = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < failTime)
      {
         sleepThread(100);
         if (areCacheViewsComplete(caches))
         {
            return;
         }
      }

      throw new RuntimeException("timed out before caches had complete views" + views(caches));
   }

   private static String views(Cache... caches)
   {
      StringBuilder builder = new StringBuilder("[\n");
      for (Cache c:caches)
      {
         builder.append("   ").append(c.getLocalAddress()).append("->").append(c.getMembers()).append("\n");
      }
      builder.append("]");
      return builder.toString();
   }

   /**
    * Version of blockUntilViewsReceived that uses varargs
    */
   public static void blockUntilViewsReceived(long timeout, Cache... caches)
   {
      blockUntilViewsReceived(caches, timeout);
   }

   /**
    * Loops, continually calling {@link #areCacheViewsComplete(org.jboss.cache.CacheSPI[])}
    * until it either returns true or <code>timeout</code> ms have elapsed.
    *
    * @param caches  caches which must all have consistent views
    * @param timeout max number of ms to loop
    * @throws RuntimeException if <code>timeout</code> ms have elapse without
    *                          all caches having the same number of members.
    */
   public static void blockUntilViewsReceived(CacheSPI[] caches, long timeout)
   {
      long failTime = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < failTime)
      {
         sleepThread(100);
         if (areCacheViewsComplete(caches))
         {
            return;
         }
      }

      throw new RuntimeException("timed out before caches had complete views" + views(caches));
   }


   /**
    * An overloaded version of {@link #blockUntilViewsReceived(long,org.jboss.cache.Cache[])} that allows for 'shrinking' clusters.
    * I.e., the usual method barfs if there are more members than expected.  This one takes a param (barfIfTooManyMembers) which,
    * if false, will NOT barf but will wait until the cluster 'shrinks' to the desired size.  Useful if in tests, you kill
    * a member and want to wait until this fact is known across the cluster.
    *
    * @param timeout
    * @param barfIfTooManyMembers
    * @param caches
    */
   public static void blockUntilViewsReceived(long timeout, boolean barfIfTooManyMembers, Cache... caches)
   {
      long failTime = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < failTime)
      {
         sleepThread(100);
         if (areCacheViewsComplete(caches, barfIfTooManyMembers))
         {
            return;
         }
      }

      throw new RuntimeException("timed out before caches had complete views" + views(caches));
   }

   /**
    * Loops, continually calling {@link #areCacheViewsComplete(org.jboss.cache.CacheSPI[])}
    * until it either returns true or <code>timeout</code> ms have elapsed.
    *
    * @param groupSize number of caches expected in the group
    * @param timeout   max number of ms to loop
    * @throws RuntimeException if <code>timeout</code> ms have elapse without
    *                          all caches having the same number of members.
    */
   public static void blockUntilViewReceived(CacheSPI cache, int groupSize, long timeout)
   {
      blockUntilViewReceived(cache, groupSize, timeout, true);
   }

   public static void blockUntilViewReceived(CacheSPI cache, int groupSize, long timeout, boolean barfIfTooManyMembersInView)
   {
      long failTime = System.currentTimeMillis() + timeout;

      while (System.currentTimeMillis() < failTime)
      {
         sleepThread(100);
         if (isCacheViewComplete(cache, groupSize, barfIfTooManyMembersInView))
         {
            return;
         }
      }

      throw new RuntimeException("timed out before caches had complete views" + views(cache));
   }

   /**
    * Checks each cache to see if the number of elements in the array
    * returned by {@link CacheSPI#getMembers()} matches the size of
    * the <code>caches</code> parameter.
    *
    * @param caches caches that should form a View
    * @return <code>true</code> if all caches have
    *         <code>caches.length</code> members; false otherwise
    * @throws IllegalStateException if any of the caches have MORE view
    *                               members than caches.length
    */
   public static boolean areCacheViewsComplete(Cache[] caches)
   {
      return areCacheViewsComplete(caches, true);
   }

   public static boolean areCacheViewsComplete(Cache[] caches, boolean barfIfTooManyMembers)
   {
      int memberCount = caches.length;

      for (int i = 0; i < memberCount; i++)
      {
         if (!isCacheViewComplete(caches[i], memberCount, barfIfTooManyMembers))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Checks each cache to see if the number of elements in the array
    * returned by {@link org.jboss.cache.RPCManager#getMembers()} matches the size of
    * the <code>caches</code> parameter.
    *
    * @param caches caches that should form a View
    * @return <code>true</code> if all caches have
    *         <code>caches.length</code> members; false otherwise
    * @throws IllegalStateException if any of the caches have MORE view
    *                               members than caches.length
    */
   public static boolean areCacheViewsComplete(CacheSPI[] caches)
   {
      if (caches == null) throw new NullPointerException("Cache impl array is null");
      Cache[] c = new Cache[caches.length];
      for (int i = 0; i < caches.length; i++) c[i] = caches[i];
      return areCacheViewsComplete(c);
   }

   /**
    * @param cache
    * @param memberCount
    */
   public static boolean isCacheViewComplete(CacheSPI cache, int memberCount)
   {
      List members = cache.getRPCManager().getMembers();
      if (members == null || memberCount > members.size())
      {
         return false;
      }
      else if (memberCount < members.size())
      {
         // This is an exceptional condition
         StringBuilder sb = new StringBuilder("Cache at address ");
         sb.append(cache.getRPCManager().getLocalAddress());
         sb.append(" had ");
         sb.append(members.size());
         sb.append(" members; expecting ");
         sb.append(memberCount);
         sb.append(". Members were (");
         for (int j = 0; j < members.size(); j++)
         {
            if (j > 0)
            {
               sb.append(", ");
            }
            sb.append(members.get(j));
         }
         sb.append(')');

         throw new IllegalStateException(sb.toString());
      }

      return true;
   }

   public static boolean isCacheViewComplete(Cache c, int memberCount)
   {
      return isCacheViewComplete(c, memberCount, true);
   }

   public static boolean isCacheViewComplete(Cache c, int memberCount, boolean barfIfTooManyMembers)
   {
      CacheSPI cache = (CacheSPI) c;
      List members = cache.getMembers();
      if (members == null || memberCount > members.size())
      {
         return false;
      }
      else if (memberCount < members.size())
      {
         if (barfIfTooManyMembers)
         {
            // This is an exceptional condition
            StringBuilder sb = new StringBuilder("Cache at address ");
            sb.append(cache.getLocalAddress());
            sb.append(" had ");
            sb.append(members.size());
            sb.append(" members; expecting ");
            sb.append(memberCount);
            sb.append(". Members were (");
            for (int j = 0; j < members.size(); j++)
            {
               if (j > 0)
               {
                  sb.append(", ");
               }
               sb.append(members.get(j));
            }
            sb.append(')');

            throw new IllegalStateException(sb.toString());
         }
         else return false;
      }

      return true;
   }


   /**
    * Puts the current thread to sleep for the desired number of ms, suppressing
    * any exceptions.
    *
    * @param sleeptime number of ms to sleep
    */
   public static void sleepThread(long sleeptime)
   {
      try
      {
         Thread.sleep(sleeptime);
      }
      catch (InterruptedException ie)
      {
      }
   }

   public static void sleepRandom(int maxTime)
   {
      sleepThread(random.nextInt(maxTime));
   }

   public static void recursiveFileRemove(String directoryName)
   {
      File file = new File(directoryName);
      recursiveFileRemove(file);
   }

   public static void recursiveFileRemove(File file)
   {
      if (file.exists())
      {
         System.out.println("Deleting file " + file);
         recursivedelete(file);
      }
   }

   private static void recursivedelete(File f)
   {
      if (f.isDirectory())
      {
         File[] files = f.listFiles();
         for (File file : files)
         {
            recursivedelete(file);
         }
      }
      //System.out.println("File " + f.toURI() + " deleted = " + f.delete());
      f.delete();
   }

   /**
    * Kills a cache - stops it, clears any data in any cache loaders, and rolls back any associated txs
    */
   public static void killCaches(Cache... caches)
   {
      for (Cache c : caches)
      {
         try
         {
            if (c!= null) utf.removeCache(c);
            if (c != null) // && ( (c.getCacheStatus() == CacheStatus.STARTED) || c.getCacheStatus() == CacheStatus.FAILED) )
            {
               CacheSPI spi = (CacheSPI) c;

               Channel channel = null;
               if (spi.getRPCManager() != null)
               {
                  channel = spi.getRPCManager().getChannel();
               }
               if (spi.getTransactionManager() != null)
               {
                  try
                  {
                     spi.getTransactionManager().rollback();
                  }
                  catch (Throwable t)
                  {
                     // don't care
                  }
               }

               CacheLoaderManager clm = spi.getCacheLoaderManager();
               CacheLoader cl = clm == null ? null : clm.getCacheLoader();
               if (cl != null)
               {
                  try
                  {
                     cl.remove(Fqn.ROOT);
                  }
                  catch (Throwable t)
                  {
                     // don't care
                  }
               }

               try
               {
                  spi.stop();
               } catch (Throwable t) {
                  System.err.println(Thread.currentThread().getName() + " !!!!!!!!!!!!!!!!!!!!! WARNING - Cache instance refused to stop.");
                  t.printStackTrace();
               }
               try {
                  spi.destroy();
               } catch (Throwable t) {
                  System.err.println(Thread.currentThread().getName() + " !!!!!!!!!!!!!!!!!!!!! WARNING - Cache instance refused to destroy.");
                  t.printStackTrace();
               }
               if (channel != null)
               {
                  if (channel.isOpen()) {
                     System.err.println(Thread.currentThread().getName() + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Channel still opened.");
                     Thread.dumpStack();
                     channel.close();
                  }
                  if (channel.isOpen()) {
                     System.err.println(Thread.currentThread().getName() + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Channel close failed.");
                     System.exit(-1);
                  }
                  if (channel.isConnected()) {
                     System.err.println(Thread.currentThread().getName() + "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! Channel still connected.");
                     Thread.dumpStack();
                     System.exit(-1);
                  }
               }
            }
         }
         catch (Throwable t)
         {
            t.printStackTrace();
            System.exit(-1);
         }
      }
   }

   /**
    * Clears any associated transactions with the current thread in the caches' transaction managers.
    */
   public static void killTransactions(Cache... caches)
   {
      for (Cache c : caches)
      {
         if (c != null && c.getCacheStatus() == CacheStatus.STARTED)
         {
            CacheSPI ci = (CacheSPI) c;
            if (ci.getTransactionManager() != null)
            {
               try
               {
                  ci.getTransactionManager().rollback();
               }
               catch (Exception e)
               {
                  // don't care
               }
            }
         }
      }
   }

   /**
    * Clears transaction with the current thread in the given transaction manager.
    * @param txManager a TransactionManager to be cleared
    */
   public static void killTransaction(TransactionManager txManager) {
      if (txManager != null) {
         try {
            txManager.rollback();
         } catch (Exception e) {
               // don't care
         }
      }
   }

   /**
    * For testing only - introspects a cache and extracts the ComponentRegistry
    *
    * @param cache cache to introspect
    * @return component registry
    */
   public static ComponentRegistry extractComponentRegistry(Cache cache)
   {
      return (ComponentRegistry) extractField(cache, "componentRegistry");
   }

   public static LockManager extractLockManager(Cache cache)
   {
      return extractComponentRegistry(cache).getComponent(LockManager.class);
   }

   /**
    * For testing only - introspects a cache and extracts the ComponentRegistry
    *
    * @param ci interceptor chain to introspect
    * @return component registry
    */
   public static ComponentRegistry extractComponentRegistry(InterceptorChain ci)
   {
      return (ComponentRegistry) extractField(ci, "componentRegistry");
   }


   /**
    * Replaces the existing interceptor chain in the cache wih one represented by the interceptor passed in.  This
    * utility updates dependencies on all components that rely on the interceptor chain as well.
    *
    * @param cache       cache that needs to be altered
    * @param interceptor the first interceptor in the new chain.
    */
   public static void replaceInterceptorChain(CacheSPI<?, ?> cache, CommandInterceptor interceptor)
   {
      ComponentRegistry cr = extractComponentRegistry(cache);
      // make sure all interceptors here are wired.
      CommandInterceptor i = interceptor;
      do
      {
         cr.wireDependencies(i);
      }
      while ((i = i.getNext()) != null);

      InterceptorChain inch = cr.getComponent(InterceptorChain.class);
      inch.setFirstInChain(interceptor);
   }

   /**
    * Retrieves the remote delegate for a given cache.  It is on this remote delegate that the JGroups RPCDispatcher
    * invokes remote methods.
    *
    * @param cache cache instance for which a remote delegate is to be retrieved
    * @return remote delegate, or null if the cacge is not configured for replication.
    */
   public static CacheInvocationDelegate getInvocationDelegate(CacheSPI cache)
   {
      ComponentRegistry cr = extractComponentRegistry(cache);
      return cr.getComponent(CacheInvocationDelegate.class);
   }

   /**
    * Blocks until the cache has reached a specified state.
    *
    * @param cache       cache to watch
    * @param cacheStatus status to wait for
    * @param timeout     timeout to wait for
    */
   public static void blockUntilCacheStatusAchieved(Cache cache, CacheStatus cacheStatus, long timeout)
   {
      CacheSPI spi = (CacheSPI) cache;
      long killTime = System.currentTimeMillis() + timeout;
      while (System.currentTimeMillis() < killTime)
      {
         if (spi.getCacheStatus() == cacheStatus) return;
         sleepThread(50);
      }
      throw new RuntimeException("Timed out waiting for condition");
   }

   public static void replicateCommand(CacheSPI cache, VisitableCommand command) throws Throwable
   {
      ComponentRegistry cr = extractComponentRegistry(cache);
      InterceptorChain ic = cr.getComponent(InterceptorChain.class);
      ic.invoke(command);
   }

   public static void blockUntilViewsReceived(int timeout, List caches)
   {
      blockUntilViewsReceived((Cache[]) caches.toArray(new Cache[]{}), timeout);
   }


   public static CommandsFactory extractCommandsFactory(CacheSPI<Object, Object> cache)
   {
      return (CommandsFactory) extractField(cache, "commandsFactory");
   }

   public static String getJGroupsAttribute(Cache cache, String protocol, String attribute)
   {
      String s = ((JChannel) ((CacheSPI) cache).getRPCManager().getChannel()).getProperties();
      String[] protocols = s.split(":");
      String attribs = null;
      for (String p : protocols)
      {
         boolean hasAttribs = p.contains("(");
         String name = hasAttribs ? p.substring(0, p.indexOf('(')) : p;
         attribs = hasAttribs ? p.substring(p.indexOf('(') + 1, p.length() - 1) : null;

         if (name.equalsIgnoreCase(protocol)) break;
      }

      if (attribs != null)
      {
         String[] attrArray = attribs.split(";");
         for (String a : attrArray)
         {
            String[] kvPairs = a.split("=");
            if (kvPairs[0].equalsIgnoreCase(attribute)) return kvPairs[1];
         }
      }
      return null;
   }

   public static void dumpCacheContents(List caches)
   {
      System.out.println("**** START: Cache Contents ****");
      int count = 1;
      for (Object o : caches)
      {
         CacheSPI c = (CacheSPI) o;
         if (c == null)
         {
            System.out.println("  ** Cache " + count + " is null!");
         }
         else
         {
            System.out.println("  ** Cache " + count + " is " + c.getLocalAddress());
            System.out.println("    " + CachePrinter.printCacheDetails(c));
         }
         count++;
      }
      System.out.println("**** END: Cache Contents ****");
   }

   public static void dumpCacheContents(Cache... caches)
   {
      dumpCacheContents(Arrays.asList(caches));
   }
   
   public static int getThreadId() {
      return threadID.get();
   }

   public static CacheLoader getCacheLoader(Cache<?, ?> c)
   {
      CacheLoaderManager clm = extractComponentRegistry(c).getComponent(CacheLoaderManager.class);
      return clm == null ? null : clm.getCacheLoader();
   }
}
