/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.jmx.deprecated;

import org.testng.annotations.Test;


/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test (groups = "functional", testName = "jmx.deprecated.OptimisticNotificationTest") 
public class OptimisticNotificationTest extends NotificationTest
{
   public OptimisticNotificationTest()
   {
      optimistic = true;
   }
}
