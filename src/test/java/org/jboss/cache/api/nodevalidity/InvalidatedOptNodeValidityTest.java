package org.jboss.cache.api.nodevalidity;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.optimistic.DefaultDataVersion;
import org.testng.annotations.Test;

/**
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional", "optimistic"}, testName = "api.nodevalidity.InvalidatedOptNodeValidityTest")
public class InvalidatedOptNodeValidityTest extends InvalidatedPessNodeValidityTest
{
   public InvalidatedOptNodeValidityTest()
   {
      nodeLockingScheme = NodeLockingScheme.OPTIMISTIC;
   }

   public void testTombstoneRevival()
   {
      modifier.put(parent, K, V);
      modifier.removeNode(parent);

      NodeSPI observerNode = (NodeSPI) observer.getRoot().getChild(parent);
      assert observerNode == null : "Should be removed";

      // now try a put on a with a newer data version; should work
      modifier.getInvocationContext().getOptionOverrides().setDataVersion(new DefaultDataVersion(10));
      modifier.put(parent, K, V);

      NodeSPI modifierNode = (NodeSPI) modifier.getRoot().getChild(parent);
      assert modifierNode != null : "Should not be null";
      assert modifierNode.isValid() : "No longer a tombstone";
      assert ((DefaultDataVersion) modifierNode.getVersion()).getRawVersion() == 10 : "Version should be updated";

      observerNode = (NodeSPI) observer.getRoot().getChild(parent);
      assert observerNode != null : "Should not be null";
      assert observerNode.isValid() : "No longer a tombstone";
      assert ((DefaultDataVersion) observerNode.getVersion()).getRawVersion() == 10 : "Version should be updated";
   }

   public void testTombstoneVersioningFailure() throws Exception
   {
      CacheSPI modifierImpl = (CacheSPI) modifier;
      CacheSPI observerImpl = (CacheSPI) observer;

      modifier.put(parent, K, V);


      // test that this exists in the (shared) loader
      assert loader.get(parent) != null;
      assert loader.get(parent).size() > 0;

      modifier.removeNode(parent);

      // assert that tombstones exist on both instances
      assert modifierImpl.peek(parent, true, true) != null;
      assert observerImpl.peek(parent, true, true) != null;
      assert modifierImpl.peek(parent, false, false) == null;
      assert observerImpl.peek(parent, false, false) == null;

      // make sure this does not exist in the loader; since it HAS been removed
      assert loader.get(parent) == null;

      NodeSPI observerNode = (NodeSPI) observer.getRoot().getChild(parent);
      assert observerNode == null : "Should be removed";

      // now try a put on a with a newer data version; should work
      modifier.getInvocationContext().getOptionOverrides().setDataVersion(new DefaultDataVersion(1));
      try
      {
         modifier.put(parent, K, V);
         assert false : "Should have barfed!";
      }
      catch (RuntimeException expected)
      {

      }

      NodeSPI modifierNode = (NodeSPI) modifier.getRoot().getChild(parent);
      assert modifierNode == null : "Should be null";

      observerNode = (NodeSPI) observer.getRoot().getChild(parent);
      assert observerNode == null : "Should be null";

      NodeSPI modifierTombstone = modifierImpl.peek(parent, true, true);
      NodeSPI observerTombstone = observerImpl.peek(parent, true, true);

      assert modifierTombstone != null : "Tombstone should still exist";
      assert observerTombstone != null : "Tombstone should still exist";

      assert !modifierTombstone.isValid() : "Should not be valid";
      assert !observerTombstone.isValid() : "Should not be valid";

      assert ((DefaultDataVersion) modifierTombstone.getVersion()).getRawVersion() == 2 : "Should retain versioning";
      assert ((DefaultDataVersion) observerTombstone.getVersion()).getRawVersion() == 2 : "Should retain versioning";
   }
}