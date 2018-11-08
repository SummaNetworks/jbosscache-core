package org.jboss.cache;

import org.jboss.cache.buddyreplication.BuddyFqnTransformer;
import org.jboss.cache.marshall.NodeData;
import org.jboss.cache.mock.MockNodesFixture;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests functionality from DataContainer.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
@Test(groups = "unit", sequential = true, testName = "DataContainerTest")
public class DataContainerTest
{
   private DataContainerImpl container;
   private MockNodesFixture nodes;

   //end of node structure.

   @BeforeMethod
   public void setUp()
   {
      nodes = new MockNodesFixture();
      container = new DataContainerImpl();
      container.setRoot(nodes.root);
      container.setBuddyFqnTransformer(new BuddyFqnTransformer());
   }

   @AfterMethod
   public void tearDown()
   {
      container = null;
      nodes = null;
   }
   /**
    * tests {@link DataContainerImpl#peek(Fqn, boolean, boolean)} method
    */
   public void testPeekNodesSimple()
   {
      assert nodes.root == container.peek(Fqn.ROOT, true, true);
      assert nodes.adfgNode == container.peek(nodes.adfg, false, false);
      assert nodes.adfgNode == container.peek(nodes.adfg, false, true);
      assert nodes.adfgNode == container.peek(nodes.adfg, true, true);
   }

   /**
    * tests {@link DataContainerImpl#peek(Fqn, boolean, boolean)} for invalid nodes.
    */
   public void testPeekInvalidNodes()
   {
      nodes.adfgNode.setValid(false, false);
      assert null == container.peek(nodes.adfg, true, false);
      assert nodes.adfgNode == container.peek(nodes.adfg, true, true);
   }

   /**
    * tests {@link DataContainerImpl#peek(Fqn, boolean, boolean)} method for deleted nodes.
    */
   public void testPeekDeletedNodes()
   {
      nodes.adfgNode.markAsDeleted(true);
      assert null == container.peek(nodes.adfg, false, false);
      assert nodes.adfgNode == container.peek(nodes.adfg, true, false);
   }

   /**
    * tests {@link DataContainerImpl#exists(Fqn)}
    */
   public void testsExists()
   {
      assert container.exists(nodes.ab) : "ab exists";
      nodes.abNode.markAsDeleted(true);
      assert !container.exists(nodes.ab) : "ab marked as deleted";
      assert container.exists(nodes.ad);
      nodes.adNode.setValid(false, false);
      assert !container.exists(nodes.ade) : "its parent was marked as invalid";
   }

   /**
    * tests {@link DataContainerImpl#hasChildren(Fqn)}
    */
   public void testHasChildren()
   {
      assert container.hasChildren(nodes.ad) : " ade is a child of ad";
      assert !container.hasChildren(nodes.notExistent) : " this one does not exist";
      assert !container.hasChildren(nodes.adfg) : "this one exists but does not have children";
      nodes.adNode.setValid(false, false);
      assert !container.hasChildren(nodes.ad) : "ad exists and has children but is invalid";
   }

   /**
    * test {@link DataContainer#buildNodeData(java.util.List}
    */
   public void testBuildNodeData()
   {
      nodes.abNode.put("ab", "ab");
      nodes.abcNode.put("abc", "abc");
      List<NodeData> result = new ArrayList<NodeData>();
      container.buildNodeData(result, nodes.abNode, true);
      assert result.size() == 2;
      assert result.contains(new NodeData(nodes.ab, nodes.abNode.getData(), true));
      assert result.contains(new NodeData(nodes.abc, nodes.abcNode.getData(), true));
   }

   /**
    * tests {@link DataContainerImpl#getNodesForEviction(Fqn, boolean)} in a nonrecursive scenario.
    */
   public void testGetNodesForEvictionNonrecursive()
   {
      //check for root first
      List<Fqn> result = container.getNodesForEviction(Fqn.ROOT, false);
      assert result.size() == 1 : "for root the direct children are considered for eviction";
      assert result.contains(nodes.a);

      //check normal
      result = container.getNodesForEviction(nodes.ad, false);
      assert result.size() == 1 : "one child expected";
      assert result.contains(nodes.ad);

      //check resident scenario
      nodes.adNode.setResident(true);
      result = container.getNodesForEviction(nodes.ad, false);
      assert result.size() == 0 : "no children expected";
   }

   /**
    * tests {@link DataContainerImpl#getNodesForEviction(Fqn, boolean)} in a recursive scenario.
    */
   public void testGetNodesForEvictionRecursive()
   {
      //check for root first
      List<Fqn> result = container.getNodesForEviction(Fqn.ROOT, true);
      assert result.size() == 8 : "all children are considered for eviction";

      //check normal
      result = container.getNodesForEviction(nodes.ad, true);
      assert result.size() == 5 : "five childrens expected";
      assert result.contains(nodes.ad);
      assert result.contains(nodes.ade);
      assert result.contains(nodes.adf);
      assert result.contains(nodes.adfh);
      assert result.contains(nodes.adfg);

      //check resident scenario
      nodes.adNode.setResident(true);
      result = container.getNodesForEviction(nodes.ad, true);
      assert result.size() == 4 : "only children expected";
      assert result.contains(nodes.ade);
      assert result.contains(nodes.adf);
      assert result.contains(nodes.adfh);
      assert result.contains(nodes.adfg);
   }

