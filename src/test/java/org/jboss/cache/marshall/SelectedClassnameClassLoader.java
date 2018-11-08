package org.jboss.cache.marshall;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * A ClassLoader that loads classes whose classname begins with one of a
 * given set of strings, without attempting first to delegate to its
 * parent loader.
 * <p>
 * This class is intended to allow emulation of 2 different types of common J2EE
 * classloading situations.
 * <ul>
 * <li>Servlet-style child-first classloading, where this class is the
 * child loader.</li>
 * <li>Parent-first classloading where the parent does not have access to
 * certain classes</li>
 * </ul>
 * </p>
 * <p>
 * This class can also be configured to raise a ClassNotFoundException if
 * asked to load certain classes, thus allowing classes on the classpath
 * to be hidden from a test environment.
 * </p>
 *
 * @author Brian Stansberry
 */
public class SelectedClassnameClassLoader extends ClassLoader
{

   private String[] includedClasses = null;
   private String[] excludedClasses = null;
   private String[] notFoundClasses = null;
   private Log log = LogFactory.getLog(SelectedClassnameClassLoader.class);

   private Map<String, Class> classes = new java.util.HashMap<String, Class>();

   /**
    * Creates a new classloader that loads the given classes.
    *
    * @param includedClasses array of class or package names that should be
    *                        directly loaded by this loader.  Classes
    *                        whose name starts with any of the strings
    *                        in this array will be loaded by this class,
    *                        unless their name appears in
    *                        <code>excludedClasses</code>.
    *                        Can be <code>null</code>
    * @param excludedClasses array of class or package names that should NOT
    *                        be directly loaded by this loader.  Loading of
    *                        classes whose name starts with any of the
    *                        strings in this array will be delegated to
    *                        <code>parent</code>, even if the classes
    *                        package or classname appears in
    *                        <code>includedClasses</code>.  Typically this
    *                        parameter is used to exclude loading one or
    *                        more classes in a package whose other classes
    *                        are loaded by this object.
    * @param parent          ClassLoader to which loading of classes should
    *                        be delegated if necessary
    */
   public SelectedClassnameClassLoader(String[] includedClasses,
                                       String[] excludedClasses,
                                       ClassLoader parent)
   {
      super(parent);
      this.includedClasses = includedClasses;
      this.excludedClasses = excludedClasses;
   }

   /**
    * Creates a new classloader that loads the given classes.
    *
    * @param includedClasses array of class or package names that should be
    *                        directly loaded by this loader.  Classes
    *                        whose name starts with any of the strings
    *                        in this array will be loaded by this class,
    *                        unless their name appears in
    *                        <code>excludedClasses</code>.
    *                        Can be <code>null</code>
    * @param excludedClasses array of class or package names that should NOT
    *                        be directly loaded by this loader.  Loading of
    *                        classes whose name starts with any of the
    *                        strings in this array will be delegated to
    *                        <code>parent</code>, even if the classes
    *                        package or classname appears in
    *                        <code>includedClasses</code>.  Typically this
    *                        parameter is used to exclude loading one or
    *                        more classes in a package whose other classes
    *                        are loaded by this object.
    * @param notFoundClasses array of class or package names for which this
    *                        should raise a ClassNotFoundException
    * @param parent          ClassLoader to which loading of classes should
    *                        be delegated if necessary
    */
   public SelectedClassnameClassLoader(String[] includedClasses,
                                       String[] excludedClasses,
                                       String[] notFoundClasses,
                                       ClassLoader parent)
   {
      super(parent);
      this.includedClasses = includedClasses;
      this.excludedClasses = excludedClasses;
      this.notFoundClasses = notFoundClasses;
   }

   protected synchronized Class<?> loadClass(String name, boolean resolve)
         throws ClassNotFoundException
   {
      log.info("In SelectedClassnameClassLoader.loadClass(" + name + "," + resolve + ")");
      if (isIncluded(name) && (isExcluded(name) == false))
      {
         Class c = findClass(name);

         if (resolve)
         {
            resolveClass(c);
         }
         return c;
      }
      else
      {
         return super.loadClass(name, resolve);
      }
   }

   protected Class<?> findClass(String name) throws ClassNotFoundException
   {
      log.info("In SelectedClassnameClassLoader.findClass()");
      Class result = classes.get(name);
      if (result != null)
      {
         return result;
      }

      if (isIncluded(name) && (isExcluded(name) == false))
      {
         try
         {
            InputStream is = getResourceAsStream(name.replace('.', '/').concat(".class"));
            byte[] bytes = new byte[1024];
            ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
            int read;
            while ((read = is.read(bytes)) > -1)
            {
               baos.write(bytes, 0, read);
            }
            bytes = baos.toByteArray();
            result = this.defineClass(name, bytes, 0, bytes.length);
         }
         catch (FileNotFoundException e)
         {
            throw new ClassNotFoundException("cannot find " + name, e);
         }
         catch (IOException e)
         {
            throw new ClassNotFoundException("cannot read " + name, e);
         }
      }
      else if (isNotFound(name))
      {
         throw new ClassNotFoundException(name + " is discarded");
      }
      else
      {
         result = super.findClass(name);
      }

      classes.put(name, result);

      return result;
   }

   private boolean isIncluded(String className)
   {

      if (includedClasses != null)
      {
         for (int i = 0; i < includedClasses.length; i++)
         {
            if (className.startsWith(includedClasses[i]))
            {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isExcluded(String className)
   {

      if (excludedClasses != null)
      {
         for (int i = 0; i < excludedClasses.length; i++)
         {
            if (className.startsWith(excludedClasses[i]))
            {
               return true;
            }
         }
      }

      return false;
   }

   private boolean isNotFound(String className)
   {

      if (notFoundClasses != null)
      {
         for (int i = 0; i < notFoundClasses.length; i++)
         {
            if (className.startsWith(notFoundClasses[i]))
            {
               return true;
            }
         }
      }

      return false;
   }
}
