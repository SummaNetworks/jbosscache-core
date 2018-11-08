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

package org.jboss.cache.integration.websession.util;

/**
 * Metadata about a webapp; mocks the object created from a jboss-web.xml.
 * 
 * @author Brian Stansberry
 */
public class WebAppMetadata
{
   public static final String DEFAULT_CACHE_CONFIG = "standard-session-cache";
   
   public enum Granularity { SESSION, ATTRIBUTE, FIELD };
   
   public final String warName;
   public final String cacheConfigName;
   public final boolean passivation;
   public final Granularity granularity;
   
   public WebAppMetadata(String warName)
   {
      this(warName, DEFAULT_CACHE_CONFIG, true, Granularity.SESSION);
   }
   
   public WebAppMetadata(String warName, String cacheConfigName, boolean passivation, Granularity granularity)
   {
      this.warName = warName;
      this.cacheConfigName = cacheConfigName;
      this.passivation = passivation;
      this.granularity = granularity;
   }
}
