/*
* JBoss, Home of Professional Open Source
* Copyright 2005, JBoss Inc., and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/

package org.jboss.cache.statetransfer;

import org.jboss.cache.CacheException;
import org.jboss.cache.CacheSPI;
import org.jboss.cache.Version;
import org.jboss.cache.config.Configuration.CacheMode;
import org.jboss.cache.factories.ComponentRegistry;
import org.jboss.cache.factories.UnitTestConfigurationFactory;
import org.jboss.cache.factories.annotations.NonVolatile;
import org.jboss.cache.lock.TimeoutException;
import org.jboss.cache.remoting.jgroups.ChannelMessageListener;
import org.jboss.cache.util.TestingUtil;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.util.Map;
import org.jboss.cache.Cache;
import org.jboss.cache.UnitTestCacheFactory;
import org.jboss.cache.config.Configuration;

/**
 * A FailedStateTransferTest.
 *
 * @author Brian Stansberry
 * @version $Revision: 7646 $
 */
@Test(groups = {"functional"}, testName = "statetransfer.FailedStateTransferTest")
public class FailedStateTransferTest extends StateTransferTestBase
{
   public void testFailedStateTransfer() throws Exception
   {
      Configuration c = UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_ASYNC);
      //c.setClusterName("VersionedTestBase");
      c.setReplVersionString(getReplicationVersion());
      // Use a long timeout to facilitate setting debugger breakpoints
      c.setStateRetrievalTimeout(60000);
      CacheSPI cache = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      // Put the cache in the map before starting, so if it fails in
      // start it can still be destroyed later
      caches.put("secretive", cache);

      // inject our own message listener and re-wire deps
      ComponentRegistry cr = TestingUtil.extractComponentRegistry(cache);
//      cr.unregisterComponent(ChannelMessageListener.class);
      cr.registerComponent(new SecretiveStateCacheMessageListener(), ChannelMessageListener.class);
//      cr.updateDependencies();

      cache.start();


      c= UnitTestConfigurationFactory.createConfiguration(CacheMode.REPL_ASYNC);
      //c.setClusterName("VersionedTestBase");
      c.setReplVersionString(getReplicationVersion());
      // Use a long timeout to facilitate setting debugger breakpoints
      c.setStateRetrievalTimeout(60000);
      CacheSPI recipient = (CacheSPI) new UnitTestCacheFactory<Object, Object>().createCache(c, false, getClass());

      //Put the cache in the map before starting, so if it fails in
      // start it can still be destroyed later
      caches.put("secretive2", recipient);

      // inject our own message listener and re-wire deps
      cr = TestingUtil.extractComponentRegistry(recipient);
      cr.registerComponent(new SecretiveStateCacheMessageListener(), ChannelMessageListener.class);

      try
      {
         recipient.start();
         fail("start() should throw an exception");
      }
      catch (CacheException good)
      {
         // this is what we want
      }    
   }

   protected String getReplicationVersion()
   {
      return Version.version;
   }

   @NonVolatile
   private static class SecretiveStateCacheMessageListener extends ChannelMessageListener
   {
      @Override
      public void setState(byte[] new_state)
      {
         setStateException = new TimeoutException("Planned Timeout");
      }

      @Override
      public void setState(InputStream istream)
      {
         setStateException = new TimeoutException("Planned Timeout");
      }

      @Override
      public void setState(String state_id, byte[] state)
      {
         setStateException = new TimeoutException("Planned Timeout");
      }

      @Override
      public void setState(String state_id, InputStream istream)
      {
         setStateException = new TimeoutException("Planned Timeout");
      }
   }
}
