package org.jboss.cache.interceptors;

import org.jboss.cache.Cache;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.marshall.MethodCall;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This is to test that "old-style" interceptors from 2.0.x and 2.1.x will work with the new interceptor structure.
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.2.0
 */
@Test(groups = "functional", sequential = true, testName = "interceptors.LegacyInterceptorTest")
public class LegacyInterceptorTest
{
   Cache cache;
   static CountDownLatch interceptorResumeLatch, interceptorInvokedLatch;
   TestInterceptor testInterceptor;
   Executor testRunner;

   @BeforeMethod
   public void createLatches()
   {
      cache = new UnitTestCacheFactory<Object, Object>().createCache(getClass());
      testInterceptor = new TestInterceptor();

      ((CacheSPI) cache).addInterceptor(testInterceptor, TxInterceptor.class);
      testRunner = Executors.newSingleThreadExecutor();

      interceptorResumeLatch = new CountDownLatch(1);
      interceptorInvokedLatch = new CountDownLatch(1);
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testPut() throws Exception
   {
      testRunner.execute(new Runnable()
      {
         public void run()
         {
            cache.put("/a", "k", "v");
         }
      });

      interceptorInvokedLatch.await();

      // check that the context on the test interceptor is correct.
      MethodCall methodCall = testInterceptor.methodCall;
      interceptorResumeLatch.countDown();

      assert methodCall.getMethodId() == PutKeyValueCommand.METHOD_ID;
      assert methodCall.getArgs()[0] == null; // gtx
      assert methodCall.getArgs()[1].equals(Fqn.fromString("/a")); // fqn
      assert methodCall.getArgs()[2].equals("k"); // key
      assert methodCall.getArgs()[3].equals("v"); // value
      assert methodCall.getArgs()[4] == Boolean.FALSE; //last boolean value
   }

   public static class TestInterceptor extends Interceptor
   {
      MethodCall methodCall;

      @Override
      public Object invoke(InvocationContext ctx) throws Throwable
      {
         if (ctx.isOriginLocal())
         {
            // copy the context so tests can inspect it
            this.methodCall = ctx.getMethodCall();

            // signal to the test that this has been invoked.
            interceptorInvokedLatch.countDown();

            // wait for tests to finish
            interceptorResumeLatch.await();

            // wipe class-level context variable
            this.methodCall = null;
         }

         // the "old-style" of passing up the interceptor chain
         return super.invoke(ctx);
      }
   }

}

