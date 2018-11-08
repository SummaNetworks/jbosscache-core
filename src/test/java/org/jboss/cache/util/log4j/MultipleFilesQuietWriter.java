package org.jboss.cache.util.log4j;

import org.apache.log4j.helpers.QuietWriter;
import org.apache.log4j.spi.ErrorHandler;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Mircea.Markus@jboss.com
 */
public class MultipleFilesQuietWriter extends QuietWriter
{

   public static final String DEFAULT_LOG_FILE = "logs/default.log";
   private static Map<String, QuietWriter> testName2Qw = new HashMap<String, QuietWriter>();

   public MultipleFilesQuietWriter(ErrorHandler errorHandler)
   {
      super(new Writer()
      {
         public void write(char cbuf[], int off, int len) throws IOException
         {
            throw new UnsupportedOperationException("Not implemented");//sholdn't be called
         }

         public void flush() throws IOException
         {
            throw new UnsupportedOperationException("Not implemented");//sholdn't be called
         }

         public void close() throws IOException
         {
            throw new UnsupportedOperationException("Not implemented");//sholdn't be called
         }
      }, errorHandler);
      File file = new File("logs");
      if (!file.isDirectory()) file.mkdir();
   }

   public void write(String string)
   {
      try
      {
         QuietWriter qw = getQuietWriter();
         qw.write(string);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
   }

   private QuietWriter getQuietWriter()
         throws IOException
   {
      String logFile = getTestClass();
      if (logFile == null)
      {
         logFile = DEFAULT_LOG_FILE;
      } else
      {
         logFile = "logs/" + logFile;
      }
      QuietWriter qw = testName2Qw.get(logFile);
      if (qw == null)
      {
         File file = new File(logFile);
         if (file.exists())
         {
            file.delete();
            file.createNewFile();
         }
         FileOutputStream ostream = new FileOutputStream(file);
         Writer writer = new OutputStreamWriter(ostream);
         qw = new QuietWriter(writer, errorHandler);
         testName2Qw.put(logFile, qw);
      }
      return qw;
   }

   public void flush()
   {
      for (QuietWriter qw : testName2Qw.values())
      {
         qw.flush();
      }
   }

   public void close() throws IOException
   {
      for (QuietWriter qw : testName2Qw.values())
      {
         qw.close();
      }
   }


   public void write(int c) throws IOException
   {
      getQuietWriter().write(c);
   }

   public void write(char cbuf[], int off, int len) throws IOException
   {
      getQuietWriter().write(cbuf, off, len);
   }

   public void write(String str, int off, int len) throws IOException
   {
      getQuietWriter().write(str, off, len);
   }

   public void write(char cbuf[]) throws IOException
   {
      getQuietWriter().write(cbuf);
   }

   public Writer append(CharSequence csq) throws IOException
   {
      return getQuietWriter().append(csq);
   }

   public Writer append(CharSequence csq, int start, int end) throws IOException
   {
      return getQuietWriter().append(csq, start, end);
   }

   public Writer append(char c) throws IOException
   {
      return getQuietWriter().append(c);
   }

   public String getTestClass()
   {
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      if (stack.length == 0) return null;
      for (int i = stack.length - 1; i > 0; i--)
      {
         StackTraceElement e = stack[i];
         String className = e.getClassName();
         if (className.indexOf("org.jboss.cache") != -1) return getFileName(className); //+ "." + e.getMethodName();
      }
      return null;
   }

   private String getFileName(String className)
   {
      String noPackageStr = className.substring("org.jboss.cache.".length());
      return noPackageStr.replace('.', '_') + ".log";
   }
}
