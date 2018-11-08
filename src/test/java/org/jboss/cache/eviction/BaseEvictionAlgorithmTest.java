/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.jboss.cache.eviction;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.cache.Fqn;
import org.jboss.cache.Region;
import org.jboss.cache.RegionImpl;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.RegionRegistry;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.config.ConfigurationException;
import org.jboss.cache.config.EvictionAlgorithmConfig;
import org.jboss.cache.config.EvictionRegionConfig;
import static org.testng.AssertJUnit.fail;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests BaseEvictionAlgorithm class.
 *
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 */
@Test(groups = "functional", testName = "eviction.BaseEvictionAlgorithmTest")
public class BaseEvictionAlgorithmTest
{
   private static final Log log = LogFactory.getLog(BaseEvictionAlgorithmTest.class);

   private RegionManager regionManager;

   @BeforeMethod(alwaysRun = true)
   public void setUp() throws Exception
   {
      RegionManagerImpl rmi = new RegionManagerImpl();
      rmi.injectDependencies(null, new Configuration(), null, null, null, new RegionRegistry());
      regionManager = rmi;
   }

   public void testFillUpRecycleQueue() throws Exception
   {
      final int recycleQueueCapacity = 10;

      /* override recycle queue capacity to make the test shorter */
      BaseEvictionAlgorithm algorithm = new MockEvictionAlgorithm(recycleQueueCapacity);

      Region region = regionManager.getRegion("/a/b/c", true);
      region.setEvictionRegionConfig(new EvictionRegionConfig(region.getFqn(), new MockEvictionAlgorithmConfig()));

      for (int i = 0; i < (recycleQueueCapacity + 1); i++)
      {
         Fqn fqn = Fqn.fromString("/a/b/c/" + Integer.toString(i + 1));
         region.registerEvictionEvent(fqn, EvictionEvent.Type.ADD_NODE_EVENT);
      }

      ExecutorService executor = Executors.newSingleThreadExecutor();
      Future<Void> future = executor.submit(new ProcessEvictionRegion((RegionImpl) region, algorithm));

      try
      {
         future.get(20, TimeUnit.SECONDS);
      }
      catch (TimeoutException te)
      {
         log.error("Region eviction processing did not finish on time", te);
         fail("Region eviction processing should have finished by now, something is wrong. Recycle queue may have filled up.");
      }
      finally
      {
         log.info("recycle queue size: " + algorithm.recycleQueue.size());
      }
   }

   /**
    * Classes *
    */

   public static class MockEvictionAlgorithm extends BaseEvictionAlgorithm
   {
      private static MockEvictionAlgorithm singleton;

      private MockEvictionAlgorithm(int recycleQueueCapacity)
      {
         recycleQueue = new LinkedBlockingQueue<Fqn>(recycleQueueCapacity);
         singleton = this;
      }

      public static MockEvictionAlgorithm getInstance()
      {
         return singleton;
      }

      @Override
      protected EvictionQueue setupEvictionQueue() throws EvictionException
      {
         return new LRUQueue();
      }

      @Override
      protected boolean shouldEvictNode(NodeEntry ne)
      {
         /* all node entries need evicting */
         return true;
      }

      public Class<? extends EvictionAlgorithmConfig> getConfigurationClass()
      {
         return null;
      }
   }

   public static class MockEvictionAlgorithmConfig implements EvictionAlgorithmConfig
   {
      public void reset()
      {
         /* no op */
      }

      public String getEvictionAlgorithmClassName()
      {
         return MockEvictionAlgorithm.class.getName();
      }

      public void validate() throws ConfigurationException
      {
         /* no op */
      }

      public EvictionAlgorithmConfig clone() throws CloneNotSupportedException
      {
         return (EvictionAlgorithmConfig) super.clone();
      }
   }

   public class ProcessEvictionRegion implements Callable<Void>
   {
      private RegionImpl region;

      private EvictionAlgorithm algorithm;

      public ProcessEvictionRegion(RegionImpl region, EvictionAlgorithm algorithm)
      {
         this.region = region;
         this.algorithm = algorithm;
      }

      public Void call() throws Exception
      {
         try
         {
            algorithm.process(region.getEvictionEventQueue());
         }
         catch (EvictionException e)
         {
            log.error("Eviction exception reported", e);
            fail("Eviction exception reported" + e);
         }

         return null;
      }
   }
}
