package org.jboss.cache.config.parsing;

import org.testng.annotations.Test;
import org.w3c.dom.Element;
import org.jboss.cache.config.CacheLoaderConfig;
import org.jboss.cache.config.parsing.element.LoadersElementParser;

import java.util.List;

/**
 * Tester class for {@link org.jboss.cache.config.parsing.element.LoadersElementParser}
 *
 * @author Mircea.Markus@jboss.com
 * @since 3.0
 */
@Test(groups = "unit", testName = "config.parsing.CacheLoadersElementParserTest")
public class CacheLoadersElementParserTest
{
   LoadersElementParser parser = new LoadersElementParser();


   public void simpleParse() throws Exception
   {
      String xmlStr = "      " +
            "      <loaders passivation=\"false\" shared=\"false\">\n" +
            "         <preload>\n" +
            "            <node fqn=\"/\"/>\n" +
            "         </preload>\n" +
            "         <loader class=\"org.jboss.cache.loader.JDBCCacheLoader\" async=\"true\" fetchPersistentState=\"false\"\n" +
            "                      ignoreModifications=\"false\" purgeOnStartup=\"false\">\n" +
            "            <properties>\n" +
            "               cache.jdbc.table.name=jbosscache\n" +
            "               cache.jdbc.table.create=true\n" +
            "               cache.jdbc.table.drop=true\n" +
            "            </properties>\n" +
            "         </loader>\n" +
            "      </loaders>";
      CacheLoaderConfig config = getCacheLoaderConfig(xmlStr);
      assert !config.isPassivation();
      assert !config.isShared();
      assert config.getPreload().equals("/");
      assert config.getFirstCacheLoaderConfig().getClassName().equals("org.jboss.cache.loader.JDBCCacheLoader");
      assert config.getFirstCacheLoaderConfig().isAsync();
      assert !config.getFirstCacheLoaderConfig().isFetchPersistentState();
      assert !config.getFirstCacheLoaderConfig().isIgnoreModifications();
      assert !config.getFirstCacheLoaderConfig().isPurgeOnStartup();
   }


   /**
    * Tests that if no values are specified the parser sets default config values.
    */
   public void testDefaultValues() throws Exception
   {
      String xmlStr = 
            "      <loaders passivation=\"false\">\n" +
            "         <preload/>\n" +
            "         <loader class=\"org.jboss.cache.loader.JDBCCacheLoader\">" +
            "             <singletonStore/>\n" +
            "          </loader>"+
            "      </loaders>";
      CacheLoaderConfig config = getCacheLoaderConfig(xmlStr);
      assert config.getPreload().equals("/") : "the default value for preload is root";
      assert !config.getFirstCacheLoaderConfig().isAsync() : "by default CL are sync";
      assert !config.isShared() : "by default the cl are not sared";
      assert !config.getFirstCacheLoaderConfig().isIgnoreModifications();
      assert !config.getFirstCacheLoaderConfig().isPurgeOnStartup();
      assert !config.getFirstCacheLoaderConfig().getSingletonStoreConfig().isSingletonStoreEnabled();
      assert config.getFirstCacheLoaderConfig().getSingletonStoreConfig().getSingletonStoreClass().equals("org.jboss.cache.loader.SingletonStoreCacheLoader");
      assert config.getFirstCacheLoaderConfig().getSingletonStoreConfig().getSingletonStoreClass().equals("org.jboss.cache.loader.SingletonStoreCacheLoader");
   }

   public void testMultiplePreloadNodes() throws Exception
   {
      String xmlStr = "      " +
            "      <loaders passivation=\"false\" shared=\"false\">\n" +
            "         <preload>\n" +
            "            <node fqn=\"/\"/>\n" +
            "            <node fqn=\"/a\"/>\n" +
            "            <node fqn=\"/a/b\"/>\n" +
            "         </preload>\n" +
            "         <loader class=\"org.jboss.cache.loader.JDBCCacheLoader\" async=\"true\" fetchPersistentState=\"false\"\n" +
            "                      ignoreModifications=\"false\" purgeOnStartup=\"false\">\n" +
            "         </loader>" +
            "</loaders>";
      CacheLoaderConfig config = getCacheLoaderConfig(xmlStr);
      assert config.getPreload().equals("/,/a,/a/b");
      assert config.getFirstCacheLoaderConfig().getSingletonStoreConfig() == null;
   }

