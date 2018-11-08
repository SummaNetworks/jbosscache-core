package org.jboss.cache.options;

import org.jboss.cache.optimistic.DataVersion;

/**
 * Note that this uses STRING comparisons!!
 */
public class TestVersion implements DataVersion
{
   private static final long serialVersionUID = -5577530957664493161L;

   private String myVersion;

   public TestVersion(String version)
   {
      myVersion = version;
   }

   public String getInternalVersion()
   {
      return myVersion;
   }

   public void setInternalVersion(String version)
   {
      myVersion = version;
   }

   public boolean newerThan(DataVersion other)
   {
      if (other instanceof TestVersion)
      {
         return myVersion.compareTo(((TestVersion) other).getInternalVersion()) > 0;
      }
      else
      {
         throw new IllegalArgumentException("version type mismatch");
      }
   }

   @Override
   public String toString()
   {
      return "TestVersion-" + myVersion;
   }

   @Override
   public boolean equals(Object other)
   {
      if (other instanceof TestVersion)
      {
         TestVersion oVersion = (TestVersion) other;
         if (oVersion.myVersion == null && myVersion == null) return true;
         if (myVersion != null) return myVersion.equals(oVersion.myVersion);
      }
      return false;
   }
   
   @Override
   public int hashCode()
   {
      return myVersion.hashCode();
   }
   
}