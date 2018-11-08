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

package org.jboss.cache.integration;

import java.io.File;
import java.net.URL;

import org.jgroups.Channel;
import org.jgroups.ChannelException;
import org.jgroups.ChannelFactory;
import org.w3c.dom.Element;

/**
 * @author Brian Stansberry
 *
 */
public class MockChannelFactory implements ChannelFactory
{
   public static final MockChannelFactory INSTANCE = new MockChannelFactory();
   
   private MockChannelFactory() {}

   public Channel createChannel() throws ChannelException
   {
      throw new UnsupportedOperationException();
   }

   public Channel createChannel(Object props) throws ChannelException
   {
      throw new UnsupportedOperationException();
   }

   public Channel createChannel(String stack_name) throws Exception
   {
      throw new UnsupportedOperationException();
   }

   public Channel createMultiplexerChannel(String stack_name, String id) throws Exception
   {
      throw new UnsupportedOperationException();
   }

   public Channel createMultiplexerChannel(String stack_name, String id, boolean register_for_state_transfer,
         String substate_id) throws Exception
   {
      throw new UnsupportedOperationException();
   }

   public void setMultiplexerConfig(Object properties) throws Exception
   {
      throw new UnsupportedOperationException();
   }

   public void setMultiplexerConfig(File properties) throws Exception
   {
      throw new UnsupportedOperationException();
   }

   public void setMultiplexerConfig(Element properties) throws Exception
   {
      throw new UnsupportedOperationException();
   }

   public void setMultiplexerConfig(URL properties) throws Exception
   {
      throw new UnsupportedOperationException();
   }

   public void setMultiplexerConfig(String properties) throws Exception
   {
      throw new UnsupportedOperationException();
   }

}
