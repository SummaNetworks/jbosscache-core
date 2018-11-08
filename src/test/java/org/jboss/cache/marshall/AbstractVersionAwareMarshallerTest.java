package org.jboss.cache.marshall;

import org.jboss.cache.CacheStatus;
import org.jboss.cache.RegionManager;
import org.jboss.cache.RegionManagerImpl;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.factories.ComponentRegistry;

/**
 * @author Manik Surtani (<a href="mailto:manik AT jboss DOT org">manik AT jboss DOT org</a>)
 * @since 2.1.0
 */
public abstract class AbstractVersionAwareMarshallerTest
{
   protected ComponentRegistry cr = null;

   protected VersionAwareMarshaller createVAMandRestartCache(String replVersion)
   {
      ComponentRegistry cr = this.cr;
      Configuration c = cr.getComponent(Configuration.class);
      c.setReplVersionString(replVersion);
      return createVAMandRestartCache(new RegionManagerImpl());
   }

   protected VersionAwareMarshaller createVAMandRestartCache(RegionManager rm)
   {
      ComponentRegistry cr = this.cr;
      if (cr.getState() == CacheStatus.STARTED) cr.stop();
      cr.registerComponent(rm, RegionManager.class);
      cr.create();
      cr.rewire();
      // force cache mode
      VersionAwareMarshaller m = (VersionAwareMarshaller) cr.getComponent(Marshaller.class);
      m.init();
      m.initReplicationVersions();
      return m;
   }
}
