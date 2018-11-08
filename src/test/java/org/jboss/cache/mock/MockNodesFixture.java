package org.jboss.cache.mock;

import org.jboss.cache.Fqn;

/**
 * This class builds a tree of <code>NodeSpiMock</code>s that can be used through tests.
 * <pre>
 * It serves following purposes:
 * - having a common known structure through all the tests is useful for test redability
 * - not having to write again and again a new fixture for each test.
 * </pre>
 * Note: changing the fixture might cause tests depending on this class to fail, as the expect certain number of nodes,
 * number of child etc.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class MockNodesFixture
{
   public NodeSpiMock root;
   public NodeSpiMock aNode;
   public Fqn a;
   public Fqn ab;
   public Fqn abc;
   public Fqn ad;
   public Fqn ade;
   public Fqn adf;
   public Fqn adfh;
   public Fqn adfg;
   public NodeSpiMock abNode;
   public NodeSpiMock abcNode;
   public NodeSpiMock adNode;
   public NodeSpiMock adeNode;
   public NodeSpiMock adfNode;
   public NodeSpiMock adfhNode;
   public NodeSpiMock adfgNode;
   public Fqn notExistent = Fqn.fromString("aaa" + System.currentTimeMillis());

   public MockNodesFixture()
   {
      this(Fqn.ROOT);
   }

   /**
    * Will place the fqn prefix before all created nodes.
    */
   public MockNodesFixture(Fqn prefix)
   {
      a = Fqn.fromRelativeElements(prefix, "a");
      ab = Fqn.fromRelativeElements(prefix, "a", "b");
      abc = Fqn.fromRelativeElements(prefix, "a", "b", "c");
      ad = Fqn.fromRelativeElements(prefix, "a", "d");
      ade = Fqn.fromRelativeElements(prefix, "a", "d", "e");
      adf = Fqn.fromRelativeElements(prefix, "a", "d", "f");
      adfh = Fqn.fromRelativeElements(prefix, "a", "d", "f", "h");
      adfg = Fqn.fromRelativeElements(prefix, "a", "d", "f", "g");
      root = new NodeSpiMock(Fqn.ROOT);
      aNode = (NodeSpiMock) root.addChild(a);
      abNode = (NodeSpiMock) root.addChild(ab);
      abcNode = (NodeSpiMock) root.addChild(abc);
      adNode = (NodeSpiMock) root.addChild(ad);
      adeNode = (NodeSpiMock) root.addChild(ade);
      adfNode = (NodeSpiMock) root.addChild(adf);
      adfhNode = (NodeSpiMock) root.addChild(adfh);
      adfgNode = (NodeSpiMock) root.addChild(adfg);
   }
}
