/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.marshall;

import org.jboss.cache.Fqn;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.remote.ReplicateCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.invocation.CacheInvocationDelegate;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

@Test(groups = "functional", testName = "marshall.CacheMarshallerTestBase")
public abstract class CacheMarshallerTestBase extends AbstractVersionAwareMarshallerTest
{
   protected String currentVersion;
   protected int currentVersionShort;
   protected Class expectedMarshallerClass, latestMarshallerClass = CacheMarshaller300.class;
   
   protected ThreadLocal<CacheMarshallerTestBaseTL> threadLocal = new ThreadLocal<CacheMarshallerTestBaseTL>();
   
   protected class CacheMarshallerTestBaseTL {
      protected VersionAwareMarshaller marshaller;
      protected RegionManager regionManager;
      protected Configuration c;
   }

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      CacheMarshallerTestBaseTL tl = new CacheMarshallerTestBaseTL();
      threadLocal.set(tl);
      tl.c = new Configuration();
      tl.c.setUseRegionBasedMarshalling(false);
      tl.c.setInactiveOnStartup(false);
      tl.c.setReplVersionString(currentVersion);
      ComponentRegistry cr = new ComponentRegistry(tl.c, new CacheInvocationDelegate());
      this.cr = cr;
      tl.marshaller = createVAMandRestartCache(new RegionManagerImpl());
      tl.regionManager = cr.getComponent(RegionManager.class);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      threadLocal.set(null);      
   }

   protected void assertObjectArraysAreEqual(Object[] a1, Object[] a2)
   {
      assertEquals("Number of args should match", a1.length, a2.length);

      for (int i = 0; i < a1.length; i++)
      {
         if (a1[i] instanceof List && a2[i] instanceof List)
         {
            Object[] a1Elements = ((List) a1[i]).toArray();
            Object[] a2Elements = ((List) a2[i]).toArray();

            assertObjectArraysAreEqual(a1Elements, a2Elements);
         }
         else
         {
            assertEquals("Argument # " + i + " should be equal", a1[i], a2[i]);
         }
      }

   }


   public void testGetMarshaller()
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      assertEquals("Only one marshaller should be in the map by this stage", 1, marshaller.marshallers.size());

      assertEquals(expectedMarshallerClass, marshaller.getMarshaller(currentVersionShort).getClass());

      // defaultMarshaller is used for outgoing streams
      assert marshaller.defaultMarshaller.getClass().equals(expectedMarshallerClass);

      assertEquals(latestMarshallerClass, marshaller.getMarshaller(15).getClass());
      assertEquals(latestMarshallerClass, marshaller.getMarshaller(1).getClass());
      assertEquals(latestMarshallerClass, marshaller.getMarshaller(-1).getClass());
      assertEquals(latestMarshallerClass, marshaller.getMarshaller(0).getClass());
      assertEquals(CacheMarshaller200.class, marshaller.getMarshaller(20).getClass());
      assertEquals(CacheMarshaller210.class, marshaller.getMarshaller(21).getClass());

      assert marshaller.marshallers.size() == 3 : "Should have 3 marshallers now";
   }

   public void testStringBasedFqn() throws Exception
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      Fqn fqn = Fqn.fromElements("JSESSIONID", "1010.10.5:3000", "1234567890", "1");
      byte[] asBytes = marshaller.objectToByteBuffer(fqn);
      Object o2 = marshaller.objectFromByteBuffer(asBytes);
      assertEquals(fqn, o2);
   }

   public void testNonStringBasedFqn() throws Exception
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      Fqn fqn = Fqn.fromElements(3, false);
      byte[] asBytes = marshaller.objectToByteBuffer(fqn);
      Object o2 = marshaller.objectFromByteBuffer(asBytes);
      assertEquals(fqn, o2);
   }

   public void testMethodCall() throws Exception
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      Fqn fqn = Fqn.fromElements(3, false);
      ReplicableCommand cmd = new PutKeyValueCommand(null, fqn, "key", "value");
      byte[] asBytes = marshaller.objectToByteBuffer(cmd);
      Object o2 = marshaller.objectFromByteBuffer(asBytes);

      assertTrue("Unmarshalled object should be a method call", o2 instanceof ReplicableCommand);
      ReplicableCommand cmd2 = (ReplicableCommand) o2;

      assertEquals(cmd, cmd2);
   }

   public void testNestedMethodCall() throws Exception
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      Fqn fqn = Fqn.fromElements(3, false);
      ReplicableCommand cmd = new PutKeyValueCommand(null, fqn, "key", "value");
      ReplicableCommand replicateCmd = new ReplicateCommand(cmd);
      byte[] asBytes = marshaller.objectToByteBuffer(replicateCmd);
      Object o2 = marshaller.objectFromByteBuffer(asBytes);
      assertTrue("Unmarshalled object should be a method call", o2 instanceof ReplicableCommand);
      ReplicableCommand cmd2 = (ReplicableCommand) o2;

      assertEquals(replicateCmd, cmd2);
   }

   public void testLargeString() throws Exception
   {
      doLargeStringTest(32767, false);
   }

   public void testLargerString() throws Exception
   {
      doLargeStringTest(32768, false);
   }

   public void test64KString() throws Exception
   {
      doLargeStringTest((2 << 15) - 10, false);
      doLargeStringTest((2 << 15) + 10, false);
   }

   public void test128KString() throws Exception
   {
      doLargeStringTest((2 << 16) - 10, false);
      doLargeStringTest((2 << 16) + 10, false);
   }

   public void testLargeStringMultiByte() throws Exception
   {
      doLargeStringTest(32767, true);
   }

   public void testLargerStringMultiByte() throws Exception
   {
      doLargeStringTest(32768, true);
   }

   public void test64KStringMultiByte() throws Exception
   {
      doLargeStringTest((2 << 15) - 10, true);
      doLargeStringTest((2 << 15) + 10, true);
   }

   public void test128KStringMultiByte() throws Exception
   {
      doLargeStringTest((2 << 16) - 10, true);
      doLargeStringTest((2 << 16) + 10, true);
   }

   protected void doLargeStringTest(int stringSize, boolean multiByteChars) throws Exception
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      StringBuilder sb = new StringBuilder();

      int startingChar = multiByteChars ? 210 : 65;
      for (int i = 0; i < stringSize; i++) sb.append((char) (startingChar + (i % 26)));

      String largeString = sb.toString();

      assertEquals(stringSize, largeString.length());

      byte[] buf = marshaller.objectToByteBuffer(largeString);

      assertEquals(largeString, marshaller.objectFromByteBuffer(buf));
   }

   public void testReplicationQueue() throws Exception
   {
      doReplicationQueueTest();
   }

   public void testReplicationQueueWithRegionBasedMarshalling() throws Exception
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      tl.c.setUseRegionBasedMarshalling(true);
      marshaller.init();
      doReplicationQueueTest();
   }

   protected void doReplicationQueueTest() throws Exception
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      VersionAwareMarshaller marshaller = tl.marshaller;
      // replication queue takes a list of replicate() MethodCalls and wraps them in a single replicate call.
      List<ReplicableCommand> calls = new ArrayList<ReplicableCommand>();

      Fqn f = Fqn.fromElements("BlahBlah", 3, false);
      String k = "key", v = "value";

      ReplicableCommand cmd = new PutKeyValueCommand(null, f, k, v);
      ReplicableCommand replCmd = new ReplicateCommand(cmd);

      calls.add(replCmd);

      cmd = new PutKeyValueCommand(null, f, k, v);
      replCmd = new ReplicateCommand(cmd);

      calls.add(replCmd);

      ReplicableCommand replAllCmd = new ReplicateCommand(calls);

      byte[] buf = marshaller.objectToByteBuffer(replAllCmd);

      assertEquals(replAllCmd, marshaller.objectFromByteBuffer(buf));
   }

}
