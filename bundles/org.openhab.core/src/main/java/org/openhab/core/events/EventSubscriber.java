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
package org.openhab.core.events;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link EventSubscriber} defines the callback interface for receiving events from
 * the openHAB event bus.
 *
 * @author Stefan Bußweiler - Initial contribution
 */
@NonNullByDefault
public interface EventSubscriber {

    /**
     * The constant {@link #ALL_EVENT_TYPES} must be returned by the {@link #getSubscribedEventTypes()} method, if the
     * event subscriber should subscribe to all event types.
     */
    String ALL_EVENT_TYPES = "ALL";

    /**
     * Gets the event types to which the event subscriber is subscribed to.
     *
     * @return subscribed event types (not null)
     */
    Set<String> getSubscribedEventTypes();

    /**
     * Gets an {@link EventFilter} in order to receive specific events if the filter applies. If there is no
     * filter all subscribed event types are received.
     *
     * @return the event filter, or null
     */
    default @Nullable EventFilter getEventFilter() {
        return null;
    }

    /**
     * Callback method for receiving {@link Event}s from the openHAB event bus. This method is called for
     * every event where the event subscriber is subscribed to and the event filter applies.
     *
     * @param event the received event (not null)
     */
    void receive(Event event);
}
