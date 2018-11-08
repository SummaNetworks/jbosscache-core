package org.jboss.cache.factories;

import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", testName = "factories.ComponentRegistryUnitTest")
public class ComponentRegistryUnitTest
{
   public void testConstruction()
   {
      Cache c = new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_SYNC), getClass());
      c.put("/a", "b", "c");
      TestingUtil.killCaches(c);
   }

//   ComponentRegistry cr;
//   Configuration cfg;
//
//   @BeforeMethod
//   public void setUp()
//   {
//      cr = new ComponentRegistry(new Configuration());
//      cfg = cr.getConfiguration();
//   }
//
//   public void testChangingComponentState()
//   {
//      cr.registerComponent("c2", new C2(), C2.class);
//      cr.registerComponent("c1", new C1(), C1.class);
//      cr.registerComponent("c3", new C3(), C3.class);
//
//      ComponentRegistry.Component c1 = cr.componentLookup.get("c1");
//      ComponentRegistry.Component c2 = cr.componentLookup.get("c2");
//      ComponentRegistry.Component c3 = cr.componentLookup.get("c3");
//
//      // add some dependencies
//      ComponentRegistry.Component d1 = cr.new Component("c1", null);
//      ComponentRegistry.Component d2 = cr.new Component("c2", null);
//      ComponentRegistry.Component d3 = cr.new Component("c3", null);
//
//      // c1 depends on c2
//      // c3 depends on c1
//
//      // test dependency and dependencyFor
//
//      assert c2.dependencies.isEmpty();
//      assert c1.dependencies.contains(d2);
//      assert c1.dependencies.size() == 1;
//      assert c3.dependencies.contains(d1);
//      assert c3.dependencies.size() == 1;
//
//      assert c2.dependencyFor.contains(d1);
//      assert c2.dependencyFor.size() == 1;
//      assert c1.dependencyFor.contains(d3);
//      assert c1.dependencyFor.size() == 1;
//      assert c3.dependencyFor.isEmpty();
//
//      assert c1.state == ComponentRegistry.State.CONSTRUCTED;
//      assert c2.state == ComponentRegistry.State.CONSTRUCTED;
//      assert c3.state == ComponentRegistry.State.CONSTRUCTED;
//
//      c1.changeState(ComponentRegistry.State.WIRED);
//
//      assert c1.state == ComponentRegistry.State.WIRED;
//      assert c2.state == ComponentRegistry.State.WIRED;
//      assert c3.state == ComponentRegistry.State.CONSTRUCTED;
//
//      c3.changeState(ComponentRegistry.State.STARTED);
//
//      assert c1.state == ComponentRegistry.State.STARTED;
//      assert c2.state == ComponentRegistry.State.STARTED;
//      assert c3.state == ComponentRegistry.State.STARTED;
//
//      c1.changeState(ComponentRegistry.State.CONSTRUCTED);
//
//      assert c1.state == ComponentRegistry.State.CONSTRUCTED;
//      assert c2.state == ComponentRegistry.State.STARTED;
//      assert c3.state == ComponentRegistry.State.CONSTRUCTED;
//   }
//
//   public static class C1
//   {
//      C2 c2;
//
//      @Inject
//      private void inject(@ComponentName("c2")C2 c2)
//      {
//         this.c2 = c2;
//      }
//   }
//
//   public static class C2
//   {
//
//   }
//
//   public static class C3
//   {
//      C1 c1;
//
//      @Inject
//      private void inject(@ComponentName("c1")C1 c1)
//      {
//         this.c1 = c1;
//      }
//   }
}
