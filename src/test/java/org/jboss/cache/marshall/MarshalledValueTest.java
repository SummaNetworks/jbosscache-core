package org.jboss.cache.marshall;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.write.PutDataMapCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.interceptors.MarshalledValueInterceptor;
import org.jboss.cache.interceptors.base.CommandInterceptor;
import org.jboss.cache.invocation.CacheInvocationDelegate;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.jboss.cache.notifications.annotation.CacheListener;
import org.jboss.cache.notifications.annotation.NodeModified;
import org.jboss.cache.notifications.event.NodeModifiedEvent;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Map;

/**
 * Tests implicit marshalled values
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", sequential = true, testName = "marshall.MarshalledValueTest")
public class MarshalledValueTest
{
   private CacheSPI<Object, Object> cache1, cache2;
   private MarshalledValueListenerInterceptor mvli;


   @BeforeMethod
   public void setUp() throws CloneNotSupportedException
   {
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC, false), false, getClass());
      if (cache1.getConfiguration().getBuddyReplicationConfig() != null)
         cache1.getConfiguration().setBuddyReplicationConfig(null);
      cache1.getConfiguration().setUseLazyDeserialization(true);

      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(cache1.getConfiguration().clone(), false, getClass());

      cache1.start();
      cache2.start();

      assert TestingUtil.findInterceptor(cache1, MarshalledValueInterceptor.class) != null : "Marshalled value interceptor not in chain!";
      assert TestingUtil.findInterceptor(cache2, MarshalledValueInterceptor.class) != null : "Marshalled value interceptor not in chain!";

      mvli = new MarshalledValueListenerInterceptor();
      cache1.addInterceptor(mvli, MarshalledValueInterceptor.class);
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
      Pojo.serializationCount = 0;
      Pojo.deserializationCount = 0;
   }

   private void assertOnlyOneRepresentationExists(MarshalledValue mv)
   {
      assert (mv.instance != null && mv.raw == null) || (mv.instance == null && mv.raw != null) : "Only instance or raw representations should exist in a MarshalledValue; never both";
   }

   private void assertSerialized(MarshalledValue mv)
   {
      assert mv.raw != null : "Should be serialized";
   }

   private void assertDeserialized(MarshalledValue mv)
   {
      assert mv.instance != null : "Should be deserialized";
   }

   private void assertSerializationCounts(int serializationCount, int deserializationCount)
   {
      assert Pojo.serializationCount == serializationCount : "Serialization count: expected " + serializationCount + " but was " + Pojo.serializationCount;
      assert Pojo.deserializationCount == deserializationCount : "Deserialization count: expected " + deserializationCount + " but was " + Pojo.deserializationCount;
   }

   public void testNonSerializable()
   {
      try
      {
         cache1.put("/a", "Hello", new Object());
         assert false : "Should have failed";
      }
      catch (CacheException expected)
      {

      }

      assert mvli.invocationCount == 0 : "Call should not have gone beyond the MarshalledValueInterceptor";

      try
      {
         cache1.put("/a", new Object(), "Hello");
         assert false : "Should have failed";
      }
      catch (CacheException expected)
      {

      }

      assert mvli.invocationCount == 0 : "Call should not have gone beyond the MarshalledValueInterceptor";
   }

   public void testNodeReleaseObjectValueReferences()
   {
      Pojo value = new Pojo();
      cache1.put("/a", "key", value);
      assertSerializationCounts(1, 0);
      NodeSPI<Object, Object> node = cache1.getNode("/a");
      Object o = node.getDirect("key");
      assert o instanceof MarshalledValue;
      MarshalledValue mv = (MarshalledValue) o;
      assertDeserialized(mv);
      assert node.get("key").equals(value);
      assertDeserialized(mv);
      assertSerializationCounts(1, 0);
      node.releaseObjectReferences(false);
      assertSerializationCounts(2, 0);
      assertOnlyOneRepresentationExists(mv);
      assertSerialized(mv);

      // now on cache 2
      node = cache2.getNode("/a");
      o = node.getDirect("key");
      assert o instanceof MarshalledValue;
      mv = (MarshalledValue) o;
      assertSerialized(mv); // this proves that unmarshalling on the recipient cache instance is lazy

      assert node.get("key").equals(value);
      assertDeserialized(mv);
      assertSerializationCounts(2, 1);
      node.releaseObjectReferences(false);
      assertSerializationCounts(2, 1);
      assertOnlyOneRepresentationExists(mv);
      assertSerialized(mv);
   }

   public void testNodeReleaseObjectKeyReferences() throws IOException, ClassNotFoundException
   {
      Pojo key = new Pojo();
      cache1.put("/a", key, "value");

      assertSerializationCounts(1, 0);

      NodeSPI<Object, Object> node = cache1.getNode("/a");
      Object o = node.getKeysDirect().iterator().next();
      assert o instanceof MarshalledValue;
      MarshalledValue mv = (MarshalledValue) o;
      assertDeserialized(mv);

      assert node.get(key).equals("value");
      assertDeserialized(mv);
      assertSerializationCounts(1, 0);
      node.releaseObjectReferences(false);
      assertSerializationCounts(2, 0);
      assertOnlyOneRepresentationExists(mv);
      assertSerialized(mv);

      // now on cache 2
      node = cache2.getNode("/a");
      o = node.getKeysDirect().iterator().next();
      assert o instanceof MarshalledValue;
      mv = (MarshalledValue) o;
      assertSerialized(mv);
      assert node.get(key).equals("value");
      assertSerializationCounts(2, 1);
      assertDeserialized(mv);
      node.releaseObjectReferences(false);

      assertOnlyOneRepresentationExists(mv);
      assertSerialized(mv);
      assertSerializationCounts(2, 1);
   }

   public void testEqualsAndHashCode() throws Exception
   {
      Pojo pojo = new Pojo();
      MarshalledValue mv = new MarshalledValue(pojo);
      assertDeserialized(mv);
      int oldHashCode = mv.hashCode();

      mv.serialize();
      assertSerialized(mv);
      assert oldHashCode == mv.hashCode();

      MarshalledValue mv2 = new MarshalledValue(pojo);
      assertSerialized(mv);
      assertDeserialized(mv2);

      assert mv2.hashCode() == oldHashCode;
      assert mv.equals(mv2);
   }

   public void assertUseOfMagicNumbers() throws Exception
   {
      Pojo pojo = new Pojo();
      MarshalledValue mv = new MarshalledValue(pojo);

      Configuration c = new Configuration();
      ComponentRegistry cr = new ComponentRegistry(c, new CacheInvocationDelegate());

      Marshaller marshaller = new CacheMarshaller210();
      cr.registerComponent(marshaller, Marshaller.class);

      // Wire the marshaller
      cr.start();

      // start the test
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bout);
      marshaller.objectToObjectStream(mv, out);
      out.close();
      bout.close();

      // check that the rest just contains a byte stream which a MarshalledValue will be able to deserialize.
      ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
      ObjectInputStream in = new ObjectInputStream(bin);

      assert in.read() == CacheMarshaller200.MAGICNUMBER_MARSHALLEDVALUE;
      MarshalledValue recreated = new MarshalledValue();
      recreated.readExternal(in);

      // there should be nothing more
      assert in.available() == 0;
      in.close();
      bin.close();

      assertSerialized(recreated);
      assert recreated.equals(mv);

      // since both objects being compared are serialized, the equals() above should just compare byte arrays.  
      assertSerialized(recreated);
      assertOnlyOneRepresentationExists(recreated);
   }

   public void testCacheLoaders() throws CloneNotSupportedException
   {
      tearDown();
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC), false, getClass());
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC), false, getClass());

      CacheLoaderConfig clc = new CacheLoaderConfig();
      CacheLoaderConfig.IndividualCacheLoaderConfig iclc = new CacheLoaderConfig.IndividualCacheLoaderConfig();
      iclc.setClassName(DummyInMemoryCacheLoader.class.getName());
      clc.addIndividualCacheLoaderConfig(iclc);
      cache1.getConfiguration().setCacheLoaderConfig(clc);
      cache2.getConfiguration().setCacheLoaderConfig(clc.clone());
      cache1.getConfiguration().setUseLazyDeserialization(true);
      cache2.getConfiguration().setUseLazyDeserialization(true);

      cache1.start();
      cache2.start();

      TestingUtil.blockUntilViewsReceived(60000, cache1, cache2);

      Pojo pojo = new Pojo();
      cache1.put("/a", "key", pojo);

      assertSerializationCounts(1, 0);

      cache2.get("/a", "key");

      assertSerializationCounts(1, 1);
   }

   public void testCallbackValues()
   {
      Listener l = new Listener();
      cache1.addCacheListener(l);
      Pojo pojo = new Pojo();
      cache1.put("/a", "key", pojo);

      assert l.modData.size() == 1;
      assert l.modData.get("key") instanceof Pojo;
      assertSerializationCounts(1, 0);
   }

   public void testRemoteCallbackValues()
   {
      Listener l = new Listener();
      cache2.addCacheListener(l);
      Pojo pojo = new Pojo();
      cache1.put("/a", "key", pojo);

      assert l.modData.size() == 1;
      pojo = (Pojo) l.modData.get("key");
      assert pojo != null;
      assertSerializationCounts(1, 1);
   }

   @CacheListener
   public static class Listener
   {
      Map modData;

      @NodeModified
      public void nodeModified(NodeModifiedEvent e)
      {
         if (!e.isPre()) modData = e.getData();
      }
   }

   class MarshalledValueListenerInterceptor extends CommandInterceptor
   {
      int invocationCount = 0;


      public Object visitPutDataMapCommand(InvocationContext ctx, PutDataMapCommand command) throws Throwable
      {
         invocationCount++;
         Object retval = invokeNextInterceptor(ctx, command);
         if (retval instanceof MarshalledValue) assertOnlyOneRepresentationExists((MarshalledValue) retval);
         return retval;
      }

      public Object visitPutKeyValueCommand(InvocationContext ctx, PutKeyValueCommand command) throws Throwable
      {
         invocationCount++;
         if (command.getKey() instanceof MarshalledValue)
            assertOnlyOneRepresentationExists((MarshalledValue) command.getKey());
         if (command.getValue() instanceof MarshalledValue)
            assertOnlyOneRepresentationExists((MarshalledValue) command.getValue());
         Object retval = invokeNextInterceptor(ctx, command);
         if (retval instanceof MarshalledValue) assertOnlyOneRepresentationExists((MarshalledValue) retval);
         return retval;
      }

   }

   public static class Pojo implements Externalizable
   {
      int i;
      boolean b;
      static int serializationCount, deserializationCount;

      public boolean equals(Object o)
      {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         Pojo pojo = (Pojo) o;

         if (b != pojo.b) return false;
         if (i != pojo.i) return false;

         return true;
      }

      public int hashCode()
      {
         int result;
         result = i;
         result = 31 * result + (b ? 1 : 0);
         return result;
      }

      public void writeExternal(ObjectOutput out) throws IOException
      {
         out.writeInt(i);
         out.writeBoolean(b);
         serializationCount++;
      }

      public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException
      {
         i = in.readInt();
         b = in.readBoolean();
         deserializationCount++;
      }
   }
}


