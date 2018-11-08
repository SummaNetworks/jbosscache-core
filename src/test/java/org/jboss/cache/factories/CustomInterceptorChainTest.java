package org.jboss.cache.factories;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 */
@Test(groups = {"functional"}, sequential = true, testName = "factories.CustomInterceptorChainTest")
public class CustomInterceptorChainTest extends InterceptorChainTestBase
{
   private CacheSPI<Object, Object> cache;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      Configuration c = new Configuration();
      cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c, getClass());
      cache.create();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      if (cache != null)
      {
         TestingUtil.killCaches(cache);
         cache = null;
      }
   }

   public void testChainImmutability()
   {
      try
      {
         cache.getInterceptorChain().add(new TestInterceptor());
         fail("unsupportedException should have been thrown as the chain obtained from the cache should be immutable");
      }
      catch (UnsupportedOperationException uoe)
      {
         // this is expected.
      }
   }

   public void testInjectionAtHead()
   {
      List<CommandInterceptor> interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 6 interceptors", 6, interceptors.size());
      assertInterceptorLinkage(interceptors);

      CommandInterceptor x = new TestInterceptor();
      cache.addInterceptor(x, 0);

      interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 7 interceptors", 7, interceptors.size());
      assertInterceptorLinkage(interceptors);

      assertEquals(x, interceptors.get(0));
   }

   public void testInjectionAtTail()
   {
      List<CommandInterceptor> interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 6 interceptors", 6, interceptors.size());
      assertInterceptorLinkage(interceptors);

      CommandInterceptor x = new TestInterceptor();
      cache.addInterceptor(x, 6);

      interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 7 interceptors", 7, interceptors.size());
      assertInterceptorLinkage(interceptors);

      assertEquals(x, interceptors.get(6));
   }

   public void testInjectionInMiddle()
   {
      List<CommandInterceptor> interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 6 interceptors", 6, interceptors.size());
      assertInterceptorLinkage(interceptors);

      CommandInterceptor x = new TestInterceptor();
      cache.addInterceptor(x, 3);

      interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 7 interceptors", 7, interceptors.size());
      assertInterceptorLinkage(interceptors);

      assertEquals(x, interceptors.get(3));
   }

   public void testInjectionBeyondTail()
   {
      List<CommandInterceptor> interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 6 interceptors", 6, interceptors.size());
      assertInterceptorLinkage(interceptors);

      CommandInterceptor x = new TestInterceptor();
      try
      {
         cache.addInterceptor(x, 9);
         fail("Should throw an exception");
      }
      catch (IllegalArgumentException e)
      {
         // expected
      }
   }

   public void testRemoveAtHead()
   {
      List<CommandInterceptor> interceptors = cache.getInterceptorChain();
      CommandInterceptor afterHead = interceptors.get(1);
      assertEquals("Expecting 6 interceptors", 6, interceptors.size());
      assertInterceptorLinkage(interceptors);

      cache.removeInterceptor(0);

      interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 5 interceptors", 5, interceptors.size());
      assertInterceptorLinkage(interceptors);

      assertEquals(afterHead, interceptors.get(0));
   }

   public void testRemoveAtTail()
   {
      List<CommandInterceptor> interceptors = cache.getInterceptorChain();
      CommandInterceptor beforeTail = interceptors.get(4);
      assertEquals("Expecting 6 interceptors", 6, interceptors.size());
      assertInterceptorLinkage(interceptors);

      cache.removeInterceptor(5);

      interceptors = cache.getInterceptorChain();

      assertEquals("Expecting 5 interceptors", 5, interceptors.size());
      assertInterceptorLinkage(interceptors);

      assertEquals(beforeTail, interceptors.get(4));
   }

   public void testRemoveAtMiddle()
   {
      List<CommandInterceptor> interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 6 interceptors", 6, interceptors.size());
      assertInterceptorLinkage(interceptors);

      cache.removeInterceptor(3);

      interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 5 interceptors", 5, interceptors.size());
      assertInterceptorLinkage(interceptors);
   }

   public void testRemoveBeyondTail()
   {
      List<CommandInterceptor> interceptors = cache.getInterceptorChain();
      assertEquals("Expecting 6 interceptors", 6, interceptors.size());
      assertInterceptorLinkage(interceptors);

      try
      {
         cache.removeInterceptor(9);
         fail("Should throw an exception");
      }
      catch (IllegalArgumentException e)
      {
         // expected
      }
   }


   public static class TestInterceptor extends CommandInterceptor
   {
   }
}
