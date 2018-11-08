package org.jboss.cache.marshall;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public abstract class RegionBasedMarshallingTestBase
{
   protected static final String ADDRESS_CLASSNAME = "org.jboss.cache.marshall.data.Address";
   protected static final String PERSON_CLASSNAME = "org.jboss.cache.marshall.data.Person";
   protected ThreadLocal<ClassLoader> originalClassLoaderTL = new ThreadLocal<ClassLoader>() {
      @Override
      protected ClassLoader initialValue() {
         return Thread.currentThread().getContextClassLoader();
      }
   };

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      originalClassLoaderTL.get();
   }
   
   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception           
   {
      resetContextClassLoader();     
   }
   
   protected ClassLoader getClassLoader() throws Exception
   {
      String[] includesClasses = {PERSON_CLASSNAME, ADDRESS_CLASSNAME};
      String[] excludesClasses = {};
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      return new SelectedClassnameClassLoader(includesClasses, excludesClasses, cl);
   }

   protected ClassLoader getFailingClassLoader() throws Exception
   {
      String[] includesClasses = {};
      String[] excludesClasses = {};
      String[] failingClasses = {PERSON_CLASSNAME, ADDRESS_CLASSNAME};
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      return new SelectedClassnameClassLoader(includesClasses, excludesClasses, failingClasses, cl);
   }

   protected void resetContextClassLoader()
   {
      Thread.currentThread().setContextClassLoader(originalClassLoaderTL.get());
   }
}