   public void testMultipleCacheLoaders() throws Exception
   {
      String xml =
            "   <loaders passivation=\"false\" shared=\"false\">\n" +
            "      <preload/>\n" +
            "      <loader class=\"org.jboss.cache.loader.JDBCCacheLoader\" async=\"true\" fetchPersistentState=\"true\"\n" +
            "                   ignoreModifications=\"true\" purgeOnStartup=\"true\"/>\n" +
            "      <loader class=\"org.jboss.cache.loader.bdbje.BdbjeCacheLoader\" async=\"true\" fetchPersistentState=\"true\"\n" +
            "                   ignoreModifications=\"true\" purgeOnStartup=\"true\"/>\n" +
            "      <loader class=\"org.jboss.cache.loader.FileCacheLoader\" async=\"true\" fetchPersistentState=\"true\"\n" +
            "                   ignoreModifications=\"true\" purgeOnStartup=\"true\"/>\n" +
            "   </loaders>";
      CacheLoaderConfig clConfig = getCacheLoaderConfig(xml);
      List<CacheLoaderConfig.IndividualCacheLoaderConfig> indClConfigs = clConfig.getIndividualCacheLoaderConfigs();
      assert indClConfigs.size() == 3;
      assert indClConfigs.get(0).getClassName().equals("org.jboss.cache.loader.JDBCCacheLoader");
      assert indClConfigs.get(1).getClassName().equals("org.jboss.cache.loader.bdbje.BdbjeCacheLoader");
      assert indClConfigs.get(2).getClassName().equals("org.jboss.cache.loader.FileCacheLoader");

   }

   public void testSingletonStoreDisabled() throws Exception
   {
      String xml =
            "   <loaders passivation=\"true\" shared=\"true\">\n" +
            "      <preload/>\n" +
            "      <loader class=\"org.jboss.cache.loader.JDBCCacheLoader\" async=\"true\" fetchPersistentState=\"true\"\n" +
            "                   ignoreModifications=\"true\" purgeOnStartup=\"true\">\n" +
            "         <singletonStore enabled=\"false\" class=\"org.jboss.cache.loader.SingletonStoreCacheLoader\">\n" +
            "            <properties>\n" +
            "               pushStateWhenCoordinator=some\n" +
            "               pushStateWhenCoordinatorTimeout=cus\n" +
            "            </properties>\n" +
            "         </singletonStore>\n" +
            "      </loader>\n" +
            "   </loaders>";
      CacheLoaderConfig clc = getCacheLoaderConfig(xml);
      CacheLoaderConfig.IndividualCacheLoaderConfig icl = clc.getFirstCacheLoaderConfig();
      CacheLoaderConfig.IndividualCacheLoaderConfig.SingletonStoreConfig singletonStoreConfig = icl.getSingletonStoreConfig();
      assert singletonStoreConfig != null;
      assert !singletonStoreConfig.isSingletonStoreEnabled();
      assert singletonStoreConfig.getSingletonStoreClass().equals("org.jboss.cache.loader.SingletonStoreCacheLoader");
      assert singletonStoreConfig.getProperties().size() == 2;
      assert singletonStoreConfig.getProperties().get("pushStateWhenCoordinator").equals("some");
      assert singletonStoreConfig.getProperties().get("pushStateWhenCoordinatorTimeout").equals("cus");

   }
   
   private CacheLoaderConfig getCacheLoaderConfig(String xmlStr)
         throws Exception
   {
      Element element = XmlConfigHelper.stringToElementInCoreNS(xmlStr);
      return parser.parseLoadersElement(element);
   }
}
