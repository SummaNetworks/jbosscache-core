/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options.cachemodelocal;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.TestingUtil;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.CacheSPI;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "options.cachemodelocal.AsyncInvalidationPessLocksTest")
public class AsyncInvalidationPessLocksTest extends CacheModeLocalTestBase
{
   public AsyncInvalidationPessLocksTest()
   {
      cacheMode = Configuration.CacheMode.INVALIDATION_ASYNC;
      nodeLockingScheme = "PESSIMISTIC";
      isInvalidation = true;
   }
}
