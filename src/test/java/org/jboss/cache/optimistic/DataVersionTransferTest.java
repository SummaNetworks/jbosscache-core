package org.jboss.cache.optimistic;

import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests whether data versions are transferred along with state
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "optimistic"}, sequential = true, testName = "optimistic.DataVersionTransferTest")
public class DataVersionTransferTest
{
   private List<Cache<Object, Object>> caches = null;

   @BeforeMethod
   public void setUp()
   {
      Configuration c = new Configuration();
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      c.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);      
      caches = new ArrayList<Cache<Object, Object>>(2);
      caches.add(new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass()));
      caches.get(0).start();

      c = new Configuration();
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      c.setNodeLockingScheme(Configuration.NodeLockingScheme.OPTIMISTIC);
      c.setCacheMode(Configuration.CacheMode.REPL_SYNC);
      caches.add(new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass()));
   }

   @AfterMethod
   public void tearDown()
   {
      if (caches != null)
      {
         for (Cache cache : caches) TestingUtil.killCaches(cache);
         caches = null;
      }
   }

   public void testStateTransferDefaultVersions() throws Exception
   {
      Fqn f = Fqn.fromString("/one/two/three");
      caches.get(0).put(f, "k", "v");
      caches.get(0).put(f, "k1", "v1");
      caches.get(0).remove(f, "k1");

      NodeSPI n = (NodeSPI) caches.get(0).getRoot().getChild(f);
      DataVersion dv = n.getVersion();

      assert dv instanceof DefaultDataVersion : "Should be an instance of DefaultDataVersion";

      assert ((DefaultDataVersion) dv).getRawVersion() == 3 : "Should have accurate data version";

      // now start next cache instance
      caches.get(1).start();

      TestingUtil.blockUntilViewsReceived(10000, caches.get(0), caches.get(1));

      assert caches.get(1).get(f, "k").equals("v") : "Value should have transferred";

      n = (NodeSPI) caches.get(1).getRoot().getChild(f);

      dv = n.getVersion();

      assert dv instanceof DefaultDataVersion : "Should be an instance of DefaultDataVersion";

      assert ((DefaultDataVersion) dv).getRawVersion() == 3 : "Version should have transferred";
      // make sure leakage doesn't occur into data map
      assert n.getData().size() == 1;
   }

   public void testStateTransferCustomVersion() throws Exception
   {
      Fqn f = Fqn.fromString("/one/two/three");
      caches.get(0).getInvocationContext().getOptionOverrides().setDataVersion(new CharVersion('A'));
      caches.get(0).put(f, "k", "v");
      caches.get(0).getInvocationContext().getOptionOverrides().setDataVersion(new CharVersion('B'));
      caches.get(0).put(f, "k1", "v1");
      caches.get(0).getInvocationContext().getOptionOverrides().setDataVersion(new CharVersion('C'));
      caches.get(0).remove(f, "k1");

      NodeSPI n = (NodeSPI) caches.get(0).getRoot().getChild(f);
      DataVersion dv = n.getVersion();

      assert dv instanceof CharVersion : "Should be an instance of CharVersion";

      assert ((CharVersion) dv).version == 'C' : "Should have accurate data version";

      // now start next cache instance
      caches.get(1).start();

      TestingUtil.blockUntilViewsReceived(10000, caches.get(0), caches.get(1));

      assert caches.get(1).get(f, "k").equals("v") : "Value should have transferred";

      n = (NodeSPI) caches.get(1).getRoot().getChild(f);

      dv = n.getVersion();

      assert dv instanceof CharVersion : "Should be an instance of CharVersion";

      assert ((CharVersion) dv).version == 'C' : "Version should have transferred";
      // make sure leakage doesn't occur into data map
      assert n.getData().size() == 1;
   }

   public void testStateTransferIntermediateNodeDefaultVersions() throws Exception
   {
      Fqn f = Fqn.fromString("/one/two/three");
      Fqn intermediate = f.getParent();

      caches.get(0).put(f, "k", "v");
      caches.get(0).put(intermediate, "k", "v");

      NodeSPI n = (NodeSPI) caches.get(0).getRoot().getChild(intermediate);
      DataVersion dv = n.getVersion();

      assert dv instanceof DefaultDataVersion : "Should be an instance of DefaultDataVersion";

      assert ((DefaultDataVersion) dv).getRawVersion() == 1 : "Should have accurate data version";

      // now start next cache instance
      caches.get(1).start();

      TestingUtil.blockUntilViewsReceived(10000, caches.get(0), caches.get(1));

      assert caches.get(1).get(intermediate, "k").equals("v") : "Value should have transferred";

      n = (NodeSPI) caches.get(0).getRoot().getChild(intermediate);
      dv = n.getVersion();

      assert dv instanceof DefaultDataVersion : "Should be an instance of DefaultDataVersion";

      assert ((DefaultDataVersion) dv).getRawVersion() == 1 : "Should have accurate data version";
      // make sure leakage doesn't occur into data map
      assert n.getData().size() == 1;
   }

   public void testStateTransferIntermediateNodeCustomVersion() throws Exception
   {
      Fqn f = Fqn.fromString("/one/two/three");
      Fqn intermediate = f.getParent();

      caches.get(0).put(f, "k", "v");
      caches.get(0).getInvocationContext().getOptionOverrides().setDataVersion(new CharVersion('X'));
      caches.get(0).put(intermediate, "k", "v");

      NodeSPI n = (NodeSPI) caches.get(0).getRoot().getChild(intermediate);
      DataVersion dv = n.getVersion();

      assert dv instanceof CharVersion : "Should be an instance of CharVersion";

      assert ((CharVersion) dv).version == 'X' : "Should have accurate data version";

      // now start next cache instance
      caches.get(1).start();

      TestingUtil.blockUntilViewsReceived(10000, caches.get(0), caches.get(1));

      assert caches.get(1).get(intermediate, "k").equals("v") : "Value should have transferred";

      n = (NodeSPI) caches.get(0).getRoot().getChild(intermediate);
      dv = n.getVersion();

      assert dv instanceof CharVersion : "Should be an instance of CharVersion";

      assert ((CharVersion) dv).version == 'X' : "Should have accurate data version";
      // make sure leakage doesn't occur into data map
      assert n.getData().size() == 1;
   }

   public static class CharVersion implements DataVersion
   {
      private char version = 'A';

      public CharVersion(char version)
      {
         this.version = version;
      }

      public boolean newerThan(DataVersion other)
      {
         if (other instanceof CharVersion)
         {
            CharVersion otherVersion = (CharVersion) other;
            return version > otherVersion.version;
         }
         else
         {
            return true;
         }
      }
   }

}
