/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.cache.config.parsing.custominterceptors;

import org.jboss.cache.interceptors.base.CommandInterceptor;

/**
 * Used for testing custom interceptors construction.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
public class AaaCustomInterceptor extends CommandInterceptor
{
   private String attrOne;
   private String attrTwo;
   private String attrThree;

   public String getAttrOne()
   {
      return attrOne;
   }

   public String getAttrTwo()
   {
      return attrTwo;
   }

   public String getAttrThree()
   {
      return attrThree;
   }

   public void setAttrOne(String attrOne)
   {
      this.attrOne = attrOne;
   }

   public void setAttrTwo(String attrTwo)
   {
      this.attrTwo = attrTwo;
   }

   public void setAttrThree(String attrThree)
   {
      this.attrThree = attrThree;
   }
}

