/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2000 - 2008, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.cache.notifications.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation should be used on methods that need to be notified when the cache is called to participate in a transaction and
 * the transaction completes, either with a commit or a rollback.
 * <p/>
 * Methods annotated with this annotation should accept a single
 * parameter, a {@link org.jboss.cache.notifications.event.TransactionCompletedEvent} otherwise a {@link org.jboss.cache.notifications.IncorrectCacheListenerException}
 * will be thrown when registering your listener.
 * <p/>
 * Note that methods marked with this annotation will only be fired <i>after the fact</i>, i.e., your method will never be
 * called with {@link org.jboss.cache.notifications.event.Event#isPre()} being set to <tt>true</tt>.
 *
 * @author <a href="mailto:manik AT jboss DOT org">Manik Surtani</a>
 * @see CacheListener
 * @since 2.0.0
 */
// ensure this annotation is available at runtime.
@Retention(RetentionPolicy.RUNTIME)
// ensure that this annotation is applied to classes.
@Target(ElementType.METHOD)
public @interface TransactionCompleted
{
}
