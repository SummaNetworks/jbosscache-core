package org.jboss.cache.transaction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Fqn;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import org.testng.annotations.Test;

import javax.transaction.RollbackException;
import javax.transaction.TransactionManager;

@Test(groups = "functional", testName = "transaction.MarkAsRollbackTest")
public class MarkAsRollbackTest
{
   private static final Fqn fqn = Fqn.fromString("/a/b/c");
   private static final Log log = LogFactory.getLog(MarkAsRollbackTest.class);

   public void testMarkAsRollbackAfterMods() throws Exception
   {
      Configuration c = new Configuration();
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      CacheSPI<String, String> cache = (CacheSPI<String, String>) new UnitTestCacheFactory<String, String>().createCache(c, MarkAsRollbackTest.class);
      try
      {
         TransactionManager tm = cache.getTransactionManager();
         assert tm != null;
         tm.begin();
         cache.put(fqn, "k", "v");
         assert cache.get(fqn, "k").equals("v");
         assert cache.getRoot().getChildren().size() == 1;
         tm.setRollbackOnly();
         try
         {
            tm.commit();
            assert false : "Should have rolled back";
         }
         catch (RollbackException expected)
         {
         }

         assert tm.getTransaction() == null : "There should be no transaction in scope anymore!";
         assert cache.get(fqn, "k") == null : "Expected a null but was " + cache.get(fqn, "k");
         assert cache.getRoot().getChildren().size() == 0;
      }
      finally
      {
         log.warn("Cleaning up");
         TestingUtil.killCaches(cache);
      }
   }
   
   public void testMarkAsRollbackBeforeMods() throws Exception
   {
      Configuration c = new Configuration();
      c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      CacheSPI<String, String> cache = (CacheSPI<String, String>) new UnitTestCacheFactory<String, String>().createCache(c, MarkAsRollbackTest.class);
      try
      {
         TransactionManager tm = cache.getTransactionManager();
         assert tm != null;
         tm.begin();
         tm.setRollbackOnly();
         try
         {
            cache.put(fqn, "k", "v");
            assert false : "Should have failed";
         }
         catch (CacheException expected)
         {
        	 assert expected.getCause() instanceof RollbackException : "exception wrapped and thrown should be RollbackException. Exception class "+expected.getCause().getClass();
         }
         try
         {
            tm.commit();
            assert false : "Should have rolled back";
         }
         catch (RollbackException expected)
         {

         }

         assert tm.getTransaction() == null : "There should be no transaction in scope anymore!";
         assert cache.get(fqn, "k") == null : "Expected a null but was " + cache.get(fqn, "k");
         assert cache.getRoot().getChildren().size() == 0;
      }
      finally
      {
         log.warn("Cleaning up");
         TestingUtil.killCaches(cache);
      }
   }
}
