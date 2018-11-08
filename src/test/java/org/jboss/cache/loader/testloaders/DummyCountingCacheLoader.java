/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.loader.testloaders;

import org.jboss.cache.Fqn;
import org.jboss.cache.Modification;
import org.jboss.cache.loader.AbstractCacheLoader;
import org.jboss.cache.config.CacheLoaderConfig.IndividualCacheLoaderConfig;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dummy cache loader that captures the number of times each method is called.  Stores statistics statically, mimicking
 * a shared cache loader.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani (manik AT jboss DOT org)</a>
 */
public class DummyCountingCacheLoader extends AbstractCacheLoader
{
   private static int getChildrenNamesCount = 0, getCount = 0, putCount = 0, existsCount = 0, removeCount = 0;

   public int getGetChildrenNamesCount()
   {
      return getChildrenNamesCount;
   }

   public int getGetCount()
   {
      return getCount;
   }

   public int getPutCount()
   {
      return putCount;
   }

   public int getExistsCount()
   {
      return existsCount;
   }

   public int getRemoveCount()
   {
      return removeCount;
   }


   /**
    * Sets the configuration. Will be called before {@link #create()} and {@link #start()}
    */
   public void setConfig(IndividualCacheLoaderConfig config)
   {
   }

   public IndividualCacheLoaderConfig getConfig()
   {
      return null;
   }

   /**
    * Returns a list of children names, all names are <em>relative</em>. Returns null if the parent node is not found.
    * The returned set must not be modified, e.g. use Collections.unmodifiableSet(s) to return the result
    *
    * @param fqn The FQN of the parent
    * @return Set<String>. A list of children. Returns null if no children nodes are present, or the parent is
    *         not present
    */
   public Set<String> getChildrenNames(Fqn fqn) throws Exception
   {
      getChildrenNamesCount++;
      return null;
   }

   /**
    * Returns the value for a given key. Returns null if the node doesn't exist, or the value is not bound
    *
    * @param name
    * @return
    * @throws Exception
    */
   public Object get(Fqn name, Object key) throws Exception
   {
      getCount++;
      return null;
   }

   /**
    * Returns all keys and values from the persistent store, given a fully qualified name
    *
    * @param name
    * @return Map<Object,Object> of keys and values for the given node. Returns null if the node was not found, or
    *         if the node has no attributes
    * @throws Exception
    */
   public Map<Object, Object> get(Fqn name) throws Exception
   {
      getCount++;
      return null;
   }

   /**
    * Checks whether the CacheLoader has a node with Fqn
    *
    * @param name
    * @return True if node exists, false otherwise
    */
   public boolean exists(Fqn name) throws Exception
   {
      existsCount++;
      return false;
   }

   /**
    * Inserts key and value into the attributes hashmap of the given node. If the node does not exist, all
    * parent nodes from the root down are created automatically. Returns the old value
    */
   public Object put(Fqn name, Object key, Object value) throws Exception
   {
      putCount++;
      return null;
   }

   /**
    * Inserts all elements of attributes into the attributes hashmap of the given node, overwriting existing
    * attributes, but not clearing the existing hashmap before insertion (making it a union of existing and
    * new attributes)
    * If the node does not exist, all parent nodes from the root down are created automatically
    *
    * @param name       The fully qualified name of the node
    * @param attributes A Map of attributes. Can be null
    */
   public void put(Fqn name, Map attributes) throws Exception
   {
      putCount++;
   }

   /**
    * Inserts all modifications to the backend store. Overwrite whatever is already in
    * the datastore.
    *
    * @param modifications A List<Modification> of modifications
    * @throws Exception
    */
   public void put(List<Modification> modifications) throws Exception
   {
      putCount++;
   }

   /**
    * Removes the given key and value from the attributes of the given node. No-op if node doesn't exist
    */
   public Object remove(Fqn name, Object key) throws Exception
   {
      removeCount++;
      return null;
   }

   /**
    * Removes the given node. If the node is the root of a subtree, this will recursively remove all subnodes,
    * depth-first
    */
   public void remove(Fqn name) throws Exception
   {
      removeCount++;
   }

   /**
    * Removes all attributes from a given node, but doesn't delete the node itself
    *
    * @param name
    * @throws Exception
    */
   public void removeData(Fqn name) throws Exception
   {
      removeCount++;
   }

   @Override
   public void loadEntireState(ObjectOutputStream os) throws Exception
   {
      //intentional no-op
   }

   @Override
   public void loadState(Fqn subtree, ObjectOutputStream os) throws Exception
   {
      // intentional no-op
   }

   @Override
   public void storeEntireState(ObjectInputStream is) throws Exception
   {
      // intentional no-op
   }

   @Override
   public void storeState(Fqn subtree, ObjectInputStream is) throws Exception
   {
      // intentional no-op
   }


   @Override
   public void destroy()
   {
      getChildrenNamesCount = 0;
      getCount = 0;
      putCount = 0;
      existsCount = 0;
      removeCount = 0;
   }

   public void scrubStats()
   {
      destroy();
   }
}