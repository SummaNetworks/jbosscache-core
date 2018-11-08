package org.jboss.cache.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Mircea.Markus@jboss.com
 */
public class UnitTestDatabaseManager
{
   private static final Properties realProps = new Properties();

   private static AtomicInteger userIndex = new AtomicInteger(0);

   static
   {
      //so that all individual databases are created here
      try
      {
         InputStream stream = new FileLookup().getAsInputStreamFromClassLoader("cache-jdbc.properties");
         realProps.load(stream);
      }
      catch (IOException e)
      {
         e.printStackTrace();
         throw new Error("Missing property file: cache-jdbc.properties", e);
      }
   }

   public static Properties getTestDbProperties()
   {
      synchronized (realProps)
      {
         return returnBasedOnDifferentInstance();
      }
   }

   public static void shutdownInMemoryDatabase(Properties props)
   {
      Connection conn = null;
      Statement st = null;
      try
      {
         String shutDownConnection = getShutdownUrl(props);
         String url = props.getProperty("cache.jdbc.url");
         assert url != null;
         conn = DriverManager.getConnection(shutDownConnection);
         st = conn.createStatement();
         st.execute("SHUTDOWN");
      }
      catch (Throwable e)
      {
         throw new IllegalStateException(e);
      }
      finally
      {
         try
         {
            conn.close();
            st.close();
         }
         catch (SQLException e)
         {
            e.printStackTrace();
         }
      }
   }

   public static void clearDatabaseFiles(Properties props)
   {
      //now delete the disk folder
      String dbName = getDatabaseName(props);
      String toDel = TestingUtil.TEST_FILES + File.separator + dbName;
      TestingUtil.recursiveFileRemove(toDel);
   }

   public static String getDatabaseName(Properties prop)
   {
      //jdbc:hsqldb:mem:jbosscache
      StringTokenizer tokenizer = new StringTokenizer(prop.getProperty("cache.jdbc.url"), ":");
      tokenizer.nextToken();
      tokenizer.nextToken();
      tokenizer.nextToken();
      return tokenizer.nextToken();
   }

   private static String getShutdownUrl(Properties props)
   {
      String url = props.getProperty("cache.jdbc.url");
      assert url != null;
      //jdbc:derby:jbossdb;create=true
      StringTokenizer tokenizer = new StringTokenizer(url, ";");
      String result = tokenizer.nextToken() + ";" + "shutdown=true";
      return result;
   }

   private static Properties returnBasedOnDifferentInstance()
   {
      //jdbc:hsqldb:mem:jbosscache
      Properties toReturn = (Properties) realProps.clone();
      String jdbcUrl = toReturn.getProperty("cache.jdbc.url");
      Pattern pattern = Pattern.compile("jbosscache");
      Matcher matcher = pattern.matcher(jdbcUrl);
      boolean found = matcher.find();
      assert found;
      String newJdbcUrl = matcher.replaceFirst(extractTestName() + userIndex.incrementAndGet());
      toReturn.put("cache.jdbc.url", newJdbcUrl);
      return toReturn;
   }

   private static String extractTestName()
   {
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      if (stack.length == 0) return null;
      for (int i = stack.length - 1; i > 0; i--)
      {
         StackTraceElement e = stack[i];
         String className = e.getClassName();
         if (className.indexOf("org.jboss.cache") != -1) return className.replace('.', '_') + "_" + e.getMethodName();
      }
      return null;
   }


   private static Properties returnBasedOnDifferentTables()
   {
      Properties toReturn = (Properties) realProps.clone();
      String tableName = toReturn.getProperty("cache.jdbc.table.name");
      int currentIndex = userIndex.incrementAndGet();
      toReturn.setProperty("cache.jdbc.table.name", tableName + currentIndex);
      String pk = toReturn.getProperty("cache.jdbc.table.primarykey");
      toReturn.setProperty("cache.jdbc.table.primarykey", pk + currentIndex);
      return toReturn;
   }
}
