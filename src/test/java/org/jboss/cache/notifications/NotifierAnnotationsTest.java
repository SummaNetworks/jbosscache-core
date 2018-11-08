package org.jboss.cache.notifications;

import static org.easymock.EasyMock.createNiceMock;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.CacheStarted;
import org.jboss.cache.notifications.annotation.CacheStopped;
import org.jboss.cache.notifications.annotation.NodeMoved;
import org.jboss.cache.notifications.event.Event;
import org.jboss.cache.notifications.event.NodeMovedEvent;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Tests both correct and incorrect annotations for listeners
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional"}, sequential = true, testName = "notifications.NotifierAnnotationsTest")
public class NotifierAnnotationsTest
{
   private NotifierImpl n;

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      n = new NotifierImpl();
      n.injectDependencies(createNiceMock(CacheSPI.class), new Configuration());
      n.start();
   }

   @AfterMethod
   public void tearDown()
   {
      n.stop();
      n.destroy();
      n = null;
   }

   public void testControl()
   {
      Object l = new TestControlListener();
      n.addCacheListener(l);
      assertEquals(1, n.getCacheListeners().size());
   }

   public void testCacheListenerNoMethods()
   {
      Object l = new TestCacheListenerNoMethodsListener();
      n.addCacheListener(l);
      assertEquals("Hello", l.toString());
      assertTrue("No listeners should be registered.", n.getCacheListeners().isEmpty()); // since the valid listener has no methods to listen
   }

   public void testNonAnnotatedListener()
   {
      Object l = new TestNonAnnotatedListener();
      try
      {
         n.addCacheListener(l);
         fail("Should not accept an un-annotated cache listener");
      }
      catch (IncorrectCacheListenerException icle)
      {
         // expected
      }
      assertTrue("No listeners should be registered.", n.getCacheListeners().isEmpty());
   }

   public void testNonPublicListener()
   {
      Object l = new TestNonPublicListener();
      try
      {
         n.addCacheListener(l);
         fail("Should not accept a private callback class");
      }
      catch (IncorrectCacheListenerException icle)
      {
         // expected
      }
      assertTrue("No listeners should be registered.", n.getCacheListeners().isEmpty());
   }

   public void testNonPublicListenerMethod()
   {
      Object l = new TestNonPublicListenerMethodListener();
      n.addCacheListener(l);

      // should not fail, should just not register anything

      assertTrue("No listeners should be registered.", n.getCacheListeners().isEmpty());
   }

   public void testNonVoidReturnTypeMethod()
   {
      Object l = new TestNonVoidReturnTypeMethodListener();
      try
      {
         n.addCacheListener(l);
         fail("Should not accept a listener method with a return type");
      }
      catch (IncorrectCacheListenerException icle)
      {
         // expected
      }
      assertTrue("No listeners should be registered.", n.getCacheListeners().isEmpty());
   }

   public void testIncorrectMethodSignature1()
   {
      Object l = new TestIncorrectMethodSignature1Listener();
      try
      {
         n.addCacheListener(l);
         fail("Should not accept a cache listener with a bad method signature");
      }
      catch (IncorrectCacheListenerException icle)
      {
         // expected
      }
      assertTrue("No listeners should be registered.", n.getCacheListeners().isEmpty());
   }

   public void testIncorrectMethodSignature2()
   {
      Object l = new TestIncorrectMethodSignature2Listener();
      try
      {
         n.addCacheListener(l);
         fail("Should not accept a cache listener with a bad method signature");
      }
      catch (IncorrectCacheListenerException icle)
      {
         // expected
      }
      assertTrue("No listeners should be registered.", n.getCacheListeners().isEmpty());
   }

   public void testIncorrectMethodSignature3()
   {
      Object l = new TestIncorrectMethodSignature3Listener();
      try
      {
         n.addCacheListener(l);
         fail("Should not accept a cache listener with a bad method signature");
      }
      catch (IncorrectCacheListenerException icle)
      {
         // expected
      }
      assertTrue("No listeners should be registered.", n.getCacheListeners().isEmpty());
   }

   public void testUnassignableMethodSignature()
   {
      Object l = new TestUnassignableMethodSignatureListener();
      try
      {
         n.addCacheListener(l);
         fail("Should not accept a cache listener with a bad method signature");
      }
      catch (IncorrectCacheListenerException icle)
      {
         // expected
      }
      assertTrue("No listeners should be registered.", n.getCacheListeners().isEmpty());
   }

   public void testPartlyUnassignableMethodSignature()
   {
      Object l = new TestPartlyUnassignableMethodSignatureListener();
      try
      {
         n.addCacheListener(l);
         fail("Should not accept a cache listener with a bad method signature");
      }
      catch (IncorrectCacheListenerException icle)
      {
         // expected
      }
   }

   public void testMultipleMethods()
   {
      Object l = new TestMultipleMethodsListener();
      n.addCacheListener(l);
      List invocations = n.cacheStartedListeners;
      assertEquals(1, invocations.size());
      invocations = n.cacheStoppedListeners;
      assertEquals(1, invocations.size());
      assertEquals(1, n.getCacheListeners().size());
   }

   public void testMultipleAnnotationsOneMethod()
   {
      Object l = new TestMultipleAnnotationsOneMethodListener();
      n.addCacheListener(l);
      List invocations = n.cacheStartedListeners;
      assertEquals(1, invocations.size());
      invocations = n.cacheStoppedListeners;
      assertEquals(1, invocations.size());
      assertEquals(1, n.getCacheListeners().size());
   }

   public void testMultipleMethodsOneAnnotation()
   {
      Object l = new TestMultipleMethodsOneAnnotationListener();
      n.addCacheListener(l);
      List invocations = n.cacheStartedListeners;
      assertEquals(2, invocations.size());
      assertEquals(1, n.getCacheListeners().size());
   }

   @CacheListener
   public class TestControlListener
   {
      @CacheStarted
      @CacheStopped
      public void callback(Event e)
      {
      }
   }

   @CacheListener
   public class TestCacheListenerNoMethodsListener
   {
      public String toString()
      {
         return "Hello";
      }
   }

   public class TestNonAnnotatedListener
   {
      public String toString()
      {
         return "Hello";
      }
   }

   @CacheListener
   protected class TestNonPublicListener
   {
      @CacheStarted
      public void callback()
      {
      }
   }

   @CacheListener
   public class TestNonPublicListenerMethodListener
   {
      @CacheStarted
      protected void callback(Event e)
      {
      }
   }

   @CacheListener
   public class TestNonVoidReturnTypeMethodListener
   {
      @CacheStarted
      public String callback(Event e)
      {
         return "Hello";
      }
   }

   @CacheListener
   public class TestIncorrectMethodSignature1Listener
   {
      @CacheStarted
      public void callback()
      {
      }
   }

   @CacheListener
   public class TestIncorrectMethodSignature2Listener
   {
      @CacheStarted
      public void callback(Event e, String s)
      {
      }
   }

   @CacheListener
   public class TestIncorrectMethodSignature3Listener
   {
      @CacheStarted
      public void callback(Event e, String... s)
      {
      }
   }

   @CacheListener
   public class TestUnassignableMethodSignatureListener
   {
      @CacheStarted
      public void callback(NodeMovedEvent nme)
      {
      }
   }

   @CacheListener
   public class TestPartlyUnassignableMethodSignatureListener
   {
      @NodeMoved
      @CacheStarted
      public void callback(NodeMovedEvent nme) // sig valid for NodeMoved but not CacheStarted
      {
      }
   }

   @CacheListener
   public class TestMultipleMethodsListener
   {
      @CacheStarted
      public void callback1(Event e)
      {
      }

      @CacheStopped
      public void callback2(Event e)
      {
      }
   }

   @CacheListener
   public class TestMultipleAnnotationsOneMethodListener
   {
      @CacheStopped
      @CacheStarted
      public void callback(Event nme)
      {
      }
   }

   @CacheListener
   public class TestMultipleMethodsOneAnnotationListener
   {
      @CacheStarted
      public void callback1(Event e)
      {
      }

      @CacheStarted
      public void callback2(Event e)
      {
      }
   }
}
