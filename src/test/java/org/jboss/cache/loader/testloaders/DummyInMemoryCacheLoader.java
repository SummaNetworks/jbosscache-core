/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader.testloaders;

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.Modification;
import org.jboss.cache.loader.AbstractCacheLoader;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dummy cache loader that stores data in memory
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
@ThreadSafe
public class DummyInMemoryCacheLoader extends AbstractCacheLoader
{

   private static Log log = LogFactory.getLog(DummyInMemoryCacheLoader.class);
   // Do NOT access this map directly.  always use getNodesMap() since it may be overridden.
   protected Map<Fqn, DummyNode> nodes = new ConcurrentHashMap<Fqn, DummyNode>();
   protected Map<Object, List<Modification>> transactions = new ConcurrentHashMap<Object, List<Modification>>();
   protected boolean debug; // whether to dump System.out messages as well as log messages or not
   protected final Object NULL = new Object()
   {
      @Override
      public String toString()
      {
         return "NULL placeholder";
      }
   };
   protected IndividualCacheLoaderConfig config;

   public void setConfig(IndividualCacheLoaderConfig config)
   {
      this.config = config;
      if (config != null && config.getProperties() != null)
      {
         debug = Boolean.parseBoolean(config.getProperties().getProperty("debug", "false"));
      }
   }

   public IndividualCacheLoaderConfig getConfig()
   {
      return config;
   }

   public Set<?> getChildrenNames(Fqn fqn) throws Exception
   {
      debugMessage("Calling getChildrenNames on Fqn " + fqn);
      if (!getNodesMap().containsKey(fqn))
      {
         log.debug("node not in loader");
         debugMessage("node not in loader");
         return null;
      }

      Set children = findChildren(fqn);
      log.debug("Fqn " + fqn + " has children " + children);
      debugMessage("Fqn " + fqn + " has children " + children);
      // to keep in line with the CacheLoader interface contract for this method.
      return children.size() == 0 ? null : children;
   }

   private Set<Object> findChildren(Fqn p)
   {
      Set<Object> c = new HashSet<Object>();
      for (Fqn f : getNodesMap().keySet())
      {
         if (!f.isRoot() && f.getParent().equals(p))
         {
            c.add(f.getLastElement());
         }
      }
      return c;
   }

   public Map<Object, Object> get(Fqn name) throws Exception
   {
      DummyNode dn = getNodesMap().get(name);
      Map<Object, Object> d = dn != null ? dn.data : null;

      debugMessage("Getting data for fqn " + name + " = " + d);
      return stripNULLs(d);
   }

   private Map<Object, Object> stripNULLs(Map<Object, Object> data)
   {
      if (data == null) return null;
      // otherwise make sure we replace NULL placeholders with nulls.
      Map<Object, Object> d = new HashMap<Object, Object>(data);
      if (d.containsKey(NULL))
      {
         Object v = d.remove(NULL);
         d.put(null, v);
      }
      Set<Object> keys = new HashSet<Object>();
      for (Map.Entry<Object, Object> e : d.entrySet())
      {
         if (e.getValue() == NULL)
         {
            keys.add(e.getKey());
         }
      }
      for (Object k : keys)
      {
         d.put(k, null);
      }
      return d;
   }

   private Map<Object, Object> injectNULLs(Map<Object, Object> data)
   {
      if (data == null) return null;
      // otherwise make sure we replace NULL placeholders with nulls.
      Map<Object, Object> d = new HashMap<Object, Object>(data);
      if (d.containsKey(null))
      {
         Object v = d.remove(null);
         d.put(NULL, v);
      }
      Set<Object> keys = new HashSet<Object>();
      for (Map.Entry<?, ?> e : d.entrySet())
      {
         if (e.getValue() == null)
         {
            keys.add(e.getKey());
         }
      }
      for (Object k : keys)
      {
         d.put(k, NULL);
      }
      return d;
   }


   public boolean exists(Fqn name) throws Exception
   {
      debugMessage("Performing exists() on " + name);
      return getNodesMap().containsKey(name == null ? NULL : name);
   }

