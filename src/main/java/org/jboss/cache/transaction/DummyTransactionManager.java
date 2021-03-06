/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2000 - 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.cache.transaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.Properties;

/**
 * Simple transaction manager implementation that maintains transaction state
 * in memory only.
 *
 * @author bela
 * @version $Revision: 7168 $
 *          Date: May 15, 2003
 *          Time: 4:11:37 PM
 */
public class DummyTransactionManager extends DummyBaseTransactionManager
{
   protected static DummyTransactionManager instance = null;
   protected static DummyUserTransaction utx = null;

   protected static Log log = LogFactory.getLog(DummyTransactionManager.class);

   private static final long serialVersionUID = 4396695354693176535L;

   public static synchronized DummyTransactionManager getInstance()
   {      
      if (instance == null)
      {
         instance = new DummyTransactionManager();
         utx = new DummyUserTransaction(instance);
         try
         {
            Properties p = new Properties();
            p.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
            Context ctx = new InitialContext(p);
            ctx.bind("java:/TransactionManager", instance);
            ctx.bind("UserTransaction", utx);
         }
         catch (NamingException e)
         {
            log.error("binding of DummyTransactionManager failed", e);
         }
      }
      return instance;
   }

   public static DummyUserTransaction getUserTransaction()
   {
      getInstance();
      return utx;
   }

   public static synchronized void destroy()
   {
      if (instance == null)
         return;
      try
      {
         System.out.println("Destroy called on DummyTransactionManager. !!!!!!!!!!!!!!!!!!!!!!");
         Thread.dumpStack();
         
         Properties p = new Properties();
         p.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.cache.transaction.DummyContextFactory");
         Context ctx = new InitialContext(p);
         ctx.unbind("java:/TransactionManager");
         ctx.unbind("UserTransaction");
      }
      catch (NamingException e)
      {
         log.error("unbinding of DummyTransactionManager failed", e);
      }
      instance.setTransaction(null);
      instance = null;
   }

}
