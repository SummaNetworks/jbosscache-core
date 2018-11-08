/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.marshall;

import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.RegionRegistry;
import org.jboss.cache.commands.remote.ClusteredGetCommand;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.ComponentRegistry;

@Test(groups = {"functional"}, testName = "marshall.CacheMarshaller200Test")
public class CacheMarshaller200Test extends CacheMarshallerTestBase
{
   public CacheMarshaller200Test()
   {
      currentVersion = "2.0.0.GA";
      currentVersionShort = 20;
      expectedMarshallerClass = CacheMarshaller200.class;
   }

   public void testBadEquals() throws Exception
   {
      // object1 and Object2 should NOT be equal, even though their equals() methods are broken.
      Broken o1 = new Broken();
      Broken o2 = new Broken();

      o1.name = "o1";
      o2.name = "o2";

      assert o1 != o2;
      assert o1.equals(o2);

      List<Broken> l = new ArrayList<Broken>(2); // lists will allow "duplicate" entries.
      l.add(o1);
      l.add(o2);

      CacheMarshaller200 cm200 = new CacheMarshaller200();
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream out = new ObjectOutputStream(bout);
      cm200.objectToObjectStream(l, out);
      out.close();
      ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray()));
      List<Broken> l2 = (List<Broken>) cm200.objectFromObjectStream(in);

      assert l2.size() == 2;
      assert l2.get(0).name.equals("o1");
      assert l2.get(1).name.equals("o2");

      assert l2.get(0) != l2.get(1);
      assert l2.get(0).equals(l2.get(1));
   }

   public void testRegionalisedStream() throws Exception
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      CacheMarshaller200 cm200 = new CacheMarshaller200();
      tl.c.setUseRegionBasedMarshalling(true);
      RegionManagerImpl rmi = new RegionManagerImpl();
      rmi.injectDependencies(null, tl.c, null, null, null, new RegionRegistry());
      cm200.injectDependencies(rmi, tl.c, getClass().getClassLoader());
      cm200.init();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      cm200.objectToObjectStream("Hello World", oos, Fqn.fromString("/hello"));
      oos.close();

      ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));

      // test that the first thing on the stream is the fqn!
      byte magic = ois.readByte();
      short ref = ois.readShort();
      assert magic == CacheMarshaller200.MAGICNUMBER_FQN;

      // now the chunks of an Fqn
      Fqn f = cm200.unmarshallFqn(ois, new UnmarshalledReferences());

      assert f.equals(Fqn.fromString("/hello"));
   }

   /**
    * This test emulates the behaviour observed when an incoming method call is unmarshalled (using objectFromOutputStream),
    * region based marshalling is used, and it is expected that we would need to marshall the response using the same
    * region as well.  To deal with this, the region (as an Fqn) is read off the stream when unmarshalling and then
    * stored in a ThreadLocal so it can be accessed for when the response needs to be marshalled again.
    * <p/>
    * The problem here - registered as JBCACHE-1170 - is that this has a tendency to leak scope and affect more than the
    * required method call.
    */
   public void testLeakageOfFqn() throws Throwable
   {
      CacheMarshallerTestBaseTL tl = threadLocal.get();
      Configuration c = tl.c;
      // Use a thread pool so that we know threads will be reused.
      // You don't need any concurrency here to demonstrate the failure - a single thread is enough.
      ExecutorService e = Executors.newFixedThreadPool(1);
      // to capture throwables
      final List<Throwable> throwables = new CopyOnWriteArrayList<Throwable>();

      // each thread will perform 1 of 3 tasks:
      // 1.  unmarshall a stream, and marshall a response - typical case such as a clustered get
      // 2.  unmarshall a stream, and marshall a 'void' response.
      // 3.  marshall a (primitive response) - case such as a state transfer sending out boolean status

      // first create a stream to unmarshall.
//      RegionManager rm = new RegionManager();
      final CacheMarshaller200 cm200 = new CacheMarshaller200();
      c.setInactiveOnStartup(false);
      c.setUseRegionBasedMarshalling(true);
      c.setCacheMarshaller(cm200);
      ComponentRegistry cr = this.cr;
      cr.registerComponent(cm200, CacheMarshaller200.class);
      cr.rewire();
      cr.start();

      RegionManager rm = cr.getComponent(RegionManager.class);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(baos);
      final Fqn region = Fqn.fromString("/hello");
      Region r = rm.getRegion(region, true);
      r.registerContextClassLoader(this.getClass().getClassLoader());
      cm200.objectToObjectStream(new ClusteredGetCommand(false, null), oos, region);
      oos.close();

      final byte[] stream = baos.toByteArray();
      // so now the stream starts with the Fqn "/hello".

      // repeat 100 times
      for (int i = 0; i < 100; i++)
      {
         if (i % 3 == 0)
         {
            // task 1 above
            e.execute(new Runnable()
            {
               public void run()
               {
                  try
                  {
                     RegionalizedMethodCall rmc = cm200.regionalizedMethodCallFromObjectStream(new ObjectInputStream(new ByteArrayInputStream(stream)));
                     ByteArrayOutputStream out = new ByteArrayOutputStream();
                     ObjectOutputStream outStream = new ObjectOutputStream(out);
                     RegionalizedReturnValue rrv = new RegionalizedReturnValue("A result", rmc);
                     cm200.objectToObjectStream(rrv, outStream);
                     outStream.close();
                     out.close();
                     // test that the output stream has got "/hello" as it's region Fqn.
                     ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
                     assert in.readByte() == CacheMarshaller200.MAGICNUMBER_FQN : "The stream should start with an Fqn";
                     // discard the nest refId short
                     in.readShort();
                     Fqn f = cm200.unmarshallFqn(in, new UnmarshalledReferences());
                     assert region.equals(f) : "Should use the same region for the response as was used for the request!";

                  }
                  catch (Throwable t)
                  {
                     throwables.add(t);
                  }
               }
            });
         }
         else if (i % 3 == 1)
         {
            // task 2 above
            e.execute(new Runnable()
            {
               public void run()
               {
                  try
                  {
                     cm200.objectFromObjectStream(new ObjectInputStream(new ByteArrayInputStream(stream)));
                     // and now just send back a 'void' return type (In JGroups this is treated as a null)
                     cm200.objectToObjectStream(null, new ObjectOutputStream(new ByteArrayOutputStream()));
                  }
                  catch (Throwable t)
                  {
                     throwables.add(t);
                  }
               }
            });
         }
         else if (i % 3 == 2)
         {
            // task 3 above
            e.execute(new Runnable()
            {
               public void run()
               {
                  try
                  {

                     // and now don't bother with any umarshalling
                     // directly marshall a boolean.
                     ByteArrayOutputStream out = new ByteArrayOutputStream();
                     ObjectOutputStream outStream = new ObjectOutputStream(out);
                     cm200.objectToObjectStream(true, outStream);
                     outStream.close();
                     out.close();
                     // test that the output stream has got "/hello" as it's region Fqn.
                     ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(out.toByteArray()));
                     byte magic = in.readByte();

                     assert magic != CacheMarshaller200.MAGICNUMBER_FQN : "The stream should NOT start with an Fqn!";
                     assert magic == CacheMarshaller200.MAGICNUMBER_NULL : "Should start with a NULL.  Instead, was " + magic;
                     assert in.readByte() == CacheMarshaller200.MAGICNUMBER_BOOLEAN : "Should have a boolean magic number before the boolean value";
                     assert in.readBoolean() : "The boolean written to the stream should be true";

                  }
                  catch (Throwable t)
                  {
                     throwables.add(t);
                  }
               }
            });
         }
      }

      e.shutdown();
      e.awaitTermination(60, TimeUnit.SECONDS);

      for (Throwable t : throwables)
      {
         t.printStackTrace();
      }

      assert throwables.size() == 0 : "Should not have caught any exceptions!";
   }
}

class Broken implements Serializable
{
   String name;

   @Override
   public boolean equals(Object o)
   {
      return true;
   }

   @Override
   public int hashCode()
   {
      return 10;
   }
}
