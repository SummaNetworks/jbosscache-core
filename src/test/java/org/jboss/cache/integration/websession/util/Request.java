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
 * Mocks the JBoss AS clustered webapp request handling.
 * 
 * @author Brian Stansberry
 */
public class Request
{
   private final SessionManager manager;
   private final String sessionId;
   private final Servlet servlet;
   private Session session;
   
   public Request(SessionManager manager, String sessionId, Servlet servlet)
   {
      this.manager = manager;
      this.sessionId = sessionId;
      this.servlet = servlet;
   }
   
   public void execute()
   {
      // Gravitate the session outside of the batch
      getSession(false);
      
      manager.startBatch();
      try
      {
         servlet.handleRequest(this);
      }
      finally
      {
         try
         {
            if (session != null && session.isValid())
               session.store();
         }
         finally
         {
            manager.endBatch();
         }
      }
      
      // StandardHostValve calls getSession(false) on the way out, so...
      getSession(false);
   }
   
   /**
    * Meant for internal use in this class and by a Servlet; test driver
    * should get a session ref from a Servlet impl.
    */
   public Session getSession(boolean create)
   {
      if (session == null && sessionId != null)
      {
         session = manager.findSession(sessionId);         
      }
      
      if (session != null && !session.isValid())
      {
         session = null;
         getSession(create);
      }
      
      if (create && session == null)
         session = manager.createSession();
      
      if (session != null)
         session.access();
      
      return session;
   }

}
