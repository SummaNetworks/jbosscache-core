package org.jboss.cache.api.nodevalidity;

import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.NodeLockingScheme;
import org.jboss.cache.optimistic.DefaultDataVersion;
import org.jboss.cache.transaction.DummyTransactionManagerLookup;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;

/**
 * exercises the isValid() api call on node.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @since 2.1.0
 */
@Test(groups = {"functional"})
public abstract class NodeValidityTestBase extends AbstractMultipleCachesTest
{
   protected NodeLockingScheme nodeLockingScheme = NodeLockingScheme.PESSIMISTIC;

   // needed to attach a blockUntilViewsReceived in setup
   protected boolean clustered = true;

   // needed to test tombstones
   protected boolean invalidation = false;


   protected static final Fqn parent = Fqn.fromString("/parent");
   protected static final Fqn child = Fqn.fromString("/parent/child");
   protected static final String K = "k", V = "v";
   protected Cache<String, String> observer;
   protected Cache<String, String> modifier;

   protected abstract Cache<String, String> createObserver();

   protected abstract Cache<String, String> createModifier();

   protected void nodeLockingSchemeSpecificSetup(Configuration c)
   {
      c.setNodeLockingScheme(nodeLockingScheme);
      if (isOptimistic())
      {
         c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
         c.setSyncCommitPhase(true);
         c.setSyncRollbackPhase(true);
      }
   }

   protected boolean isOptimistic()
   {
      return nodeLockingScheme == NodeLockingScheme.OPTIMISTIC;
   }

   protected void createCaches()
   {
      observer = createObserver();
      modifier = createModifier();
      registerCaches(observer, modifier);
      if (clustered) TestingUtil.blockUntilViewsReceived(60000, observer, modifier);
   }

   public void testRemoval()
   {
      observer.put(parent, K, V);

      Node<String, String> obsNode = observer.getRoot().getChild(parent);

      assert obsNode.get(K).equals(V) : "Data should be in the node.";
      assert obsNode.isValid() : "Node should be valid";

      modifier.removeNode(parent);

      assert !obsNode.isValid() : "Should no longer be valid";
   }

   public void testRemovalWithChildren()
   {
      observer.put(child, K, V);

      Node<String, String> obsParentNode = observer.getRoot().getChild(parent);
      Node<String, String> obsChildNode = observer.getRoot().getChild(child);

      assert obsChildNode.get(K).equals(V) : "Data should be in the node.";
      assert obsChildNode.isValid() : "Node should be valid";
      assert obsParentNode.isValid() : "Node should be valid";

      modifier.removeNode(parent);

      assert !obsParentNode.isValid() : "Should no longer be valid";
      assert !obsChildNode.isValid() : "Should no longer be valid";
   }

   public void testMove()
   {
      Fqn newParent = Fqn.fromString("/newParent/parent");

      //observer.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      observer.put(parent, K, V);

      Node<String, String> obsNode = observer.getRoot().getChild(parent);

      assert obsNode.get(K).equals(V) : "Data should be in the node.";
      assert obsNode.isValid() : "Node should be valid";

      // new parent needs to exist first.
      modifier.getRoot().addChild(newParent);
      modifier.move(parent, newParent.getParent());

      // the old node is only marked as invalid if we use opt locking
      // with pess locking we directly move the node reference so the old ref is still valid, EVEN if the move happens
      // remotely.
      if (isOptimistic()) assert !obsNode.isValid() : "Should no longer be valid";

      assert observer.getRoot().getChild(newParent).isValid() : "Should be valid";
   }

   public void testMoveWithChildren()
   {
      Fqn newParent = Fqn.fromString("/newParent/parent");
      Fqn newChild = Fqn.fromString("/newParent/parent/child");

//      observer.getInvocationContext().getOptionOverrides().setCacheModeLocal(true);
      observer.put(child, K, V);

      Node<String, String> obsParentNode = observer.getRoot().getChild(parent);
      Node<String, String> obsChildNode = observer.getRoot().getChild(child);

      assert obsChildNode.get(K).equals(V) : "Data should be in the node.";
      assert obsChildNode.isValid() : "Node should be valid";
      assert obsParentNode.isValid() : "Node should be valid";

      // new parent needs to exist first.
      modifier.getRoot().addChild(newParent);
      modifier.move(parent, newParent.getParent());

      // the old node is only marked as invalid if we use opt locking
      // with pess locking we directly move the node reference so the old ref is still valid.
      if (isOptimistic())
      {
         assert !obsParentNode.isValid() : "Should no longer be valid";
         assert !obsChildNode.isValid() : "Should no longer be valid";
      }

      assert observer.getRoot().getChild(newParent).isValid() : "Should be valid";
      assert observer.getRoot().getChild(newChild).isValid() : "Should be valid";
   }

   public void testEvict()
   {
      // eviction should affect validity
      observer.put(parent, K, V);
      Node<String, String> obsNode = observer.getRoot().getChild(parent);

      assert obsNode.get(K).equals(V) : "Data should be in the node.";
      assert obsNode.isValid() : "Node should be valid";

      // eviction needs to happen on the same cache being watched
      observer.evict(parent, false);

      assert !obsNode.isValid() : "Node should not be valid";
   }

