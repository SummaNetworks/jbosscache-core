package org.jboss.cache.marshall;

import org.jboss.cache.Fqn;
import org.jboss.cache.commands.CommandsFactory;
import org.jboss.cache.commands.CommandsFactoryImpl;
import org.jboss.cache.commands.WriteCommand;
import org.jboss.cache.commands.tx.PrepareCommand;
import org.jboss.cache.commands.write.PutDataMapCommand;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional"}, sequential = true, testName = "marshall.MethodIdPreservationTest")
public class MethodIdPreservationTest
{
   private Marshaller m;
   private ObjectOutputStream stream;
   private ByteArrayOutputStream byteStream;
   private WriteCommand command1;
   private List<WriteCommand> list;
   private PrepareCommand prepareComand;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {

      byteStream = new ByteArrayOutputStream();
      stream = new ObjectOutputStream(byteStream);
      command1 = new PutDataMapCommand(null, Fqn.ROOT, null);

      list = new ArrayList<WriteCommand>(2);
      list.add(command1);
      list.add(new PutDataMapCommand(null, Fqn.ROOT, null));
      prepareComand = new PrepareCommand(null, list, null, true);

      CacheMarshaller210 cm210 = new CacheMarshaller210();
      CommandsFactory factory = new CommandsFactoryImpl();
      cm210.injectCommandsFactory(factory);

      m = cm210;
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {

      byteStream = null;
      stream = null;
      command1 = null;
      list = null;
      prepareComand = null;
      m = null;
   }
   
   public void testSingleMethodCall() throws Exception
   {
      m.objectToObjectStream(command1, stream);
      stream.close();
      ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteStream.toByteArray()));
      Object result = m.objectFromObjectStream(in);
      assertEquals(command1.getClass(), result.getClass());
   }

   public void testListOfMethodCalls() throws Exception
   {
      m.objectToObjectStream(list, stream);
      stream.close();
      ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteStream.toByteArray()));
      Object result = m.objectFromObjectStream(in);
      assertEquals(list.getClass(), result.getClass());
      assertEquals(list.size(), ((List) result).size());
      assert ((List) result).get(0) instanceof PutDataMapCommand;
      assert ((List) result).get(1) instanceof PutDataMapCommand;
   }

   public void testMethodCallsInPrepare() throws Exception
   {
      m.objectToObjectStream(prepareComand, stream);
      stream.close();
      ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(byteStream.toByteArray()));
      Object result = m.objectFromObjectStream(in);

      assertEquals(prepareComand.getClass(), result.getClass());
      PrepareCommand prepareCallRes = (PrepareCommand) result;
      List listResult = prepareCallRes.getModifications();

      assertEquals(list.size(), listResult.size());

      assert listResult.get(0) instanceof PutDataMapCommand;
      assert listResult.get(1) instanceof PutDataMapCommand;
   }
}
