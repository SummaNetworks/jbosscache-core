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
public class MockFailureInterceptor extends CommandInterceptor
{
   private List<Class<? extends ReplicableCommand>> allCalled = new ArrayList<Class<? extends ReplicableCommand>>();
   private List<Class<? extends ReplicableCommand>> failurelist = new ArrayList<Class<? extends ReplicableCommand>>();
   private List<Integer> allCalledIdsList = new ArrayList<Integer>();

   @Override
   public Object handleDefault(InvocationContext ctx, VisitableCommand command) throws Throwable
   {
      if (failurelist.contains(command.getClass())) throw new Exception("Failure in method " + command);
      allCalled.add(command.getClass());
      allCalledIdsList.add(command.getCommandId());

      return null;
   }

   /**
    * @return Returns the failurelist.
    */
   public List<Class<? extends ReplicableCommand>> getFailurelist()
   {
      return failurelist;
   }

   /**
    * @param failurelist The failurelist to set.
    */
   public void setFailurelist(List<Class<? extends ReplicableCommand>> failurelist)
   {
      this.failurelist = failurelist;
   }

   /**
    * @return Returns the called.
    */
   public List<Class<? extends ReplicableCommand>> getAllCalled()
   {
      return allCalled;
   }

   /**
    * @param called The called to set.
    */
   public void setAllCalled(List<Class<? extends ReplicableCommand>> called)
   {
      this.allCalled = called;
   }

   public List<Integer> getAllCalledIds()
   {
      return allCalledIdsList;
   }
}