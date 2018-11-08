package org.jboss.cache.interceptors;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.marshall.MarshalledValueHelper;
import org.jboss.cache.util.CachePrinter;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", sequential = true, testName = "interceptors.MarshalledValueInterceptorTest")
public class MarshalledValueInterceptorTest
{
   CacheSPI c;

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(c);
      c = null;
   }

   public void testDefaultInterceptorStack()
   {
      c = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(getClass());

      assert TestingUtil.findInterceptor(c, MarshalledValueInterceptor.class) == null;

      TestingUtil.killCaches(c);

      c = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(false, getClass());
      c.getConfiguration().setUseLazyDeserialization(true);
      c.start();

      assert TestingUtil.findInterceptor(c, MarshalledValueInterceptor.class) != null;
   }

   public void testEnabledInterceptorStack()
   {
      Configuration cfg = new Configuration();
      cfg.setUseLazyDeserialization(true);
      c = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(cfg, getClass());

      assert TestingUtil.findInterceptor(c, MarshalledValueInterceptor.class) != null;
   }

   public void testDisabledInterceptorStack()
   {
      Configuration cfg = new Configuration();
      cfg.setUseLazyDeserialization(false);
      c = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(cfg, getClass());

      assert TestingUtil.findInterceptor(c, MarshalledValueInterceptor.class) == null;
   }

   public void testExcludedTypes()
   {
      // Strings
      assert MarshalledValueHelper.isTypeExcluded(String.class);
      assert MarshalledValueHelper.isTypeExcluded(String[].class);
      assert MarshalledValueHelper.isTypeExcluded(String[][].class);
      assert MarshalledValueHelper.isTypeExcluded(String[][][].class);

      // primitives
      assert MarshalledValueHelper.isTypeExcluded(void.class);
      assert MarshalledValueHelper.isTypeExcluded(boolean.class);
      assert MarshalledValueHelper.isTypeExcluded(char.class);
      assert MarshalledValueHelper.isTypeExcluded(byte.class);
      assert MarshalledValueHelper.isTypeExcluded(short.class);
      assert MarshalledValueHelper.isTypeExcluded(int.class);
      assert MarshalledValueHelper.isTypeExcluded(long.class);
      assert MarshalledValueHelper.isTypeExcluded(float.class);
      assert MarshalledValueHelper.isTypeExcluded(double.class);

      assert MarshalledValueHelper.isTypeExcluded(boolean[].class);
      assert MarshalledValueHelper.isTypeExcluded(char[].class);
      assert MarshalledValueHelper.isTypeExcluded(byte[].class);
      assert MarshalledValueHelper.isTypeExcluded(short[].class);
      assert MarshalledValueHelper.isTypeExcluded(int[].class);
      assert MarshalledValueHelper.isTypeExcluded(long[].class);
      assert MarshalledValueHelper.isTypeExcluded(float[].class);
      assert MarshalledValueHelper.isTypeExcluded(double[].class);

      assert MarshalledValueHelper.isTypeExcluded(boolean[][].class);
      assert MarshalledValueHelper.isTypeExcluded(char[][].class);
      assert MarshalledValueHelper.isTypeExcluded(byte[][].class);
      assert MarshalledValueHelper.isTypeExcluded(short[][].class);
      assert MarshalledValueHelper.isTypeExcluded(int[][].class);
      assert MarshalledValueHelper.isTypeExcluded(long[][].class);
      assert MarshalledValueHelper.isTypeExcluded(float[][].class);
      assert MarshalledValueHelper.isTypeExcluded(double[][].class);

      assert MarshalledValueHelper.isTypeExcluded(Void.class);
      assert MarshalledValueHelper.isTypeExcluded(Boolean.class);
      assert MarshalledValueHelper.isTypeExcluded(Character.class);
      assert MarshalledValueHelper.isTypeExcluded(Byte.class);
      assert MarshalledValueHelper.isTypeExcluded(Short.class);
      assert MarshalledValueHelper.isTypeExcluded(Integer.class);
      assert MarshalledValueHelper.isTypeExcluded(Long.class);
      assert MarshalledValueHelper.isTypeExcluded(Float.class);
      assert MarshalledValueHelper.isTypeExcluded(Double.class);

      assert MarshalledValueHelper.isTypeExcluded(Boolean[].class);
      assert MarshalledValueHelper.isTypeExcluded(Character[].class);
      assert MarshalledValueHelper.isTypeExcluded(Byte[].class);
      assert MarshalledValueHelper.isTypeExcluded(Short[].class);
      assert MarshalledValueHelper.isTypeExcluded(Integer[].class);
      assert MarshalledValueHelper.isTypeExcluded(Long[].class);
      assert MarshalledValueHelper.isTypeExcluded(Float[].class);
      assert MarshalledValueHelper.isTypeExcluded(Double[].class);

      assert MarshalledValueHelper.isTypeExcluded(Boolean[][].class);
      assert MarshalledValueHelper.isTypeExcluded(Character[][].class);
      assert MarshalledValueHelper.isTypeExcluded(Byte[][].class);
      assert MarshalledValueHelper.isTypeExcluded(Short[][].class);
      assert MarshalledValueHelper.isTypeExcluded(Integer[][].class);
      assert MarshalledValueHelper.isTypeExcluded(Long[][].class);
      assert MarshalledValueHelper.isTypeExcluded(Float[][].class);
      assert MarshalledValueHelper.isTypeExcluded(Double[][].class);
   }

   public void testNonExcludedTypes()
   {
      assert !MarshalledValueHelper.isTypeExcluded(Object.class);
      assert !MarshalledValueHelper.isTypeExcluded(List.class);
      assert !MarshalledValueHelper.isTypeExcluded(Collection.class);
      assert !MarshalledValueHelper.isTypeExcluded(Map.class);
      assert !MarshalledValueHelper.isTypeExcluded(Date.class);
      assert !MarshalledValueHelper.isTypeExcluded(Thread.class);
      assert !MarshalledValueHelper.isTypeExcluded(Collection.class);
      assert !MarshalledValueHelper.isTypeExcluded(new Object()
      {
         String blah;
      }.getClass());
   }
}
