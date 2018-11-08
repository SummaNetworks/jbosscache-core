/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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

package org.jboss.cache.integration.hibernate;

import org.jboss.cache.Fqn;

/**
 * Utilities related to the Hibernate integration tests.
 *
 * @author Brian Stansberry
 */
public class HibernateIntegrationTestUtil
{
   public static final Fqn TS_BASE_FQN = Fqn.fromString("/TS");
   public static final Fqn REGION_PREFIX_FQN = Fqn.fromString("/test");
   public static final Fqn TS_FQN = Fqn.fromRelativeFqn(TS_BASE_FQN, Fqn.fromRelativeFqn(REGION_PREFIX_FQN, Fqn.fromString("/org/hibernate/cache/UpdateTimestampsCache")));
   public static final String ITEM = "item";

   /**
    * Prevent instantiation.
    */
   private HibernateIntegrationTestUtil()
   {
      // no-op
   }
}
