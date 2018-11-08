package org.jboss.cache.optimistic;

import org.jboss.cache.InvocationContext;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.VisitableCommand;
import org.jboss.cache.interceptors.base.CommandInterceptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles putXXX() methods: if the given node doesn't exist, it will be created
 * (depending on the create_if_not_exists argument)
 *
 * @author Bela Ban
 * @version $Id: CreateIfNotExistsInterceptor.java,v 1.7 2005/01/26 11:45:14
 *          belaban Exp $
 */
public class MockInterceptor extends CommandInterceptor
{
   ReplicableCommand calledCommand;
   private List<Class<? extends ReplicableCommand>> calledlist = new ArrayList<Class<? extends ReplicableCommand>>();
   private List<Integer> calledIdsList = new ArrayList<Integer>();

   @Override
   public synchronized Object handleDefault(InvocationContext ctx, VisitableCommand command) throws Throwable
   {
      calledlist.add(command.getClass());
      calledIdsList.add(command.getCommandId());
      calledCommand = command;
      return null;
   }

   /**
    * @return Returns the called.
    */
   public ReplicableCommand getCalledCommand()
   {
      return calledCommand;
   }

   public Class<? extends ReplicableCommand> getCalledCommandClass()
   {
      return calledCommand.getClass();
   }

   public List<Class<? extends ReplicableCommand>> getAllCalled()
   {
      return calledlist;
   }

   public List<Integer> getAllCalledIds()
   {
      return calledIdsList;
   }

   /**
    * @param called The called to set.
    */
   public void setCalled(ReplicableCommand called)
   {
      this.calledCommand = called;
   }
}