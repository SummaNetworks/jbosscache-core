/*
 * Created on 17-Feb-2005
 *
 *
 *
 */
package org.jboss.cache.optimistic;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.interceptors.OptimisticCreateIfNotExistsInterceptor;
import org.jboss.cache.interceptors.OptimisticNodeInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * @author xenephon
 */
@SuppressWarnings("unchecked")
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.NodeInterceptorTransactionTest")
public class NodeInterceptorTransactionTest extends AbstractOptimisticTestCase
{
   CacheSPI cache;
   MockInterceptor dummy;

   @BeforeMethod
   public void setUp() throws Exception
   {
      cache = createCache();
      CommandInterceptor interceptor = new OptimisticCreateIfNotExistsInterceptor();
      CommandInterceptor nodeInterceptor = new OptimisticNodeInterceptor();
      dummy = new MockInterceptor();

      interceptor.setNext(nodeInterceptor);
      nodeInterceptor.setNext(dummy);

      TestingUtil.replaceInterceptorChain(cache, interceptor);
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache);
      cache = null;
   }

   public void testNoTransactionCRUDMethod() throws Exception
   {

      try
      {
         cache.put("/one/two", "key1", new Object());
         fail();
      }
      catch (Throwable t)
      {

         assertTrue(true);
      }
      assertEquals(null, dummy.getCalledCommand());
   }

   public void testNoTransactionGetMethod() throws Exception
   {
      boolean fail = false;
      try
      {
         assertEquals(null, cache.get("/one/two", "key1"));
      }
      catch (Exception e)
      {
         fail = true;
      }
      assertTrue(fail);
      assertEquals(null, dummy.getCalledCommand());
   }


}
