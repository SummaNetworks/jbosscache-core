package org.jboss.cache.marshall;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * This test ensures the CacheMarshaller doesn't use stale regions when attempting to unmarshall state transfers.  Seen intermittently
 * when async replication is used, since JGroups doesn't attempt to marshall a return value, thereby leaving a stale region in thread local
 * in the cache marshaller, which then gets reused when the thread is reused to provide state.
 * <p/>
 * Need to ensure that the same thread is used to process incoming requests as well as state transfers, hence limiting the JGroups
 * thread pool size to 1.
 * <p/>
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
@Test(groups = "functional", sequential = true, testName = "marshall.InvalidRegionForStateTransferTest")
public class InvalidRegionForStateTransferTest
{
   Cache<Object, Object> c1, c2;
   ReplicationListener replListener2;

   @BeforeMethod
   public void setUp() throws CloneNotSupportedException
   {
      c1 = new UnitTestCacheFactory<Object, Object>().createCache(UnitTestConfigurationFactory.createConfiguration(Configuration.CacheMode.REPL_ASYNC), false, getClass());
      c1.getConfiguration().setNodeLockingScheme(NodeLockingScheme.PESSIMISTIC);
      String jgroupsCfg = c1.getConfiguration().getClusterConfig();

      // make sure we use STATE_TRANSFER and not STREAMING_STATE_TRANSFER, so the same thread pool is used for incoming calls and ST
      jgroupsCfg = jgroupsCfg.replace("STREAMING_STATE_TRANSFER", "STATE_TRANSFER");
      // also make sure we use a thread pool size of 1
      jgroupsCfg = jgroupsCfg.replaceFirst("thread_pool.max_threads=[0-9]*;", "thread_pool.max_threads=1;");

      c1.getConfiguration().setClusterConfig(jgroupsCfg);

      c1.getConfiguration().setUseRegionBasedMarshalling(true);
      c1.start();

      c2 = new UnitTestCacheFactory<Object, Object>().createCache(c1.getConfiguration().clone(), getClass());
      replListener2 = ReplicationListener.getReplicationListener(c2);

      TestingUtil.blockUntilViewsReceived(60000, c1, c2);
   }

   @AfterMethod
   public void tearDown()
   {
      TestingUtil.killCaches(c1, c2);
      c1 = null;
      c2 = null;
   }

   public void testUseOfInvalidRegion()
   {
      Fqn fqn = Fqn.fromString("/a/b/c/d");
      c1.getRegion(fqn.getParent(), true).registerContextClassLoader(getClass().getClassLoader());
      c2.getRegion(fqn.getParent(), true).registerContextClassLoader(getClass().getClassLoader());

      replListener2.expect(PutKeyValueCommand.class);
      // write something; will cause a stale region to be stored in C2's cache marshaller
      c1.put(fqn, "k", "v");
      assert c1.get(fqn, "k").equals("v");

      replListener2.waitForReplicationToOccur(250);

      // assert that this made it to c2
      assert c2.get(fqn, "k").equals("v");

      // c2's cache marshaller's thread local would be polluted now.

      // restart c1 so that it forces a state transfer from c2
      c1.destroy();
      c1.create();
      Region r = c1.getRegion(fqn.getParent(), true);
      r.registerContextClassLoader(getClass().getClassLoader());
      r.deactivate();
      c1.start();

      TestingUtil.blockUntilViewsReceived(60000, c1, c2);

      // assert that the state has been transferred to C1
      assert c1.get(fqn, "k").equals("v");
   }
}
