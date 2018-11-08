/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.marshall;

import org.jboss.cache.Version;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.invocation.CacheInvocationDelegate;
import org.jboss.util.stream.MarshalledValueInputStream;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;

/**
 * Tests the enhanced treecache marshaller
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@Test(groups = {"functional"}, testName = "marshall.VersionAwareMarshallerTest")
public class VersionAwareMarshallerTest extends AbstractVersionAwareMarshallerTest
{
   @BeforeMethod
   public void setUp()
   {
      this.cr = new ComponentRegistry(new Configuration(), new CacheInvocationDelegate());
   }

   @AfterMethod
   public void tearDown()
   {
      cr = null;
   }

   public void testMarshallerSelection()
   {
      VersionAwareMarshaller marshaller = createVAMandRestartCache("2.2.0.GA");
      assertEquals(CacheMarshaller210.class, marshaller.defaultMarshaller.getClass());

      marshaller = createVAMandRestartCache("2.1.0.GA");
      assertEquals(CacheMarshaller210.class, marshaller.defaultMarshaller.getClass());

      marshaller = createVAMandRestartCache("2.0.0.GA");
      assertEquals(CacheMarshaller200.class, marshaller.defaultMarshaller.getClass());

      marshaller = createVAMandRestartCache("1.4.0.GA");
      assertEquals(CacheMarshaller300.class, marshaller.defaultMarshaller.getClass());

      marshaller = createVAMandRestartCache("1.5.0.GA");
      assertEquals(CacheMarshaller300.class, marshaller.defaultMarshaller.getClass());

      marshaller = createVAMandRestartCache("1.3.0.GA");
      assertEquals(CacheMarshaller300.class, marshaller.defaultMarshaller.getClass());

      marshaller = createVAMandRestartCache("1.3.0.SP2");
      assertEquals(CacheMarshaller300.class, marshaller.defaultMarshaller.getClass());

      marshaller = createVAMandRestartCache("1.3.1.GA");
      assertEquals(CacheMarshaller300.class, marshaller.defaultMarshaller.getClass());

      marshaller = createVAMandRestartCache("1.2.4.SP2");
      assertEquals(CacheMarshaller300.class, marshaller.defaultMarshaller.getClass());

      marshaller = createVAMandRestartCache("1.2.3");
      assertEquals(CacheMarshaller300.class, marshaller.defaultMarshaller.getClass());
   }

   public void testVersionHeaderDefaultCurrent() throws Exception
   {
      VersionAwareMarshaller marshaller = createVAMandRestartCache(Version.getVersionString(Version.getVersionShort()));

      byte[] bytes = marshaller.objectToByteBuffer("Hello");
      ObjectInputStream in = new MarshalledValueInputStream(new ByteArrayInputStream(bytes));
      assertEquals("Version header short should be '30'", 31, in.readShort());
   }

   public void testVersionHeader210() throws Exception
   {
      VersionAwareMarshaller marshaller = createVAMandRestartCache("2.1.0.GA");

      byte[] bytes = marshaller.objectToByteBuffer("Hello");
      ObjectInputStream in = new MarshalledValueInputStream(new ByteArrayInputStream(bytes));
      assertEquals("Version header short should be '21'", 21, in.readShort());
   }

   public void testVersionHeader200() throws Exception
   {
      VersionAwareMarshaller marshaller = createVAMandRestartCache("2.0.0.GA");

      byte[] bytes = marshaller.objectToByteBuffer("Hello");
      ObjectInputStream in = new MarshalledValueInputStream(new ByteArrayInputStream(bytes));
      assertEquals("Version header short should be '20'", 20, in.readShort());
   }
}
