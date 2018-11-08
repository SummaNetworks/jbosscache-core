package org.jboss.cache.factories;

import org.testng.annotations.Test;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = {"functional"}, testName = "factories.ComponentRegistryFunctionalTest")
public class ComponentRegistryFunctionalTest
{
//   private ComponentRegistry cr;
//   private Configuration configuration;
//
//   @BeforeMethod
//   public void setUp() throws Exception
//   {
//      CacheFactory cf = new DefaultCacheFactory<Object, Object>();
//
//      Cache cache = cf.createCache(false);
//      cr = TestingUtil.extractComponentRegistry(cache);
//      configuration = cache.getConfiguration();
//   }
//
//   public void testDefaultFactoryScanning()
//   {
//      cr.scanDefaultFactories();
//
//      assert cr.defaultFactories != null : "Should be populated";
//
//      // at very least, expecting a Marshaller factory and a DefaultCacheFactory.
//      assert cr.defaultFactories.containsKey(Marshaller.class);
//      assert cr.defaultFactories.get(Marshaller.class).equals(EmptyConstructorFactory.class);
//      assert cr.defaultFactories.containsKey(Notifier.class);
//      assert cr.defaultFactories.get(Notifier.class).equals(EmptyConstructorFactory.class);
//
//   }
//
//   public void testDependencyConsistency()
//   {
//      for (ComponentRegistry.Component component : cr.componentLookup.values())
//      {
//         // test that this component appears in all dependencies' dependencyFor collection.
//         for (ComponentRegistry.Component dep : component.dependencies)
//         {
//            assert cr.componentLookup.get(dep.name).dependencyFor.contains(component) : "Dependency " + dep.name + " does not have component " + component.name + " in its dependencyFor collection.";
//         }
//      }
//
//      for (ComponentRegistry.Component component : cr.componentLookup.values())
//      {
//         // test that this component appears in all dependencies' dependencyFor collection.
//         for (ComponentRegistry.Component dep : component.dependencyFor)
//         {
//            assert cr.componentLookup.get(dep.name).dependencies.contains(component) : "Dependency " + dep.name + " does not have component " + component.name + " in its dependencies collection.";
//         }
//      }
//   }
//
//
//   public void testNamedComponents()
//   {
//      cr.registerComponent("blah", new Object(), Object.class);
//      Object namedComponent1 = cr.getOrCreateComponent("blah", Object.class);
//      Object namedComponent2 = cr.getOrCreateComponent("blah", Object.class);
//
//      assert namedComponent1 == namedComponent2;
//   }
//
//   /**
//    * Case 1:
//    * nothing injected, nothing specified in Configuration.  Should use default factory.
//    */
//   public void testConstructionOrder1()
//   {
//      Class<Marshaller> componentToTest = Marshaller.class;
//      Marshaller m = cr.getOrCreateComponent(null, componentToTest);
//      assert m instanceof VersionAwareMarshaller;
//      VersionAwareMarshaller vam = (VersionAwareMarshaller) m;
//      vam.initReplicationVersions();
//      m = (Marshaller) TestingUtil.extractField(vam, "defaultMarshaller");
//      assert m instanceof CacheMarshaller210;
//   }
//
//   /**
//    * Case 2:
//    * instance injected, class specified in Configuration.  Should use injected.
//    */
//   public void testConstructionOrder2()
//   {
//      Class<Marshaller> componentToTest = Marshaller.class;
//      configuration.setMarshallerClass(CacheMarshaller200.class.getName());
//      Marshaller instance = new CacheMarshaller210();
//      configuration.setCacheMarshaller(instance);
//
//      // the setup() would have wired the default marshaller.  Need to update deps.
//      cr.unregisterComponent(Marshaller.class);
//      cr.updateDependencies();
//
//      Marshaller m = cr.getOrCreateComponent(null, componentToTest);
//      assert m == instance : "m is " + m + " but expected " + instance;
//   }
//
//   /**
//    * Case 3:
//    * instance injected, no class specified in Configuration.  Should use injected.
//    */
//   public void testConstructionOrder3()
//   {
//      Class<Marshaller> componentToTest = Marshaller.class;
//      Marshaller instance = new CacheMarshaller210();
//      configuration.setCacheMarshaller(instance);
//
//      // the setup() would have wired the default marshaller.  Need to update deps.
//      cr.unregisterComponent(Marshaller.class);
//      cr.updateDependencies();
//
//      Marshaller m = cr.getOrCreateComponent(null, componentToTest);
//      assert m == instance : "m is " + m + " but expected " + instance;
//   }
//
//   /**
//    * Case 4:
//    * nothing injected, class specified in Configuration.  Should use class specified.
//    */
//   public void testConstructionOrder4()
//   {
//      Class<Marshaller> componentToTest = Marshaller.class;
//      configuration.setMarshallerClass(CacheMarshaller200.class.getName());
//      Marshaller m = cr.getOrCreateComponent(null, componentToTest);
//      assert m instanceof VersionAwareMarshaller;
//      VersionAwareMarshaller vam = (VersionAwareMarshaller) m;
//      vam.initReplicationVersions();
//      m = (Marshaller) TestingUtil.extractField(vam, "defaultMarshaller");
//      assert m instanceof CacheMarshaller200;
//   }
//
//   public void testTransitiveDependencies()
//   {
//      Class<BuddyManager> componentToTest = BuddyManager.class;
//
//      // configure the cfg to use BR
//      BuddyReplicationConfig brc = new BuddyReplicationConfig();
//      brc.setEnabled(true);
//      BuddyReplicationConfig.BuddyLocatorConfig blc = new BuddyReplicationConfig.BuddyLocatorConfig();
//      blc.setBuddyLocatorClass(NextMemberBuddyLocator.class.getName());
//      brc.setBuddyLocatorConfig(blc);
//      configuration.setBuddyReplicationConfig(brc);
//
//      // needs to be a non-LOCAL configuration
//      configuration.setCacheMode(Configuration.CacheMode.REPL_ASYNC);
//      BuddyManager bm = cr.getOrCreateComponent(null, componentToTest);
//      assert bm != null;
//
//      StateTransferManager stm = (StateTransferManager) TestingUtil.extractField(bm, "stateTransferManager");
//      assert stm != null;
//
//      RPCManager rpcm = (RPCManager) TestingUtil.extractField(bm, "rpcManager");
//      assert rpcm != null;
//
//      RegionManager rm = (RegionManager) TestingUtil.extractField(bm, "regionManager");
//      assert rm != null;
//
//      Configuration cfg = (Configuration) TestingUtil.extractField(bm, "configuration");
//      assert cfg == configuration;
//   }
//
//   public void testInjectionOrder()
//   {
//      // injection should only occur after dependent components have been fully wired.
//
//      // E.g. Test1 depends on Test2 and Test2 depends on Test3.
//      //cr.reset();
//
//      // DefaultFactoryFor annotation won't work since tests are compiled into a separate classpath
//      cr.defaultFactories.put(Test1.class, EmptyConstructorFactory.class);
//      cr.defaultFactories.put(Test2.class, EmptyConstructorFactory.class);
//      cr.defaultFactories.put(Test3.class, EmptyConstructorFactory.class);
//
//      Test1 t1 = cr.getOrCreateComponent(null, Test1.class);
//
//      assert t1 != null;
//      assert t1.test2 != null;
//      assert t1.test2.test3 != null;
//      assert t1.someValue == t1.test2.test3.someValue;
//   }
//
//   public static class Test1
//   {
//      private Test2 test2;
//      private boolean someValue = false;
//
//      @Inject
//      public void setTest2(Test2 test2)
//      {
//         this.test2 = test2;
//         someValue = test2.test3.someValue;
//      }
//   }
//
//   public static class Test2
//   {
//      private Test3 test3;
//
//      @Inject
//      public void setTest3(Test3 test3)
//      {
//         this.test3 = test3;
//      }
//   }
//
//   public static class Test3
//   {
//      private boolean someValue = true;
//   }
}

