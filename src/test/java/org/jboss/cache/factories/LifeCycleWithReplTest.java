package org.jboss.cache.factories;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = {"functional"}, testName = "factories.LifeCycleWithReplTest")
public class LifeCycleWithReplTest extends AbstractMultipleCachesTest
{

   private CacheSPI<Object, Object> first;
   private CacheSPI<Object, Object> second;

   protected void createCaches() throws Throwable
   {
      first = createCache(Configuration.CacheMode.REPL_SYNC);
      second = createCache(Configuration.CacheMode.REPL_SYNC);
      TestingUtil.blockUntilViewReceived(first, 2, 10000);
      registerCaches(first, second);
   }

   public void testRemoteInvalidStateInvocations() throws Exception
   {
      try
      {
         // now DIRECTLY change the status of c2.
         // emulate the race condition where the remote cache is stopping but hasn't disconnected from the channel.
         ComponentRegistry cr1 = TestingUtil.extractComponentRegistry(second);
         cr1.state = CacheStatus.STOPPING;

         // Thanks to JBCACHE-1179, this should only log a warning and not throw an exception
         first.put(Fqn.ROOT, "k", "v");
      }
      finally
      {
         ComponentRegistry cr1 = TestingUtil.extractComponentRegistry(second);
         cr1.state = CacheStatus.STARTED;
      }
   }

   public void testStopInstanceWhileOtherInstanceSends() throws Exception
   {
      final Fqn fqn = Fqn.fromString("/a");
      final List<Boolean> running = new LinkedList<Boolean>();
      final List<Exception> exceptions = new LinkedList<Exception>();
      running.add(true);

      first.put(fqn, "k", "v");

      assert "v".equals(first.get(fqn, "k"));
      assert "v".equals(second.get(fqn, "k"));

      // now kick start a thread on second that will constantly update the fqn

      Thread updater = new Thread()
      {
         public void run()
         {
            int i = 0;
            while (running.get(0))
            {
               try
               {
                  i++;
                  if (running.get(0)) second.put(fqn, "k", "v" + i);
               }
               catch (ReplicationException re)
               {
                  // this sometimes happens when JGroups suspects the remote node.  This is ok, as long as we don't get an ISE.
               }
               catch (SuspectException se)
               {
                  // this sometimes happens when JGroups suspects the remote node.  This is ok, as long as we don't get an ISE.
               }
               catch (Exception e)
               {
                  exceptions.add(e);
               }
               TestingUtil.sleepThread(20);

            }
         }
      };

      updater.start();

      running.add(false);
      running.remove(true);
      updater.join();

      for (Exception e : exceptions) throw e;
   }

   public void testRemoteInvalidStateInvocations2() throws Exception
   {
      try
      {
         // now DIRECTLY change the status of second.
         // emulate the race condition where the remote cache is stopping but hasn't disconnected from the channel.
         // there is a lousy race condition here - we need to make sure seconds's start() method doesn't set status to STARTED
         // after we attempt to change this.
         ComponentRegistry cr1 = TestingUtil.extractComponentRegistry(second);
         cr1.state = CacheStatus.STARTING;
         try
         {
            // This call should wait for up to StateRetrievalTimeout secs or until second has entered the STARTED state, and then barf.
            first.put(Fqn.ROOT, "k", "v");
            fail("Should barf!");
         }
         catch (Exception good)
         {
         }

         // now kick off another thread to sleep for a few secs and then set second to STARTED
         final int sleepTime = 500;
         new Thread()
         {
            public void run()
            {
               TestingUtil.sleepThread(sleepTime);
               ComponentRegistry cr1 = TestingUtil.extractComponentRegistry(second);
               cr1.state = CacheStatus.STARTED;
            }
         }.start();

         first.put(Fqn.ROOT, "k", "v");
      }
      finally
      {
         // reset second to running so the tearDown method can clean it up
         ComponentRegistry cr1 = TestingUtil.extractComponentRegistry(second);
         cr1.state = CacheStatus.STARTED;
      }
   }



   private CacheSPI<Object, Object> createCache(Configuration.CacheMode cacheMode)
   {
      Configuration c = new Configuration();
      c.setCacheMode(cacheMode);
      c.setTransactionManagerLookupClass("org.jboss.cache.transaction.DummyTransactionManagerLookup");
      CacheSPI<Object, Object> retval = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());
      retval.start();
      return retval;
   }
}
