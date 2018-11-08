package org.jboss.cache.invocation;

import org.jboss.cache.interceptors.CallInterceptor;
import org.jboss.cache.interceptors.InterceptorChain;
import org.jboss.cache.interceptors.InvalidationInterceptor;
import org.jboss.cache.interceptors.InvocationContextInterceptor;
import org.jboss.cache.interceptors.PessimisticLockInterceptor;
import org.jboss.cache.interceptors.TxInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.AfterMethod;

/**
 * Tests functionality defined by InterceptorChain.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = {"unit"}, testName = "invocation.InterceptorChainTest")
public class InterceptorChainTest
{
   private CommandInterceptor icInterceptor;
   private CommandInterceptor invalidationInterceptor;
   private CommandInterceptor txInterceptor;
   private CommandInterceptor pessimisticInterceptor;
   private CommandInterceptor callInterceptor;
   private InterceptorChain chain;

   @BeforeMethod
   public void setUp()
   {
      icInterceptor = create(InvocationContextInterceptor.class);
      invalidationInterceptor = create(InvalidationInterceptor.class);
      txInterceptor = create(TxInterceptor.class);
      pessimisticInterceptor = create(PessimisticLockInterceptor.class);
      callInterceptor = create(CallInterceptor.class);
      chain = new InterceptorChain(icInterceptor);
   }

   @AfterMethod
   public void tearDown()
   {
      icInterceptor = null;
      invalidationInterceptor = null;
      txInterceptor = null;
      pessimisticInterceptor = null;
      callInterceptor = null;
      chain = null;
   }
   
   public void testGetIntercpetorsAsList() throws Throwable
   {
      invalidationInterceptor.setNext(txInterceptor);
      txInterceptor.setNext(pessimisticInterceptor);
      pessimisticInterceptor.setNext(callInterceptor);

      InterceptorChain chain = new InterceptorChain(invalidationInterceptor);
      List<CommandInterceptor> expectedList = new ArrayList<CommandInterceptor>();
      expectedList.add(invalidationInterceptor);
      expectedList.add(txInterceptor);
      expectedList.add(pessimisticInterceptor);
      expectedList.add(callInterceptor);

      assert chain.asList().equals(expectedList);
   }

   public void testAddAtPosition() throws Throwable
   {
      chain.addInterceptor(invalidationInterceptor, 1);
      assert invalidationInterceptor.equals(icInterceptor.getNext());

      chain.addInterceptor(pessimisticInterceptor, 1);
      assert pessimisticInterceptor.equals(icInterceptor.getNext());
      assert invalidationInterceptor.equals(pessimisticInterceptor.getNext());
      assert invalidationInterceptor.getNext() == null;

      chain.addInterceptor(callInterceptor, 3);
      assert invalidationInterceptor.getNext().equals(callInterceptor);
   }

   public void testAddAtPositionIncremented()
   {
      chain.addInterceptor(txInterceptor, 1);
      chain.addInterceptor(invalidationInterceptor, 2);
      chain.addInterceptor(pessimisticInterceptor, 3);
      chain.addInterceptor(callInterceptor, 4);
      assert icInterceptor.getNext().equals(txInterceptor);
      assert txInterceptor.getNext().equals(invalidationInterceptor);
      assert invalidationInterceptor.getNext().equals(pessimisticInterceptor);
      assert pessimisticInterceptor.getNext().equals(callInterceptor);
   }

   public void testRemoveAtPostion() throws Throwable
   {
      chain.addInterceptor(txInterceptor, 1);
      chain.addInterceptor(invalidationInterceptor, 2);
      chain.addInterceptor(pessimisticInterceptor, 3);
      chain.addInterceptor(callInterceptor, 4);

      chain.removeInterceptor(4);
      assert chain.size() == 4;
      assert pessimisticInterceptor.getNext() == null;

      chain.removeInterceptor(0);
      assert chain.size() == 3;
      chain.getFirstInChain().equals(txInterceptor);

      chain.removeInterceptor(1);
      assert chain.size() == 2;
      assert txInterceptor.getNext().equals(pessimisticInterceptor);
   }

   public void testGetSize()
   {
      assert chain.size() == 1;
      chain.addInterceptor(txInterceptor, 1);
      assert chain.size() == 2;
      chain.addInterceptor(invalidationInterceptor, 2);
      assert chain.size() == 3;
      chain.addInterceptor(pessimisticInterceptor, 3);
      assert chain.size() == 4;
      chain.addInterceptor(callInterceptor, 4);
      assert chain.size() == 5;
   }

   public void testAppendInterceptor()
   {
      chain.appendIntereceptor(txInterceptor);
      assert chain.size() == 2;
      assert icInterceptor.getNext().equals(txInterceptor);

      chain.appendIntereceptor(invalidationInterceptor);
      assert chain.size() == 3;
      assert txInterceptor.getNext().equals(invalidationInterceptor);
   }

   public void testGetInterceptorsWhichExtend()
   {
      InvocationContextInterceptor ic2 = (InvocationContextInterceptor) create(InvocationContextInterceptor.class);
      chain.appendIntereceptor(ic2);
      List<CommandInterceptor> result = chain.getInterceptorsWhichExtend(InvocationContextInterceptor.class);
      assert result.contains(icInterceptor);
      assert result.contains(ic2);
      assert result.size() == 2;
      result = chain.getInterceptorsWhichExtend(CommandInterceptor.class);
      assert result.size() == chain.asList().size();
   }

   public void testRemoveInterceptorWithtType()
   {
      chain.addInterceptor(txInterceptor, 1);
      chain.addInterceptor(invalidationInterceptor, 2);
      chain.addInterceptor(pessimisticInterceptor, 3);
      chain.addInterceptor(callInterceptor, 4);

      chain.removeInterceptor(InvalidationInterceptor.class);
      assert chain.size() == 4;
      assert txInterceptor.getNext().equals(pessimisticInterceptor);

      chain.removeInterceptor(InvocationContextInterceptor.class);
      assert chain.size() == 3;
      assert chain.getFirstInChain().equals(txInterceptor);

      chain.removeInterceptor(CallInterceptor.class);
      assert chain.size() == 2;
      assert pessimisticInterceptor.getNext() == null;
   }

   public void testAddInterceptorWithType()
   {
      assert chain.addAfterInterceptor(invalidationInterceptor, icInterceptor.getClass());
      assert icInterceptor.getNext().equals(invalidationInterceptor);

      chain.addAfterInterceptor(txInterceptor, icInterceptor.getClass());
      assert icInterceptor.getNext().equals(txInterceptor);
      assert txInterceptor.getNext().equals(invalidationInterceptor);
   }

   public void testGetInterceptorsWithClassName()
   {
      chain.appendIntereceptor(invalidationInterceptor);
      chain.appendIntereceptor(callInterceptor);
      chain.appendIntereceptor(pessimisticInterceptor);
      chain.appendIntereceptor(create(CallInterceptor.class));
      assert chain.getInterceptorsWithClassName(InvocationContextInterceptor.class.getName()).size() == 1;
      assert chain.getInterceptorsWithClassName(InvalidationInterceptor.class.getName()).size() == 1;
      assert chain.getInterceptorsWithClassName(PessimisticLockInterceptor.class.getName()).size() == 1;
      assert chain.getInterceptorsWithClassName(CallInterceptor.class.getName()).size() == 2;
      assert chain.getInterceptorsWithClassName(CommandInterceptor.class.getName()).size() == 0;
   }

   private CommandInterceptor create(Class<? extends CommandInterceptor> toInstantiate)
   {
      try
      {
         return toInstantiate.newInstance();
      }
      catch (Throwable th)
      {
         throw new RuntimeException(th);
      }
   }
}
