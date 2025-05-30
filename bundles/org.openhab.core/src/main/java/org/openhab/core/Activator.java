/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extension of the default OSGi bundle activator
 *
 * @author Jan N. Klug - Initial contribution
 */
@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
@NonNullByDefault
public final class Activator implements BundleActivator {

    private final Logger logger = LoggerFactory.getLogger(Activator.class);

    @Override
    public void start(@Nullable BundleContext bc) throws Exception {
        logger.info("Starting openHAB {} ({})", OpenHAB.getVersion(), OpenHAB.buildString());
    }

    @Override
    public void stop(@Nullable BundleContext context) throws Exception {
        // do nothing
    }
}
