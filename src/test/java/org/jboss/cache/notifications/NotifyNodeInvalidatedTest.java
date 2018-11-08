package org.jboss.cache.notifications;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.notifications.event.Event;
import static org.jboss.cache.notifications.event.Event.Type.NODE_INVALIDATED;
import org.jboss.cache.notifications.event.EventImpl;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import org.jboss.cache.UnitTestCacheFactory;

@Test(groups = "functional", testName = "notifications.NotifyNodeInvalidatedTest")
public class NotifyNodeInvalidatedTest
{
   public void testInvalidatedCallback() throws CloneNotSupportedException
   {
      Cache<String, String> c1 = null, c2 = null;
      try
      {
         Configuration cfg = UnitTestConfigurationFactory.createConfiguration(CacheMode.INVALIDATION_SYNC, false);
         cfg.setNodeLockingScheme(NodeLockingScheme.MVCC);
         c1 = new UnitTestCacheFactory<String, String>().createCache(cfg.clone(), getClass());
         c2 = new UnitTestCacheFactory<String, String>().createCache(cfg.clone(), getClass());
         EventLog eventLog = new EventLog();
         c2.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
         c2.put("/a/b/c", "x", "y");
         c2.addCacheListener(eventLog);
         c1.put("/a/b/c", "k", "v");

         List<Event> expected = new ArrayList<Event>();
         expected.add(new EventImpl(true, c2, null, null, Fqn.fromString("/a/b/c"), null, false, null, false, null, NODE_INVALIDATED));
         expected.add(new EventImpl(false, c2, null, null, Fqn.fromString("/a/b/c"), null, false, null, false, null, NODE_INVALIDATED));

         assert expected.equals(eventLog.events) : "Expected " + expected + " but got " + eventLog.events;
         assert c2.getNode("/a/b/c") == null;
      }
      finally
      {
         TestingUtil.killCaches(c1, c2);
      }
   }
}
