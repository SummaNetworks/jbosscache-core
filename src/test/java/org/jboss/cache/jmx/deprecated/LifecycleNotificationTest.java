/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.cache.jmx.deprecated;

import static org.testng.AssertJUnit.assertEquals;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;
import org.jboss.cache.jmx.CacheJmxWrapper;
import org.jboss.cache.jmx.CacheJmxWrapperMBean;

import javax.management.AttributeChangeNotification;
import javax.management.Notification;
import javax.management.NotificationListener;
import java.util.LinkedList;
import java.util.List;

/**
 * A LifecycleNotificationTest.
 *
 * @author <a href="brian.stansberry@jboss.com">Brian Stansberry</a>
 * @version $Revision: 7696 $
 */
@Test (groups = "functional", testName = "jmx.deprecated.LifecycleNotificationTest")
public class LifecycleNotificationTest extends CacheJmxWrapperTestBase
{
   public void testGetStateAndStateNotification() throws Exception
   {
      CacheJmxWrapper<String, String> wrapper = createWrapper(createConfiguration());
      StateNotificationListener listener = new StateNotificationListener();
      wrapper.addNotificationListener(listener, null, null);

      AssertJUnit.assertEquals("Correct state after instanitation",
            CacheJmxWrapperMBean.UNREGISTERED, wrapper.getState());

      registerWrapper(wrapper);
      assertEquals("Correct state after registration",
            CacheJmxWrapperMBean.REGISTERED, wrapper.getState());

      wrapper.create();
      assertEquals("Correct state after create",
            CacheJmxWrapperMBean.CREATED, wrapper.getState());

      wrapper.start();
      assertEquals("Correct state after start",
            CacheJmxWrapperMBean.STARTED, wrapper.getState());

      wrapper.stop();
      assertEquals("Correct state after stop",
            CacheJmxWrapperMBean.STOPPED, wrapper.getState());

      wrapper.destroy();
      assertEquals("Correct state after destroy",
            CacheJmxWrapperMBean.DESTROYED, wrapper.getState());

      unregisterWrapper();
      assertEquals("Correct state after unregistration",
            CacheJmxWrapperMBean.UNREGISTERED, wrapper.getState());

      assertEquals("Correct number of notifications received", 4, listener.notifications.size());
      assertEquals("Correct first notification", new Integer(CacheJmxWrapperMBean.STARTING), listener.notifications.get(0));
      assertEquals("Correct second notification", new Integer(CacheJmxWrapperMBean.STARTED), listener.notifications.get(1));
      assertEquals("Correct third notification", new Integer(CacheJmxWrapperMBean.STOPPING), listener.notifications.get(2));
      assertEquals("Correct fourth notification", new Integer(CacheJmxWrapperMBean.STOPPED), listener.notifications.get(3));
   }

   private static class StateNotificationListener
         implements NotificationListener
   {
      private List<Integer> notifications = new LinkedList<Integer>();

      public void handleNotification(Notification msg, Object handback)
      {
         if (msg instanceof AttributeChangeNotification)
         {
            AttributeChangeNotification change = (AttributeChangeNotification) msg;
            String attrName = change.getAttributeName();
            Object newValue = change.getNewValue();
            if ("State".equals(attrName) && newValue != null && newValue instanceof Integer)
            {
               notifications.add((Integer) newValue);
            }
         }
      }
   }

}
