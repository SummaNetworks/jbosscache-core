package org.jboss.cache.integration.hibernate;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Cache;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import static org.jboss.cache.integration.hibernate.HibernateIntegrationTestUtil.*;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.assertEquals;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.Synchronization;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.util.HashSet;
import java.util.Set;

/**
 * Tests that mimic the Hibernate Second Level Cache UpdateTimestamps
 * use case.
 * <p>
 * FIXME: Make this much more closely duplicate the real Hibernate usage;
 * use a CacheManager with configs consistent with Hibernate, etc.
 * </p>
 *
 * @author Brian Stansberry
 */
@Test(groups = "integration", sequential = true, testName = "integration.hibernate.UpdateTimestampsCachingTest")
public class UpdateTimestampsCachingTest
{
   private static final Log log = LogFactory.getLog(UpdateTimestampsCachingTest.class);

   private static final Fqn ENTITY_TYPE_FQN = Fqn.fromString("/com/foo/MyEntity");

   private Set<Cache<String, Object>> caches;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      caches = new HashSet<Cache<String, Object>>();
   }

   @AfterMethod(alwaysRun = true)
   public void tearDown()
   {
      for (Cache<String, Object> cache : caches)
         TestingUtil.killCaches(cache);
      caches = null;
   }

   private Cache<String, Object> createCache(boolean optimistic)
   {
      UnitTestCacheFactory<String, Object> cf = new UnitTestCacheFactory<String, Object>();
      Cache<String, Object> cache = cf.createCache("configs/local-tx.xml", false, getClass());
      cache.getConfiguration().setNodeLockingScheme(optimistic ? Configuration.NodeLockingScheme.OPTIMISTIC : Configuration.NodeLockingScheme.PESSIMISTIC);
      cache.start();
      caches.add(cache);
      return cache;
   }

   public void testTimestampUpdateInAfterCompletionPessimistic() throws Exception
   {
      timestampUpdateInAfterCompletionTest(false);
   }

   public void testTimestampUpdateInAfterCompletionOptimistic() throws Exception
   {
      timestampUpdateInAfterCompletionTest(true);
   }

   /**
    * FIXME Make this use a cluster, not just a single cache
    *
    * @param optimistic
    * @throws Exception
    */
   private void timestampUpdateInAfterCompletionTest(boolean optimistic) throws Exception
   {
      Cache<String, Object> cache = createCache(optimistic);
      TransactionManager tm = cache.getConfiguration().getRuntimeConfig().getTransactionManager();

      tm.begin();
      Transaction tx = tm.getTransaction();
      UpdateTimestampsSynchronization sync = new UpdateTimestampsSynchronization(ENTITY_TYPE_FQN, cache, tm);
      tx.registerSynchronization(sync);

      Fqn fqn = Fqn.fromRelativeFqn(REGION_PREFIX_FQN, ENTITY_TYPE_FQN);
      fqn = Fqn.fromRelativeElements(fqn, "1");
      cache.put(fqn, ITEM, "value");

      tm.commit();

      fqn = Fqn.fromRelativeFqn(HibernateIntegrationTestUtil.TS_FQN, ENTITY_TYPE_FQN);
      assertEquals(sync.getTimestamp(), cache.get(fqn, ITEM));
   }

   private class UpdateTimestampsSynchronization implements Synchronization
   {
      private final Cache<String, Object> cache;
      private final Fqn entityType;
      private final TransactionManager tm;
      private Long timestamp;

      UpdateTimestampsSynchronization(Fqn entityType, Cache<String, Object> cache, TransactionManager tm)
      {
         this.entityType = entityType;
         this.cache = cache;
         this.tm = tm;
      }

      public Long getTimestamp()
      {
         return timestamp;
      }

      public void beforeCompletion()
      {
         // no-op
      }

      public void afterCompletion(int status)
      {
         Fqn fqn = Fqn.fromRelativeFqn(TS_FQN, entityType);
         try
         {
            timestamp = System.currentTimeMillis();

            Transaction tx = tm.suspend();
            cache.getInvocationContext().getOptionOverrides().setForceAsynchronous(true);
            cache.put(fqn, ITEM, timestamp);
            tm.resume(tx);
            log.info("Updated timestamp " + entityType);
         }
         catch (Exception e)
         {
            log.error("Problem updating timestamp " + entityType, e);
            throw new RuntimeException(e);
         }
      }
   }
}
