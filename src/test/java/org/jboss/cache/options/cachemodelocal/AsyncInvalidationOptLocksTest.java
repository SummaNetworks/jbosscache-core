/*
 * JBoss, Home of Professional Open Source
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jboss.cache.options.cachemodelocal;

import org.jboss.cache.config.Configuration;
import org.testng.annotations.Test;

@Test (groups = "functional", testName = "options.cachemodelocal.AsyncInvalidationOptLocksTest")
public class AsyncInvalidationOptLocksTest extends CacheModeLocalTestBase
{
    public AsyncInvalidationOptLocksTest()
    {
        cacheMode = Configuration.CacheMode.INVALIDATION_ASYNC;
        nodeLockingScheme = "OPTIMISTIC";
        isInvalidation = true;
    }
}
