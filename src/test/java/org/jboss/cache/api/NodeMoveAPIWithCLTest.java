package org.jboss.cache.api;

import org.testng.annotations.Test;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;
import org.jboss.cache.*;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.parsing.XmlConfigHelper;
import org.jboss.cache.config.parsing.element.LoadersElementParser;
import org.jboss.cache.loader.testloaders.DummyInMemoryCacheLoader;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;

import javax.transaction.TransactionManager;
import java.util.Map;

/**
 * @author Mircea.Markus@jboss.com
 */
@Test(groups = {"functional", "pessimistic"}, testName = "api.NodeMoveAPIWithCLTest")
public class NodeMoveAPIWithCLTest extends AbstractSingleCacheTest
{

   protected final Log log = LogFactory.getLog(getClass());

   protected static final Fqn A = Fqn.fromString("/a"), B = Fqn.fromString("/b"), C = Fqn.fromString("/c"), D = Fqn.fromString("/d"), E = Fqn.fromString("/e");
   protected static final Object k = "key", vA = "valueA", vB = "valueB", vC = "valueC", vD = "valueD", vE = "valueE";

   protected Configuration.NodeLockingScheme nodeLockingScheme = Configuration.NodeLockingScheme.PESSIMISTIC;

   private TransactionManager tm;

   protected CacheSPI createCache()
   {
      // start a single cache instance
      CacheSPI<Object, Object> cache = (CacheSPI<Object, Object>) new UnitTestCacheFactory<Object, Object>().createCache("configs/local-tx.xml", false, getClass());
      cache.getConfiguration().setNodeLockingScheme(nodeLockingScheme);
      cache.getConfiguration().setFetchInMemoryState(false);
      cache.getConfiguration().setEvictionConfig(null);
      configure(cache.getConfiguration());
      cache.start();
      tm = cache.getTransactionManager();
      return cache;
   }

   protected void configure(Configuration c)
   {
      // to be overridden
   }


   public void testWithCacheloaders() throws Exception
   {
      doCacheLoaderTest(false, false);
   }

   public void testWithPassivation() throws Exception
   {
      doCacheLoaderTest(true, false);
   }

   public void testWithCacheloadersTx() throws Exception
   {
      doCacheLoaderTest(false, true);
   }

   public void testWithPassivationTx() throws Exception
   {
      doCacheLoaderTest(true, true);
   }

   protected void doCacheLoaderTest(boolean pasv, boolean useTx) throws Exception
   {
      Node<Object, Object> rootNode = cache.getRoot();

      cache.destroy();
      cache.getConfiguration().setCacheLoaderConfig(getSingleCacheLoaderConfig(pasv, "/", DummyInMemoryCacheLoader.class.getName(), "debug=true", false, false, false, false));
      cache.start();

      DummyInMemoryCacheLoader loader = (DummyInMemoryCacheLoader) cache.getCacheLoaderManager().getCacheLoader();

      rootNode.put("key", "value");

      if (!pasv)
      {
         Map m = loader.get(Fqn.ROOT);
         assertNotNull("Should not be null", m);
         assertEquals("value", m.get("key"));
      }

      Node<Object, Object> nodeA = rootNode.addChild(A);
      nodeA.put(k, vA);
      Node<Object, Object> nodeB = rootNode.addChild(B);
      nodeB.put(k, vB);
      Node<Object, Object> nodeC = nodeA.addChild(C);
      nodeC.put(k, vC);
      Node<Object, Object> nodeD = nodeC.addChild(D);
      nodeD.put(k, vD);
      Node<Object, Object> nodeE = nodeD.addChild(E);
      nodeE.put(k, vE);
      cache.evict(Fqn.ROOT, true);

      // move
      if (useTx) tm.begin();
      cache.move(nodeC.getFqn(), nodeB.getFqn());
      if (useTx) tm.commit();

      // after eviction, the node objects we hold are probably stale.
      nodeA = rootNode.getChild(A);
      nodeB = rootNode.getChild(B);
      nodeC = nodeB.getChild(C);
      log.info("nodeC get child B ");
      nodeD = nodeC.getChild(D);
      log.info("nodeD get child E ");
      nodeE = nodeD.getChild(E);

      Fqn old_C = C;
      Fqn old_D = Fqn.fromRelativeFqn(old_C, D);
      Fqn old_E = Fqn.fromRelativeFqn(old_D, E);

      // test data
      assertEquals(vA, nodeA.get(k));
      assertEquals(vB, nodeB.get(k));
      assertEquals(vC, nodeC.get(k));
      assertEquals(vD, nodeD.get(k));
      assertEquals(vE, nodeE.get(k));

      // parentage
      assertEquals(rootNode, nodeA.getParent());
      assertEquals(rootNode, nodeB.getParent());
      assertEquals(nodeB, nodeC.getParent());
      assertEquals(nodeC, nodeD.getParent());
      assertEquals(nodeD, nodeE.getParent());


      if (pasv) cache.evict(Fqn.ROOT, true);

      //now inspect the loader.
      assertEquals(vA, loader.get(nodeA.getFqn()).get(k));
      assertEquals(vB, loader.get(nodeB.getFqn()).get(k));
      assertEquals(vC, loader.get(nodeC.getFqn()).get(k));
      assertEquals(vD, loader.get(nodeD.getFqn()).get(k));
      assertEquals(vE, loader.get(nodeE.getFqn()).get(k));

      assertNull(loader.get(old_C));
      assertNull(loader.get(old_D));
      assertNull(loader.get(old_E));

   }

   protected CacheLoaderConfig getSingleCacheLoaderConfig(boolean passivation, String preload, String cacheloaderClass, String properties, boolean async, boolean fetchPersistentState, boolean shared, boolean purgeOnStartup) throws Exception
   {
      String xml =
            "      <loaders passivation=\"" + passivation + "\" shared=\"" + shared + "\">\n" +
                  "         <preload>\n" +
                  "            <node fqn=\"" + preload + "\"/>\n" +
                  "         </preload>\n" +
                  "         <loader class=\"" + cacheloaderClass + "\" async=\"" + async + "\" fetchPersistentState=\"" + fetchPersistentState + "\"\n" +
                  "                     purgeOnStartup=\"" + purgeOnStartup + "\">\n" +
                  "            <properties>\n" +
                  properties +
                  "            </properties>\n" +
                  "         </loader>\n" +
                  "      </loaders>";
      Element element = XmlConfigHelper.stringToElementInCoreNS(xml);
      LoadersElementParser elementParser = new LoadersElementParser();
      return elementParser.parseLoadersElement(element);
   }
}
