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
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNull;

@Test(groups = {"functional", "jgroups"}, testName = "options.cachemodelocal.AsyncReplOptLocksTest")
public class AsyncReplOptLocksTest extends CacheModeLocalTestBase
{
   public AsyncReplOptLocksTest()
   {
      cacheMode = Configuration.CacheMode.REPL_ASYNC;
      nodeLockingScheme = "OPTIMISTIC";
   }
}
