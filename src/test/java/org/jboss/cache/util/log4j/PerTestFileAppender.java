package org.jboss.cache.util.log4j;

import org.apache.log4j.WriterAppender;

/**
 * @author Mircea.Markus@jboss.com
 */
public class PerTestFileAppender extends WriterAppender
{

   public PerTestFileAppender()
   {
   }

   public void activateOptions()
   {
      super.activateOptions();
      this.qw = new MultipleFilesQuietWriter(getErrorHandler());
   }
}
