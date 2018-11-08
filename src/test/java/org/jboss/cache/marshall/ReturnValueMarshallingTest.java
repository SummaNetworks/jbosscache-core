package org.jboss.cache.marshall;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.buddyreplication.GravitateResult;
import org.jboss.cache.commands.CommandsFactory;
import org.jboss.cache.commands.CommandsFactoryImpl;
import org.jboss.cache.commands.DataCommand;
import org.jboss.cache.commands.read.GetKeyValueCommand;
import org.jboss.cache.commands.read.GravitateDataCommand;
import org.jboss.cache.commands.remote.ClusteredGetCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Tests the marshalling of retvals
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.0.0
 */
@Test(groups = {"functional", "jgroups"}, testName = "marshall.ReturnValueMarshallingTest")
public class ReturnValueMarshallingTest extends RegionBasedMarshallingTestBase
{
   protected boolean useMarshalledValues = false;
   private CacheSPI<Object, Object> cache1, cache2;
   private Fqn fqn = Fqn.fromString("/a");
   private ClassLoader classLoader;
   private Object key = "key", value;
   private String className = "org.jboss.cache.marshall.MyList";
   private Class listClass;
   private CommandsFactory commandsFactory = new CommandsFactoryImpl();

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      super.setUp();
      Configuration c1 = new Configuration();
      c1.setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      c1.setUseLazyDeserialization(useMarshalledValues);
      c1.setUseRegionBasedMarshalling(!useMarshalledValues);
      c1.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c1.setSyncReplTimeout(60000);// to aid with debugging
      cache1 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c1, false, getClass());
      cache1.start();

      Configuration c2 = new Configuration();
      c2.setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      c2.setUseLazyDeserialization(useMarshalledValues);
      c2.setUseRegionBasedMarshalling(!useMarshalledValues);
      c2.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      c2.setSyncReplTimeout(60000);// to aid with debugging
      cache2 = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache(c2, false, getClass());
      cache2.start();

      classLoader = getClassLoader();
      if (!useMarshalledValues)
      {
         Region r1 = cache1.getRegion(fqn, true);
         r1.setActive(true);
         r1.registerContextClassLoader(classLoader);
         Region r2 = cache2.getRegion(fqn, true);
         r2.setActive(true);
         r2.registerContextClassLoader(classLoader);
      }

      listClass = classLoader.loadClass(className);
      value = listClass.newInstance();

      cache1.put(fqn, key, value);
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown() throws Exception
   {
      TestingUtil.killCaches(cache1, cache2);
      cache1 = null;
      cache2 = null;
      super.tearDown();
   }

   @Override
   protected ClassLoader getClassLoader()
   {
      String[] includesClasses = {className};
      String[] excludesClasses = {};
      ClassLoader cl = Thread.currentThread().getContextClassLoader();
      return new SelectedClassnameClassLoader(includesClasses, excludesClasses, cl);
   }

   public void testClusteredGet() throws Exception
   {
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(classLoader);
      assertNotNull(cache1.get(fqn, key));
      assertNotSame(MyList.class, cache1.get(fqn, key).getClass());
      assertSame(listClass, cache1.get(fqn, key).getClass());

      assertNotNull(cache2.get(fqn, key));
      assertNotSame(MyList.class, cache2.get(fqn, key).getClass());
      assertSame(listClass, cache2.get(fqn, key).getClass());


      DataCommand command = new GetKeyValueCommand(fqn, key, false);
      ClusteredGetCommand clusteredGet = new ClusteredGetCommand(false, command);

      List responses = cache1.getRPCManager().callRemoteMethods(null, clusteredGet, true, 15000, false);
      List response1 = (List) responses.get(0);// response from the first (and only) node

      Boolean found = (Boolean) response1.get(0);
      assertTrue("Should have found remote data", found);

      Object data = response1.get(1);

      // now test that the data returned has been marshalled using the appropriate class loader.
      assertNotNull(data);
      if (useMarshalledValues) data = ((MarshalledValue) data).get();
      assertNotSame(MyList.class, data.getClass());
      assertSame(listClass, data.getClass());
   }

   public void testDataGravitation() throws Exception
   {
      if (useMarshalledValues) Thread.currentThread().setContextClassLoader(classLoader);
      assertNotNull(cache1.get(fqn, key));
      assertNotSame(MyList.class, cache1.get(fqn, key).getClass());
      assertSame(listClass, cache1.get(fqn, key).getClass());

      assertNotNull(cache2.get(fqn, key));
      assertNotSame(MyList.class, cache2.get(fqn, key).getClass());
      assertSame(listClass, cache2.get(fqn, key).getClass());

      GravitateDataCommand gravitateDataCommand = new GravitateDataCommand(fqn, false, cache1.getRPCManager().getLocalAddress());

      List responses = cache1.getRPCManager().callRemoteMethods(null, gravitateDataCommand, true, 15000, false);
      GravitateResult data = (GravitateResult) responses.get(0);// response from the first (and only) node

      assertTrue("Should have found remote data", data.isDataFound());
      assertNotNull(data.getNodeData());
      Object value = data.getNodeData().get(0).getAttributes().get(key);

      assertNotNull(value);
      if (useMarshalledValues) value = ((MarshalledValue) value).get();
      assertNotSame(MyList.class, value.getClass());
      assertSame(listClass, value.getClass());
   }

}