   public Object put(Fqn name, Object key, Object value) throws Exception
   {
      DummyNode n = getNodesMap().get(name);
      if (n == null)
      {
         n = new DummyNode(name);
      }
      Object k = key == null ? NULL : key;
      Object v = value == null ? NULL : value;
      Object old = n.data.put(k, v);

      getNodesMap().put(name, n);
      // we need to make sure parents get put in as well.
      recursivelyPutParentsIfNeeded(name);
      if (log.isDebugEnabled()) log.debug("Did a put on " + name + ", data is " + n.data);
      debugMessage("Did a put on " + name + ", data is " + n.data);
      return old == NULL ? null : old;
   }

   public void put(Fqn name, Map<Object, Object> attributes) throws Exception
   {
      DummyNode n = getNodesMap().get(name);
      if (n == null)
      {
         n = new DummyNode(name);
      }
      n.data.clear(); // emulate cache loaders overwriting any internal data map with new data map passed in.
      if (attributes != null) n.data.putAll(injectNULLs(attributes));
      getNodesMap().put(name, n);
      // we need to make sure parents get put in as well.
      recursivelyPutParentsIfNeeded(name);
      if (log.isDebugEnabled()) log.debug("Did a put on " + name + ", data is " + n.data);
      debugMessage("Did a put on " + name + ", data is " + n.data);
   }

   private void recursivelyPutParentsIfNeeded(Fqn node)
   {
      Fqn parent = node.getParent();
      if (getNodesMap().containsKey(parent)) return; // nothing to do.

      // else put the parent in.
      getNodesMap().put(parent, new DummyNode(parent));
      recursivelyPutParentsIfNeeded(parent);
   }

   public Object remove(Fqn fqn, Object key) throws Exception
   {
      log.debug("Removing data from " + fqn);
      debugMessage("Removing data from " + fqn);
      DummyNode n = getNodesMap().get(fqn);
      if (n == null) n = new DummyNode(fqn);
      Object old = n.data.remove(key == null ? NULL : key);
      getNodesMap().put(fqn, n);
      return old == NULL ? null : old;
   }

   public void remove(Fqn fqn) throws Exception
   {
      log.debug("Removing fqn " + fqn);
      debugMessage("Removing fqn " + fqn);
      getNodesMap().remove(fqn);
      // remove children.
      recursivelyRemoveChildren(fqn);
   }

   private void recursivelyRemoveChildren(Fqn removedParent)
   {
      for (Fqn f : getNodesMap().keySet())
      {
         if (f.getParent().equals(removedParent))
         {
            // remove the child node too
            getNodesMap().remove(f);
            // and it's children.  Depth first.
            recursivelyRemoveChildren(f);
         }
      }
   }

   public void removeData(Fqn fqn) throws Exception
   {
      log.debug("Removing data from " + fqn);
      debugMessage("Removing data from " + fqn);
      DummyNode n = getNodesMap().get(fqn);
      if (n == null) n = new DummyNode(fqn);
      n.data.clear();
      getNodesMap().put(fqn, n);
   }

   public class DummyNode
   {
      Map<Object, Object> data = new ConcurrentHashMap<Object, Object>();
      Fqn fqn;

      public DummyNode(Fqn fqn)
      {
         this.fqn = fqn;
      }

      @Override
      public String toString()
      {
         return "Node{" +
               "data=" + data +
               ", fqn=" + fqn +
               '}';
      }
   }

   @Override
   public String toString()
   {
      return "DummyInMemoryCacheLoader{" +
            "getNodesMap()=" + getNodesMap() +
            '}';
   }

   protected void debugMessage(String msg)
   {
      if (log.isTraceEnabled()) log.trace(msg);
   }

   /**
    * ALWAYS use this method instead of accessing the node map directly as it may be overridden.
    */
   protected Map<Fqn, DummyNode> getNodesMap()
   {
      return nodes;
   }

   public void wipe()
   {
      nodes.clear();
   }  
}
