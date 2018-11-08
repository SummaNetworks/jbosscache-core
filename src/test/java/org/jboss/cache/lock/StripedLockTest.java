package org.jboss.cache.lock;

import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jboss.cache.Fqn;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = { "functional" }, testName = "lock.StripedLockTest")
public class StripedLockTest
{
   private StripedLock stripedLock;

   @BeforeMethod(alwaysRun = true)
   public void setUp()
   {
      stripedLock = new StripedLock();
   }

   public void testHashingDistribution()
   {
      // ensure even bucket distribution of lock stripes
      List<Fqn> fqns = createRandomFqns(1000);

      Map<ReentrantReadWriteLock, Integer> distribution = new HashMap<ReentrantReadWriteLock, Integer>();

      for (Fqn f : fqns)
      {
         ReentrantReadWriteLock lock = stripedLock.getLock(f);
         if (distribution.containsKey(lock))
         {
            int count = distribution.get(lock) + 1;
            distribution.put(lock, count);
         }
         else
         {
            distribution.put(lock, 1);
         }
      }

      assertTrue(distribution.size() <= stripedLock.sharedLocks.length);
      // assume at least a 2/3rd spread
      assertTrue(distribution.size() * 1.5 >= stripedLock.sharedLocks.length);
   }

   private List<Fqn> createRandomFqns(int number)
   {
      List<Fqn> f = new ArrayList<Fqn>(number);
      Random r = new Random();

      while (f.size() < number)
      {
         Fqn fqn = Fqn.fromString("/" + ((char) (65 + r.nextInt(26))) + "/" + ((char) (65 + r.nextInt(26))) + "/" + ((char) (65 + r.nextInt(26))));
         f.add(fqn);
      }
      return f;
   }
}
