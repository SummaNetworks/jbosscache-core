package org.jboss.cache.mock;

import org.jboss.cache.CacheSPI;
import org.jboss.cache.DataContainer;
import org.jboss.cache.Fqn;
import org.jboss.cache.InternalNode;
import org.jboss.cache.InvocationContext;
import org.jboss.cache.Node;
import org.jboss.cache.NodeSPI;
import org.jboss.cache.lock.NodeLock;
import org.jboss.cache.optimistic.DataVersion;
import org.jboss.cache.transaction.GlobalTransaction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class NodeSpiMock implements NodeSPI
{

   boolean isChildrenLoaded;
   boolean isDataLoaded;
   Map<Object, NodeSpiMock> children = new HashMap<Object, NodeSpiMock>();
   boolean isDeleted = false;
   boolean isValid = true;
   DataVersion version = null;
   Map data = new HashMap();
   NodeSpiMock parent;
   Fqn fqn;
   private boolean isResident = false;

   public NodeSpiMock(Fqn fqn)
   {
      this.fqn = fqn;
   }

   public boolean isChildrenLoaded()
   {
      return isChildrenLoaded;
   }

   public void setChildrenLoaded(boolean loaded)
   {
      this.isChildrenLoaded = loaded;
   }

   public boolean isDataLoaded()
   {
      return isDataLoaded;
   }

   public void setDataLoaded(boolean dataLoaded)
   {
      this.isDataLoaded = dataLoaded;
   }

   public Map getChildrenMapDirect()
   {
      return children;
   }

   public void setChildrenMapDirect(Map children)
   {
      this.children = new HashMap(children);
   }

   public NodeSPI getOrCreateChild(Object name, GlobalTransaction tx)
   {
      if (children.containsKey(name)) return children.get(name);
      NodeSpiMock child = newChild(name);
      return child;
   }

   private NodeSpiMock newChild(Object name)
   {
      NodeSpiMock child = new NodeSpiMock(Fqn.fromRelativeElements(fqn, name));
      child.parent = this;
      children.put(name, child);
      return child;
   }

   public NodeLock getLock()
   {
      throw new UnsupportedOperationException();
   }

   public void setFqn(Fqn f)
   {
      throw new UnsupportedOperationException();
   }

   public boolean isDeleted()
   {
      return isDeleted;
   }

   public void markAsDeleted(boolean marker)
   {
      this.isDeleted = marker;
   }

   public void markAsDeleted(boolean marker, boolean recursive)
   {
      this.isDeleted = marker;
      if (recursive)
      {
         for (NodeSpiMock child : children.values())
         {
            child.markAsDeleted(marker, true);
         }
      }
   }

   public void addChild(Object nodeName, Node nodeToAdd)
   {
      children.put(nodeName, (NodeSpiMock) nodeToAdd);
      ((NodeSpiMock) nodeToAdd).parent = this;
      ((NodeSpiMock) nodeToAdd).fqn = Fqn.fromRelativeElements(fqn, nodeName);
   }

   public void printDetails(StringBuilder sb, int indent)
   {
      //skip
   }

   public void print(StringBuilder sb, int indent)
   {
      //skip
   }

   public void setVersion(DataVersion version)
   {
      this.version = version;
   }

   public DataVersion getVersion()
   {
      return version;
   }

   public Set getChildrenDirect()
   {
      return new HashSet(children.values());
   }

   public void removeChildrenDirect()
   {
      children.clear();
   }

   public Set getChildrenDirect(boolean includeMarkedAsDeleted)
   {
      Set result = new HashSet();
      for (NodeSpiMock child : children.values())
      {
         if (!includeMarkedAsDeleted && child.isDeleted()) continue;
         result.add(child);
      }
      return result;
   }

   public NodeSPI getChildDirect(Object childName)
   {
      return children.get(childName);
   }

   public NodeSPI addChildDirect(Fqn childName)
   {
      if (childName.size() == 0) return this;
      Object directChildName = childName.get(0);
      NodeSpiMock directChild = children.get(directChildName);
      Fqn subFqn = childName.getSubFqn(1, childName.size());
      if (directChild == null)
      {
         directChild = newChild(directChildName);
      }
      return directChild.addChildDirect(subFqn);
   }

   public NodeSPI addChildDirect(Fqn f, boolean notify)
   {
      return addChildDirect(f);
   }

   public NodeSPI addChildDirect(Object childName, boolean notify)
   {
      return newChild(childName);
   }

   public void addChildDirect(NodeSPI child)
   {
      throw new UnsupportedOperationException();
   }

   public NodeSPI getChildDirect(Fqn childName)
   {
      return children.get(childName.getLastElement());
   }

   public boolean removeChildDirect(Fqn fqn)
   {
      throw new UnsupportedOperationException();
   }

   public boolean removeChildDirect(Object childName)
   {
      return children.remove(childName) != null;
   }

   public Object removeDirect(Object key)
   {
      return data.remove(key);
   }

   public Object putDirect(Object key, Object value)
   {
      return data.put(key, value);
   }

   public void putAllDirect(Map data)
   {
      this.data.putAll(data);
   }

   public Map getDataDirect()
   {
      return data;
   }

   public Object getDirect(Object key)
   {
      return data.get(key);
   }

   public boolean containsKeyDirect(Object key)
   {
      return data != null && data.containsKey(key);
   }

   public void clearDataDirect()
   {
      data.clear();
   }

   public Set getKeysDirect()
   {
      return data.keySet();
   }

   public Set getChildrenNamesDirect()
   {
      return new HashSet(children.keySet());
   }

   public CacheSPI getCache()
   {
      throw new UnsupportedOperationException();
   }

   public NodeSPI getParentDirect()
   {
      return parent;
   }

   public Node getParent()
   {
      return parent;
   }

   public boolean hasChildrenDirect()
   {
      return !children.isEmpty();
   }

   public Map getInternalState(boolean onlyInternalState)
   {
      throw new UnsupportedOperationException();
   }

   public void setInternalState(Map state)
   {
      throw new UnsupportedOperationException();
   }

   public void setValid(boolean valid, boolean recursive)
   {
      this.isValid = valid;
      if (recursive)
      {
         for (NodeSpiMock child : children.values())
         {
            child.setValid(valid, true);
            child.isValid = valid;
         }
      }
   }

   public boolean isNullNode()
   {
      throw new UnsupportedOperationException();
   }

   public void markForUpdate(DataContainer container, boolean writeSkewCheck)
   {
      throw new UnsupportedOperationException();
   }

   public void commitUpdate(InvocationContext ctx, DataContainer container)
   {
      throw new UnsupportedOperationException();
   }

   public boolean isChanged()
   {
      throw new UnsupportedOperationException();
   }

   public boolean isCreated()
   {
      throw new UnsupportedOperationException();
   }

   public InternalNode getDelegationTarget()
   {
      throw new UnsupportedOperationException();
   }

   public void setCreated(boolean created)
   {
      throw new UnsupportedOperationException();
   }

   public void rollbackUpdate()
   {
      throw new UnsupportedOperationException();
   }

   public Set getChildren()
   {
      return getChildrenDirect();
   }

   public Set getChildrenNames()
   {
      return getChildrenNamesDirect();
   }

   public Map getData()
   {
      return getDataDirect();
   }

   public Set getKeys()
   {
      return getKeysDirect();
   }

   public Fqn getFqn()
   {
      return fqn;
   }

   public Node addChild(Fqn f)
   {
      return addChildDirect(f);
   }

   public boolean removeChild(Fqn f)
   {
      return removeChildDirect(f);
   }

   public boolean removeChild(Object childName)
   {
      return removeChildDirect(childName);
   }

   public Node getChild(Fqn f)
   {
      return getChildDirect(f);
   }

   public Node getChild(Object name)
   {
      return getChildDirect(name);
   }

   public Object put(Object key, Object value)
   {
      return putDirect(key, value);
   }

   public Object putIfAbsent(Object key, Object value)
   {
      if (data.containsKey(key)) return data.get(key);
      return data.put(key, value);
   }

   public Object replace(Object key, Object value)
   {
      return data.put(key, value);
   }

   public boolean replace(Object key, Object oldValue, Object newValue)
   {
      if (data.get(key).equals(oldValue))
      {
         data.put(key, newValue);
         return true;
      }
      else
         return false;
   }

   public void putAll(Map map)
   {
      putAllDirect(map);
   }

   public void replaceAll(Map map)
   {
      data = map;
   }

   public Object get(Object key)
   {
      return getDirect(key);
   }

   public Object remove(Object key)
   {
      return removeDirect(key);
   }

   public void clearData()
   {
      clearDataDirect();
   }

   public int dataSize()
   {
      return data.size();
   }

   public boolean hasChild(Fqn f)
   {
      NodeSpiMock directChild = children.get(fqn.getLastElement());
      return directChild != null && (fqn.size() == 1 || directChild.hasChild(f.getSubFqn(1, f.size())));
   }

   public boolean hasChild(Object o)
   {
      return children.containsKey(o);
   }

   public boolean isValid()
   {
      return isValid;
   }

   public boolean isResident()
   {
      return isResident;
   }

   public void setResident(boolean resident)
   {
      this.isResident = resident;
   }

   public boolean isLockForChildInsertRemove()
   {
      return false;
   }

   public void setLockForChildInsertRemove(boolean lockForChildInsertRemove)
   {
   }

   public void releaseObjectReferences(boolean recursive)
   {
   }

   public boolean isLeaf()
   {
      return false;
   }
}