   /**
    * tests {@link DataContainerImpl#getNodesForEviction(Fqn, boolean)} in a recursive scenario.
    */
   public void testGetNodesForEvictionRecursiveNullNodes()
   {
      container.removeFromDataStructure(nodes.ad, true);
      //check for root first
      List<Fqn> result = container.getNodesForEviction(Fqn.ROOT, true);
      assert result.size() == 3 : "all children are considered for eviction";

      //check normal
      // this node does not exist!!  Should NOT throw a NPE.
      result = container.getNodesForEviction(nodes.ad, true);
      assert result.isEmpty() : "Should be empty";
   }


   /**
    * tests {@link DataContainerImpl#getNumberOfNodes()}
    */
   public void testGetNumberOfNodes()
   {
      int i;
      assert (i=container.getNumberOfNodes()) == -1 : "-1 nodes expected, was " + i;
      container.started = true;
      assert (i=container.getNumberOfNodes()) == 8 : "8 nodes expected, was " + i;
   }

   /**
    * tests {@link DataContainerImpl#removeFromDataStructure(Fqn, boolean)} having skipMarkerCheck set to false.
    */
   public void removeFromDataStructureNoSkip1()
   {
      //check inexisten node
      assert !container.removeFromDataStructure(nodes.notExistent, false);

      //check root - all the subnodes should be deleted and marked as invalid, but the root itself
      nodes.root.markAsDeleted(true);
      assert container.removeFromDataStructure(Fqn.ROOT, false);
      assert !nodes.aNode.isValid();
      assert !nodes.abNode.isValid();
      assert !nodes.abcNode.isValid();
      assert !nodes.adNode.isValid();
      assert !nodes.adeNode.isValid();
      assert !nodes.adfNode.isValid();
      assert !nodes.adfgNode.isValid();
      assert !nodes.adfhNode.isValid();
      assert nodes.root.isValid();
   }

   /**
    * tests {@link DataContainerImpl#removeFromDataStructure(Fqn, boolean)} having skipMarkerCheck set to false.
    */
   public void removeFromDataStructureNoSkip2()
   {
      //check root - all the subnodes should be deleted and marked as invalid, but the root itself
      nodes.root.markAsDeleted(false);
      assert !container.removeFromDataStructure(Fqn.ROOT, false);

      //check a normal node
      nodes.adNode.markAsDeleted(true);
      assert container.removeFromDataStructure(nodes.ad, false);
      assert !nodes.adeNode.isValid();
      assert !nodes.adfNode.isValid();
      assert !nodes.adfhNode.isValid();
      assert !nodes.adfhNode.isValid();
   }

   /**
    * tests {@link DataContainerImpl#removeFromDataStructure(Fqn, boolean)} having skipMarkerCheck set to true.
    */
   public void removeFromDataStructureWithSkip()
   {
      //check inexisten node
      assert !container.removeFromDataStructure(nodes.notExistent, false);

      //check root - all the subnodes should be deleted and marked as invalid, but the root itself
      assert container.removeFromDataStructure(Fqn.ROOT, true);
      assert !nodes.aNode.isValid();
      assert !nodes.abNode.isValid();
      assert !nodes.abcNode.isValid();
      assert !nodes.adNode.isValid();
      assert !nodes.adeNode.isValid();
      assert !nodes.adfNode.isValid();
      assert !nodes.adfgNode.isValid();
      assert !nodes.adfhNode.isValid();
      assert nodes.root.isValid();
   }

   /**
    * tests {@link DataContainerImpl#evict(Fqn)}
    */
   public void testEvict()
   {
      //tests eviction of leaf nodes
      assert container.evict(nodes.abc);
      assert !nodes.abcNode.isValid();
      assert !nodes.abNode.hasChild("c");

      //test eviction of intermediate nodes
      nodes.adNode.put("key", "value");
      assert !container.evict(nodes.ad);
      assert nodes.adNode.isValid();
      assert nodes.adNode.getData().isEmpty();
      assert nodes.aNode.hasChild("d");
      assert nodes.adNode.hasChild("e");
   }

   /**
    * test {@link DataContainerImpl#createNodes(Fqn)}
    */
   public void testCreateNodes()
   {
      Object[] objects = container.createNodes(Fqn.fromString("/a/x/y/z"));
      List result = (List) objects[0];
      assert result.size() == 3;
      assert ((NodeSPI) result.get(0)).getFqn().equals(Fqn.fromString("/a/x"));
      assert ((NodeSPI) result.get(1)).getFqn().equals(Fqn.fromString("/a/x/y"));
      assert ((NodeSPI) result.get(2)).getFqn().equals(Fqn.fromString("/a/x/y/z"));
      NodeSPI target = (NodeSPI) objects[1];
      assert target != null;
      assert target.getFqn().toString().equals("/a/x/y/z");
   }
}
