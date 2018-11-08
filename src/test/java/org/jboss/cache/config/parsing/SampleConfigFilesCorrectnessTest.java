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
package org.jboss.cache.config.parsing;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.jboss.cache.Cache;
import org.jboss.cache.DefaultCacheFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;

/**
 * The purpose of this test is to make sure that the config files we ship are correct both according to xml schema and
 * according to the used JGroups/JBossCache version. For the latter, we start a cache instance and make sure that no
 * configuration warnings are being logged by both JGroups and JBossCache.
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "functional", testName = "config.parsing.SampleConfigFilesCorrectnessTest")
public class SampleConfigFilesCorrectnessTest
{
   public static final String CONFIG_ROOT = "src/main/resources/config-samples";
   public static final String XSD_FILE = "src/main/resources/jbosscache-config-3.1.xsd";

   private InMemoryAppender appender;
   private Level oldLevel;

   @BeforeMethod
   public void setUpTest()
   {
      Logger log4jLogger = Logger.getRootLogger();
      oldLevel = log4jLogger.getLevel();
      log4jLogger.setLevel(Level.WARN);
      appender = new InMemoryAppender();
      log4jLogger.addAppender(appender);
      File f = new File(".");
   }

   @AfterMethod
   public void tearDownTest()
   {
      Logger log4jLogger = Logger.getRootLogger();
      log4jLogger.setLevel(oldLevel);
      log4jLogger.removeAppender(appender);
      appender.close();
   }

   public void testSchemaValidity()
   {
      System.setProperty("jbosscache.config.schemaLocation", XSD_FILE);
      XmlConfigurationSchemaTest.ExceptionCountingErrorHandler errorHandler = new XmlConfigurationSchemaTest.ExceptionCountingErrorHandler();
      XmlConfigurationParser parser = new XmlConfigurationParser(errorHandler);
      String[] configFiles = getConfigFileNames();
      for (String aConfFile : configFiles)
      {
         parser.parseFile(CONFIG_ROOT + "/" + aConfFile);
      }
      assert errorHandler.noErrors();
   }

   public void testConfigWarnings()
   {
      DefaultCacheFactory ucf = new DefaultCacheFactory();
      for (String aConfFile : getConfigFileNames())
      {
         assert !appender.isFoundUnknownWarning();
         Cache cache = ucf.createCache(CONFIG_ROOT + "/" + aConfFile, true);
         cache.stop();
         cache.destroy();
         assert !appender.isFoundUnknownWarning();
      }
   }

   private String[] getConfigFileNames()
   {
      File file = new File(CONFIG_ROOT);
      return file.list(new FilenameFilter()
      {
         public boolean accept(File dir, String name)
         {
            return name.indexOf("xml") > 0;
         }
      });
   }


   private static class InMemoryAppender extends AppenderSkeleton
   {
      String[] TOLERABLE_WARNINGS =
            {
                  "DummyTransactionManager",
                  "not recommended for sync replication",
                  "could not bind to /", //this is a binding excpetion that might appear on some linuxes...
                  "failed to join /" //this might appear on linux + jdk6 
            };
      boolean foundUnknownWarning = false;

      /**
       * As this test runs in parallel with other tests tha also log information, we should disregard
       * other possible warnings from other threads and only consider warnings issues within this test class's test.
       *
       * @see #isExpectedThread()
       */
      private Thread loggerThread = Thread.currentThread();

      protected void append(LoggingEvent event)
      {
         if (event.getLevel().equals(Level.WARN) && isExpectedThread())
         {
            boolean skipPrinting = false;
            foundUnknownWarning = true;
            for (String knownWarn : TOLERABLE_WARNINGS)
            {
               if (event.getMessage().toString().indexOf(knownWarn) >= 0)
               {
                  skipPrinting = true;
                  foundUnknownWarning = false;
               }
            }

            if (!skipPrinting)
            {
               System.out.println("InMemoryAppender ****** " + event.getMessage().toString());
               System.out.println("TOLERABLE_WARNINGS: " + Arrays.toString(TOLERABLE_WARNINGS));
            }
         }
      }

      public boolean requiresLayout()
      {
         return false;
      }

      public void close()
      {
         //do nothing
      }

      public boolean isFoundUnknownWarning()
      {
         return foundUnknownWarning;
      }

      public boolean isExpectedThread()
      {
         return loggerThread.equals(Thread.currentThread());
      }
   }
}
