package org.jboss.cache.config.parsing;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests the {@link org.jboss.cache.config.parsing.CacheConfigsXmlParser}.
 *
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 3.0
 */
@Test(groups = "unit", testName = "config.parsing.CacheConfigsTest")
public class CacheConfigsTest
{
   public void testNewFormat() throws CloneNotSupportedException
   {
      String xml = "<cache-configs>\n" +
            "   <cache-config name=\"A\">\n" +
            "      <jbosscache  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "            xmlns=\"urn:jboss:jbosscache-core:config:3.1\">\n" +
            "         <locking isolationLevel=\"REPEATABLE_READ\" lockAcquisitionTimeout=\"15000\"/>\n" +
            "         <transaction transactionManagerLookupClass=\"org.jboss.cache.transaction.GenericTransactionManagerLookup\"/>\n" +
            "         <clustering><stateRetrieval timeout=\"20000\"/></clustering>\n" +
            "      </jbosscache>\n" +
            "   </cache-config>\n" +
            "\n" +
            "   <cache-config name=\"B\">\n" +
            "      <jbosscache  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "            xmlns=\"urn:jboss:jbosscache-core:config:3.1\">\n" +
            "         <locking isolationLevel=\"READ_COMMITTED\" lockAcquisitionTimeout=\"15000\"/>\n" +
            "         <transaction transactionManagerLookupClass=\"org.jboss.cache.transaction.GenericTransactionManagerLookup\"/>\n" +
            "         <clustering><stateRetrieval timeout=\"20000\"/></clustering>\n" +
            "      </jbosscache>\n" +
            "   </cache-config>\n" +
            "\n" +
            "   <cache-config name=\"C\">\n" +
            "      <jbosscache  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "            xmlns=\"urn:jboss:jbosscache-core:config:3.1\">\n" +
            "         <locking isolationLevel=\"READ_COMMITTED\" lockAcquisitionTimeout=\"100\"/>\n" +
            "         <clustering><stateRetrieval timeout=\"100\"/></clustering>\n" +
            "      </jbosscache>\n" +
            "   </cache-config>\n" +
            "</cache-configs>";

      ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes());

      CacheConfigsXmlParser ccxp = new CacheConfigsXmlParser();
      Map<String, Configuration> map = ccxp.parseConfigs(bais, null);
      Map toExpect = buildExpectedValues();

      assert map.equals(toExpect) : "Expected " + toExpect + " but was " + map;
   }

   public void testLegacyFormat() throws CloneNotSupportedException
   {
      String xml = "<cache-configs>\n" +
            "   <cache-config name=\"A\">\n" +
            "      <attribute name=\"IsolationLevel\">REPEATABLE_READ</attribute>\n" +
            "      <attribute name=\"LockAcquisitionTimeout\">15000</attribute>\n" +
            "      <attribute name=\"TransactionManagerLookupClass\">org.jboss.cache.transaction.GenericTransactionManagerLookup</attribute>\n" +
            "      <attribute name=\"StateRetrievalTimeout\">20000</attribute>\n" +
            "      <attribute name=\"CacheMode\">REPL_SYNC</attribute>"+
            "   </cache-config>\n" +
            "\n" +
            "   <cache-config name=\"B\">\n" +
            "         <attribute name=\"IsolationLevel\">READ_COMMITTED</attribute>\n" +
            "         <attribute name=\"LockAcquisitionTimeout\">15000</attribute>\n" +
            "         <attribute name=\"TransactionManagerLookupClass\">org.jboss.cache.transaction.GenericTransactionManagerLookup</attribute>\n" +
            "         <attribute name=\"StateRetrievalTimeout\">20000</attribute>\n" +
            "      <attribute name=\"CacheMode\">REPL_SYNC</attribute>"+
            "   </cache-config>\n" +
            "\n" +
            "   <cache-config name=\"C\">\n" +
            "         <attribute name=\"IsolationLevel\">READ_COMMITTED</attribute>\n" +
            "         <attribute name=\"LockAcquisitionTimeout\">100</attribute>\n" +
            "         <attribute name=\"StateRetrievalTimeout\">100</attribute>\n" +
            "      <attribute name=\"CacheMode\">REPL_SYNC</attribute>"+
            "   </cache-config>\n" +
            "</cache-configs>";

      ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes());

      CacheConfigsXmlParser ccxp = new CacheConfigsXmlParser();
      Map<String, Configuration> map = ccxp.parseConfigs(bais, null);
      Map toExpect = buildExpectedValues();

      assert map.equals(toExpect);
   }

   private Map<String, Configuration> buildExpectedValues()
   {
      Map<String, Configuration> map = new HashMap<String, Configuration>(3);
      Configuration cfg = new Configuration();
      map.put("A", cfg);
      cfg.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
      cfg.setLockAcquisitionTimeout(15000);
      cfg.setTransactionManagerLookupClass("org.jboss.cache.transaction.GenericTransactionManagerLookup");
      cfg.setStateRetrievalTimeout(20000);
      cfg.setCacheMode(CacheMode.REPL_SYNC);

      cfg = new Configuration();
      map.put("B", cfg);
      cfg.setIsolationLevel(IsolationLevel.READ_COMMITTED);
      cfg.setLockAcquisitionTimeout(15000);
      cfg.setTransactionManagerLookupClass("org.jboss.cache.transaction.GenericTransactionManagerLookup");
      cfg.setStateRetrievalTimeout(20000);
      cfg.setCacheMode(CacheMode.REPL_SYNC);

      cfg = new Configuration();
      map.put("C", cfg);
      cfg.setIsolationLevel(IsolationLevel.READ_COMMITTED);
      cfg.setLockAcquisitionTimeout(100);
      cfg.setStateRetrievalTimeout(100);
      cfg.setCacheMode(CacheMode.REPL_SYNC);

      return map;
   }
}
