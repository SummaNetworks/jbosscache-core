package org.jboss.cache.api.mvcc.repeatable_read;

import org.jboss.cache.api.mvcc.LockTestBase;
import org.testng.annotations.Test;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import org.jboss.cache.Cache;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.repeatable_read.RepeatableReadLockTest")
public class RepeatableReadLockTest extends LockTestBase
{
   public RepeatableReadLockTest()
   {
      repeatableRead = true;
   }

   public void testRepeatableReadWithRemove() throws Exception
   {
      cache.put(AB, "k", "v");

      tm.begin();
      assert cache.getNode(AB) != null;
      Transaction reader = tm.suspend();

      tm.begin();
      assert cache.removeNode(AB);
      assert cache.getNode(AB) == null;
      tm.commit();

      assert cache.getNode(AB) == null;

      tm.resume(reader);
      assert cache.getNode(AB) != null;
      assert "v".equals(cache.get(AB, "k"));
      tm.commit();

      assert cache.getNode(AB) == null;
      assertNoLocks();
   }

   public void testRepeatableReadWithEvict() throws Exception
   {
      cache.put(AB, "k", "v");

      tm.begin();
      assert cache.getNode(AB) != null;
      Transaction reader = tm.suspend();

      tm.begin();
      cache.evict(AB);
      assert cache.getNode(AB) == null;
      tm.commit();

      assert cache.getNode(AB) == null;

      tm.resume(reader);
      assert cache.getNode(AB) != null;
      assert "v".equals(cache.get(AB, "k"));
      tm.commit();

      assert cache.getNode(AB) == null;
      assertNoLocks();
   }

   public void testRepeatableReadWithNull() throws Exception
   {
      assert cache.getNode(AB) == null;

      tm.begin();
      assert cache.getNode(AB) == null;
      Transaction reader = tm.suspend();

      tm.begin();
      cache.put(AB, "k", "v");
      assert cache.getNode(AB) != null;
      assert "v".equals(cache.get(AB, "k"));
      tm.commit();

      assert cache.getNode(AB) != null;
      assert "v".equals(cache.get(AB, "k"));

      tm.resume(reader);
      assert cache.getNode(AB) == null;
      assert cache.get(AB, "k") == null;
      tm.commit();

      assert cache.getNode(AB) != null;
      assert "v".equals(cache.get(AB, "k"));
      assertNoLocks();
   }
}
