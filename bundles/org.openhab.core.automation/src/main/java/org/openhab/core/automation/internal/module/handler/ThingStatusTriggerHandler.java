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
package org.openhab.core.automation.internal.module.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.automation.ModuleHandlerCallback;
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.handler.BaseTriggerModuleHandler;
import org.openhab.core.automation.handler.TriggerHandlerCallback;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventFilter;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.events.TopicEventFilter;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.events.ThingStatusInfoChangedEvent;
import org.openhab.core.thing.events.ThingStatusInfoEvent;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a ModuleHandler implementation for Triggers which trigger the rule if a thing status event occurs. The
 * eventType and status value can be set in the configuration.
 *
 * @author Christoph Weitkamp - Initial contribution
 */
@NonNullByDefault
public class ThingStatusTriggerHandler extends BaseTriggerModuleHandler implements EventSubscriber {

    public static final String UPDATE_MODULE_TYPE_ID = "core.ThingStatusUpdateTrigger";
    public static final String CHANGE_MODULE_TYPE_ID = "core.ThingStatusChangeTrigger";

    public static final String CFG_THING_UID = "thingUID";
    public static final String CFG_STATUS = "status";
    public static final String CFG_PREVIOUS_STATUS = "previousStatus";

    public static final String OUT_STATUS = "status";
    public static final String OUT_NEW_STATUS = "newStatus";
    public static final String OUT_OLD_STATUS = "oldStatus";
    public static final String OUT_EVENT = "event";

    private final Logger logger = LoggerFactory.getLogger(ThingStatusTriggerHandler.class);

    private @Nullable final String status;
    private @Nullable final String previousStatus;
    private final Set<String> types;

    private final ServiceRegistration<?> eventSubscriberRegistration;
    private final TopicEventFilter eventTopicFilter;

    public ThingStatusTriggerHandler(Trigger module, BundleContext bundleContext) {
        super(module);
        String thingUID = (String) module.getConfiguration().get(CFG_THING_UID);
        this.status = (String) module.getConfiguration().get(CFG_STATUS);
        this.previousStatus = (String) module.getConfiguration().get(CFG_PREVIOUS_STATUS);
        if (UPDATE_MODULE_TYPE_ID.equals(module.getTypeUID())) {
            this.types = Set.of(ThingStatusInfoEvent.TYPE);
        } else {
            this.types = Set.of(ThingStatusInfoChangedEvent.TYPE);
        }

        this.eventTopicFilter = new TopicEventFilter(
                "^openhab/things/" + thingUID.replace("?", ".?").replace("*", ".*?") + "/.*$");

        eventSubscriberRegistration = bundleContext.registerService(EventSubscriber.class.getName(), this, null);
    }

    @Override
    public Set<String> getSubscribedEventTypes() {
        return types;
    }

    @Override
    public @Nullable EventFilter getEventFilter() {
        return eventTopicFilter;
    }

    @Override
    public void receive(Event event) {
        final ModuleHandlerCallback callback = this.callback;
        if (!(callback instanceof TriggerHandlerCallback thCallback)) {
            return;
        }

        logger.trace("Received Event: Source: {} Topic: {} Type: {}  Payload: {}", event.getSource(), event.getTopic(),
                event.getType(), event.getPayload());
        Map<String, Object> values = new HashMap<>();
        if (event instanceof ThingStatusInfoEvent infoEvent && UPDATE_MODULE_TYPE_ID.equals(module.getTypeUID())) {
            ThingStatus status = infoEvent.getStatusInfo().getStatus();
            if (statusMatches(this.status, status)) {
                values.put(OUT_STATUS, status);
            }
        } else if (event instanceof ThingStatusInfoChangedEvent changedEvent
                && CHANGE_MODULE_TYPE_ID.equals(module.getTypeUID())) {
            ThingStatus newStatus = changedEvent.getStatusInfo().getStatus();
            ThingStatus oldStatus = changedEvent.getOldStatusInfo().getStatus();
            if (statusMatches(this.status, newStatus) && statusMatches(this.previousStatus, oldStatus)) {
                values.put(OUT_NEW_STATUS, newStatus);
                values.put(OUT_OLD_STATUS, oldStatus);
            }
        }
        if (!values.isEmpty()) {
            values.put(OUT_EVENT, event);
            thCallback.triggered(module, values);
        }
    }

    private boolean statusMatches(@Nullable String requiredStatus, ThingStatus status) {
        if (requiredStatus == null) {
            return true;
        }
        String reqStatus = requiredStatus.trim();
        return reqStatus.isEmpty() || reqStatus.equals(status.toString());
    }

    /**
     * do the cleanup: unregistering eventSubscriber...
     */
    @Override
    public void dispose() {
        eventSubscriberRegistration.unregister();
        super.dispose();
    }
}
