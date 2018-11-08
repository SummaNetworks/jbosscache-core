/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options.cachemodelocal;

import org.jboss.cache.config.Configuration;
import org.jboss.cache.util.internals.replicationlisteners.ReplicationListener;
import org.jboss.cache.commands.ReplicableCommand;
import org.jboss.cache.commands.legacy.write.PessPutDataMapCommand;
import org.jboss.cache.commands.legacy.write.PessPutKeyValueCommand;
import org.jboss.cache.commands.legacy.write.PessRemoveKeyCommand;
import org.jboss.cache.commands.legacy.write.PessRemoveNodeCommand;
import org.jboss.cache.commands.write.PutDataMapCommand;
import org.jboss.cache.commands.write.PutKeyValueCommand;
import org.jboss.cache.commands.write.RemoveKeyCommand;
import org.jboss.cache.commands.write.RemoveNodeCommand;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Cache;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.HashMap;

@Test(groups = {"functional", "jgroups"}, testName = "options.cachemodelocal.AsyncReplPessLocksTest")
public class AsyncReplPessLocksTest extends CacheModeLocalTestBase
{
   ReplicationListener current;

   Map<Cache, ReplicationListener> cache2Listener = new HashMap<Cache, ReplicationListener>(2);

   public AsyncReplPessLocksTest()
   {
      cacheMode = Configuration.CacheMode.REPL_ASYNC;
      nodeLockingScheme = "PESSIMISTIC";
   }
}
