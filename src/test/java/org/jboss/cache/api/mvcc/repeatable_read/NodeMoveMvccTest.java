/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.api.mvcc.repeatable_read;

import org.jboss.cache.api.mvcc.NodeMoveMvccTestBase;
import org.jboss.cache.config.Configuration;
import org.jboss.cache.lock.IsolationLevel;
import org.testng.annotations.Test;

@Test(groups = {"functional", "mvcc"}, testName = "api.mvcc.repeatable_read.NodeMoveMvccTest")
public class NodeMoveMvccTest extends NodeMoveMvccTestBase
{
   @Override
   protected void configure(Configuration c)
   {
      c.setIsolationLevel(IsolationLevel.REPEATABLE_READ);
   }
}
