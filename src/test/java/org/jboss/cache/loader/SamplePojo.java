package org.jboss.cache.loader;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sample object for testing.
 *
 * @author Bela Ban
 * @version $Id: SamplePojo.java 7168 2008-11-19 17:37:20Z jason.greene@jboss.com $
 */
public class SamplePojo implements Serializable {
   private static final long serialVersionUID = -8505492581349365824L;
   int age;
   String name;
   List<String> hobbies=new ArrayList<String>();

   public SamplePojo(int age, String name) {
      this.age=age;
      this.name=name;
   }

   public int getAge() {
      return age;
   }

   public void setAge(int age) {
      this.age=age;
   }

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name=name;
   }

   public List<String> getHobbies() {
      return hobbies;
   }

   public void setHobbies(List<String> hobbies) {
      this.hobbies=hobbies;
   }

   public String toString() {
      return "name=" + name + ", age=" + age + ", hobbies=" + hobbies;
   }

   public boolean equals(Object o)
   {
       if (!(o instanceof SamplePojo))
       {
          return false;
       }

       SamplePojo other = (SamplePojo) o;
       boolean equals = (name.equals(other.getName())) && (age == other.getAge()) && (hobbies.equals(other.getHobbies()));
       return equals;
   }

   public int hashCode()
   {
      return name.hashCode() ^ age ^ hobbies.hashCode();
   }

}