   public void testOperationsOnInvalidNode()
   {
      observer.put(parent, K, V);
      Node<String, String> obsNode = observer.getRoot().getChild(parent);

      assert obsNode.get(K).equals(V) : "Data should be in the node.";
      assert obsNode.isValid() : "Node should be valid";

      modifier.removeNode(parent);

      assert !obsNode.isValid() : "Node should not be valid";

      // all operations on the cached node should throw a NodeNotValidException

      try
      {
         obsNode.get(K);
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }

      try
      {
         obsNode.put(K, "v2");
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }

      try
      {
         obsNode.remove(K);
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }

      try
      {
         obsNode.clearData();
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }

      try
      {
         obsNode.putAll(Collections.singletonMap(K, "v2"));
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }

      try
      {
         obsNode.getKeys();
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }

      try
      {
         obsNode.hasChild("Something");
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }

      try
      {
         obsNode.removeChild("Something");
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }

      try
      {
         obsNode.addChild(child);
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }

      try
      {
         obsNode.getChildrenNames();
         assert false : "Should fail";
      }
      catch (NodeNotValidException good)
      {
         // do nothing
      }
   }

   public void testExistenceOfTombstones()
   {
      CacheSPI modifierImpl = (CacheSPI) modifier;
      CacheSPI observerImpl = (CacheSPI) observer;

      modifier.put(parent, K, V);
      modifier.removeNode(parent);

      if (isOptimistic() && invalidation)
      {
         // if we are using optimistic invalidation then we should see tombstones.  NOT otherwise.
         NodeSPI modifierTombstone = modifierImpl.peek(parent, true, true);
         NodeSPI observerTombstone = observerImpl.peek(parent, true, true);

         assert modifierTombstone != null : "Modifier tombstone should not be null";
         assert observerTombstone != null : "Observer tombstone should not be null";

         assert !modifierTombstone.isValid() : "Should not be valid";
         assert !observerTombstone.isValid() : "Should not be valid";

         assert ((DefaultDataVersion) modifierTombstone.getVersion()).getRawVersion() == 2 : "Tombstone should be versioned";
         assert ((DefaultDataVersion) observerTombstone.getVersion()).getRawVersion() == 2 : "Tombstone should be versioned";

      }
      else
      {
         // if we are using pess locking there should be NO tombstones, regardless of replication/invalidation!
         assert modifierImpl.peek(parent, true, true) == null : "Tombstone should not exist";
         assert observerImpl.peek(parent, true, true) == null : "Tombstone should not exist";
      }
   }

   public void testExistenceOfTombstonesWithChildren()
   {
      CacheSPI modifierImpl = (CacheSPI) modifier;
      CacheSPI observerImpl = (CacheSPI) observer;

      modifier.put(child, K, V);
      modifier.removeNode(parent);

      if (isOptimistic() && invalidation)
      {
         // if we are using optimistic invalidation then we should see tombstones.  NOT otherwise.
         NodeSPI modifierParentTombstone = modifierImpl.peek(parent, true, true);
         NodeSPI observerParentTombstone = observerImpl.peek(parent, true, true);
         NodeSPI modifierChildTombstone = modifierImpl.peek(child, true, true);
         NodeSPI observerChildTombstone = observerImpl.peek(child, true, true);

         assert modifierParentTombstone != null : "Modifier parent tombstone should not be null";
         assert observerParentTombstone != null : "Observer parent tombstone should not be null";
         assert modifierChildTombstone != null : "Modifier child tombstone should not be null";
         assert observerChildTombstone != null : "Observer child tombstone should not be null";

         assert !modifierParentTombstone.isValid() : "Should not be valid";
         assert !observerParentTombstone.isValid() : "Should not be valid";
         assert !modifierChildTombstone.isValid() : "Should not be valid";
         assert !observerChildTombstone.isValid() : "Should not be valid";

         assert ((DefaultDataVersion) modifierParentTombstone.getVersion()).getRawVersion() == 1 : "Tombstone should be versioned";
         assert ((DefaultDataVersion) observerParentTombstone.getVersion()).getRawVersion() == 1 : "Tombstone should be versioned";

         // note that versions on children cannot be incremented/updated since the remove operation was
         // performed on the parent.
         assert ((DefaultDataVersion) modifierChildTombstone.getVersion()).getRawVersion() == 1 : "Tombstone should be versioned";
         assert ((DefaultDataVersion) observerChildTombstone.getVersion()).getRawVersion() == 1 : "Tombstone should be versioned";

      }
      else
      {
         // if we are using pess locking there should be NO tombstones, regardless of replication/invalidation!
         assert modifierImpl.peek(parent, true, true) == null : "Tombstone should not exist";
         assert observerImpl.peek(parent, true, true) == null : "Tombstone should not exist";
         assert modifierImpl.peek(child, true, true) == null : "Tombstone should not exist";
         assert observerImpl.peek(child, true, true) == null : "Tombstone should not exist";
      }
   }
}
