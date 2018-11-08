/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options.cachemodelocal;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.CacheSPI;
import org.testng.annotations.Test;

@Test(groups = {"functional", "jgroups"}, testName = "options.cachemodelocal.SyncReplPessLocksTest")
public class SyncReplPessLocksTest extends CacheModeLocalTestBase
{
    public SyncReplPessLocksTest()
    {
        cacheMode = Configuration.CacheMode.REPL_SYNC;
        nodeLockingScheme = "PESSIMISTIC";
    }
}
