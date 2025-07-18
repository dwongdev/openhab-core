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
package org.openhab.core.internal.items;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.common.ThreadPoolManager;
import org.openhab.core.common.registry.RegistryChangeListener;
import org.openhab.core.events.Event;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.events.EventSubscriber;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.items.Metadata;
import org.openhab.core.items.MetadataKey;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.items.events.GroupItemStateChangedEvent;
import org.openhab.core.items.events.ItemCommandEvent;
import org.openhab.core.items.events.ItemEvent;
import org.openhab.core.items.events.ItemEventFactory;
import org.openhab.core.items.events.ItemStateChangedEvent;
import org.openhab.core.items.events.ItemStateEvent;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.Type;
import org.openhab.core.types.TypeParser;
import org.openhab.core.types.UnDefType;
import org.openhab.core.util.DurationUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component which takes care of sending item state expiry events.
 *
 * @author Kai Kreuzer - Initial contribution
 * @author Michael Wyraz - Author of the 1.x expire binding, which this class is based on
 */
@NonNullByDefault
@Component(immediate = true, service = { ExpireManager.class,
        EventSubscriber.class }, configurationPid = "org.openhab.expire", configurationPolicy = ConfigurationPolicy.OPTIONAL)
public class ExpireManager implements EventSubscriber, RegistryChangeListener<Item> {

    protected static final String EVENT_SOURCE = "org.openhab.core.expire";
    protected static final String METADATA_NAMESPACE = "expire";
    protected static final String PROPERTY_ENABLED = "enabled";

    private static final Set<String> SUBSCRIBED_EVENT_TYPES = Set.of(ItemStateEvent.TYPE, ItemStateChangedEvent.TYPE,
            ItemCommandEvent.TYPE, GroupItemStateChangedEvent.TYPE);

    private final Logger logger = LoggerFactory.getLogger(ExpireManager.class);

    private final Map<String, Optional<ExpireConfig>> itemExpireConfig = new ConcurrentHashMap<>();
    private final Map<String, Instant> itemExpireMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService threadPool = ThreadPoolManager
            .getScheduledPool(ThreadPoolManager.THREAD_POOL_NAME_COMMON);

    private final EventPublisher eventPublisher;
    private final MetadataRegistry metadataRegistry;
    private final ItemRegistry itemRegistry;
    /* default */ final MetadataChangeListener metadataChangeListener = new MetadataChangeListener();

    private boolean enabled = true;

    private @Nullable ScheduledFuture<?> expireJob;

    @Activate
    public ExpireManager(Map<String, @Nullable Object> configuration, final @Reference EventPublisher eventPublisher,
            final @Reference MetadataRegistry metadataRegistry, final @Reference ItemRegistry itemRegistry) {
        this.eventPublisher = eventPublisher;
        this.metadataRegistry = metadataRegistry;
        this.itemRegistry = itemRegistry;

        modified(configuration);
    }

    @Modified
    protected void modified(Map<String, @Nullable Object> configuration) {
        Object valueEnabled = configuration.get(PROPERTY_ENABLED);
        if (valueEnabled != null) {
            enabled = Boolean.parseBoolean(valueEnabled.toString());
        }
        if (enabled) {
            ScheduledFuture<?> localExpireJob = expireJob;
            if (localExpireJob == null) {
                expireJob = threadPool.scheduleWithFixedDelay(() -> {
                    if (!itemExpireMap.isEmpty()) {
                        for (String itemName : itemExpireConfig.keySet()) {
                            if (isReadyToExpire(itemName)) {
                                expire(itemName);
                            }
                        }
                    }
                }, 1, 1, TimeUnit.SECONDS);
            }
            itemRegistry.addRegistryChangeListener(this);
            metadataRegistry.addRegistryChangeListener(metadataChangeListener);
        } else {
            deactivate();
        }
    }

    @Deactivate
    protected void deactivate() {
        ScheduledFuture<?> localExpireJob = expireJob;
        if (localExpireJob != null) {
            localExpireJob.cancel(true);
            expireJob = null;
        }
        itemRegistry.removeRegistryChangeListener(this);
        metadataRegistry.removeRegistryChangeListener(metadataChangeListener);
        itemExpireMap.clear();
    }

    private void processEvent(String itemName, Type stateOrCommand, ExpireConfig expireConfig, Class<?> eventClz) {
        logger.trace("Received '{}' for item {}, event type= {}", stateOrCommand, itemName, eventClz.getSimpleName());
        Command expireCommand = expireConfig.expireCommand;
        State expireState = expireConfig.expireState;

        if ((expireCommand != null && expireCommand.equals(stateOrCommand))
                || (expireState != null && expireState.equals(stateOrCommand))) {
            // New event is expired command or state -> no further action needed
            itemExpireMap.remove(itemName); // remove expire trigger until next update or command
            logger.debug("Item {} received '{}'; stopping any future expiration.", itemName, stateOrCommand);
        } else {
            // New event is not the expired command or state, so add the trigger to the map
            Duration duration = expireConfig.duration;
            itemExpireMap.put(itemName, Instant.now().plus(duration));
            logger.debug("Item {} will expire (with '{}' {}) in {} ms", itemName,
                    expireCommand == null ? expireState : expireCommand, expireCommand == null ? "state" : "command",
                    duration);
        }
    }

    private @Nullable ExpireConfig getExpireConfig(String itemName) {
        Optional<ExpireConfig> itemConfig = itemExpireConfig.get(itemName);
        if (itemConfig != null) {
            return itemConfig.orElse(null);
        } else {
            Metadata metadata = metadataRegistry.get(new MetadataKey(METADATA_NAMESPACE, itemName));
            if (metadata != null) {
                try {
                    Item item = itemRegistry.getItem(itemName);
                    try {
                        ExpireConfig cfg = new ExpireConfig(item, metadata.getValue(), metadata.getConfiguration());
                        itemExpireConfig.put(itemName, Optional.of(cfg));
                        return cfg;
                    } catch (IllegalArgumentException e) {
                        logger.warn("Expire config '{}' of item '{}' is invalid: {}", metadata.getValue(), itemName,
                                e.getMessage());
                    }
                } catch (ItemNotFoundException e) {
                    logger.debug("Item '{}' does not exist.", itemName);
                }
            }
            // also fill the map when there is no config, so that we do not retry to find one
            itemExpireConfig.put(itemName, Optional.empty());
            return null;
        }
    }

    private void postCommand(String itemName, Command command) {
        eventPublisher.post(ItemEventFactory.createCommandEvent(itemName, command, EVENT_SOURCE));
    }

    private void postUpdate(String itemName, State state) {
        eventPublisher.post(ItemEventFactory.createStateEvent(itemName, state, EVENT_SOURCE));
    }

    private boolean isReadyToExpire(String itemName) {
        Instant nextExpiry = itemExpireMap.get(itemName);
        return (nextExpiry != null) && Instant.now().isAfter(nextExpiry);
    }

    private void expire(String itemName) {
        itemExpireMap.remove(itemName); // disable expire trigger until next update or command
        Optional<ExpireConfig> expireConfig = itemExpireConfig.get(itemName);

        if (expireConfig != null && expireConfig.isPresent()) {
            Command expireCommand = expireConfig.get().expireCommand;
            State expireState = expireConfig.get().expireState;

            if (expireCommand != null) {
                logger.debug("Item {} received no command or update for {} - posting command '{}'", itemName,
                        expireConfig.get().duration, expireCommand);
                postCommand(itemName, expireCommand);
            } else if (expireState != null) {
                logger.debug("Item {} received no command or update for {} - posting state '{}'", itemName,
                        expireConfig.get().duration, expireState);
                postUpdate(itemName, expireState);
            }
        }
    }

    @Override
    public Set<String> getSubscribedEventTypes() {
        return SUBSCRIBED_EVENT_TYPES;
    }

    @Override
    public void receive(Event event) {
        if (!enabled) {
            return;
        }
        if (!(event instanceof ItemEvent)) {
            return;
        }

        ItemEvent itemEvent = (ItemEvent) event;
        String itemName = itemEvent.getItemName();
        ExpireConfig expireConfig = getExpireConfig(itemName);
        if (expireConfig == null) {
            return;
        }

        if (event instanceof ItemStateEvent isEvent) {
            if (!expireConfig.ignoreStateUpdates) {
                processEvent(itemName, isEvent.getItemState(), expireConfig, event.getClass());
            }
        } else if (event instanceof ItemCommandEvent icEvent) {
            if (!expireConfig.ignoreCommands) {
                processEvent(itemName, icEvent.getItemCommand(), expireConfig, event.getClass());
            }
        } else if (event instanceof ItemStateChangedEvent icEvent) {
            processEvent(itemName, icEvent.getItemState(), expireConfig, event.getClass());
        }
    }

    @Override
    public void added(Item item) {
        itemExpireConfig.remove(item.getName());
    }

    @Override
    public void removed(Item item) {
        itemExpireConfig.remove(item.getName());
    }

    @Override
    public void updated(Item oldItem, Item item) {
        itemExpireConfig.remove(item.getName());
    }

    class MetadataChangeListener implements RegistryChangeListener<Metadata> {

        @Override
        public void added(Metadata element) {
            itemExpireConfig.remove(element.getUID().getItemName());
        }

        @Override
        public void removed(Metadata element) {
            itemExpireConfig.remove(element.getUID().getItemName());
        }

        @Override
        public void updated(Metadata oldElement, Metadata element) {
            itemExpireConfig.remove(element.getUID().getItemName());
        }
    }

    static class ExpireConfig {
        static final String CONFIG_DURATION = "duration";
        static final String CONFIG_COMMAND = "command";
        static final String CONFIG_STATE = "state";
        static final String CONFIG_IGNORE_STATE_UPDATES = "ignoreStateUpdates";
        static final String CONFIG_IGNORE_COMMANDS = "ignoreCommands";
        static final Set<String> CONFIG_KEYS = Set.of(CONFIG_DURATION, CONFIG_COMMAND, CONFIG_STATE,
                CONFIG_IGNORE_STATE_UPDATES, CONFIG_IGNORE_COMMANDS);

        private static final StringType STRING_TYPE_NULL_HYPHEN = new StringType("'NULL'");
        private static final StringType STRING_TYPE_NULL = new StringType("NULL");
        private static final StringType STRING_TYPE_UNDEF_HYPHEN = new StringType("'UNDEF'");
        private static final StringType STRING_TYPE_UNDEF = new StringType("UNDEF");

        protected static final String COMMAND_PREFIX = CONFIG_COMMAND + "=";
        protected static final String STATE_PREFIX = CONFIG_STATE + "=";

        final @Nullable Command expireCommand;
        final @Nullable State expireState;
        final String durationString;
        final Duration duration;
        final boolean ignoreStateUpdates;
        final boolean ignoreCommands;

        /**
         * Construct an ExpireConfig from the config string.
         *
         * Valid syntax:
         *
         * {@code &lt;duration&gt;[,(state=|command=|)&lt;stateOrCommand&gt;]}<br>
         * if neither state= or command= is present, assume state
         *
         * {@code duration} is a string of the form "1d1h15m30s" or "1d" or "1h" or "15m" or "30s",
         * or an ISO-8601 duration string (e.g. "PT1H15M30S").
         *
         * {@code configuration} is a map of configuration keys and values:
         * - {@code duration}: the duration string
         * - {@code command}: the {@link Command} to send when the item expires
         * - {@code state}: the {@link State} to send when the item expires
         * - {@code ignoreStateUpdates}: if true, ignore state updates
         * - {@code ignoreCommands}: if true, ignore commands
         *
         * - When neither command nor state is specified, the default is to post an {@link UNDEF} state.
         *
         * @param item the item to which we are bound
         * @param configString the string that the user specified in the metadata
         * @param configuration the configuration map
         * @throws IllegalArgumentException if it is ill-formatted, or the configuration contains an unknown key,
         *             or any setting is specified more than once
         */
        public ExpireConfig(Item item, String configString, Map<String, Object> configuration)
                throws IllegalArgumentException {
            int commaPos = configString.indexOf(',');
            String commandString = null;
            String stateString = null;

            String durationStr = (commaPos >= 0) ? configString.substring(0, commaPos).trim() : configString.trim();
            if (configuration.containsKey(CONFIG_DURATION)) {
                if (!durationStr.isEmpty()) {
                    throw new IllegalArgumentException("Expire duration for item " + item.getName()
                            + " is specified in both the value string and the configuration");
                }
                durationStr = configuration.get(CONFIG_DURATION).toString();
            }

            durationString = durationStr;
            duration = DurationUtils.parse(durationString);
            if (duration.isNegative()) {
                throw new IllegalArgumentException(
                        "Expire duration for item " + item.getName() + " must be a positive value");
            }

            String stateOrCommand = ((commaPos >= 0) && (configString.length() - 1) > commaPos)
                    ? configString.substring(commaPos + 1).trim()
                    : null;

            ignoreStateUpdates = getBooleanConfigValue(configuration, CONFIG_IGNORE_STATE_UPDATES);
            ignoreCommands = getBooleanConfigValue(configuration, CONFIG_IGNORE_COMMANDS);

            if (configuration.containsKey(CONFIG_COMMAND)) {
                commandString = configuration.get(CONFIG_COMMAND).toString();
            }

            if (configuration.containsKey(CONFIG_STATE)) {
                if (commandString != null) {
                    throw new IllegalArgumentException(
                            "Expire configuration for item " + item.getName() + " contains both command and state");
                }
                stateString = configuration.get(CONFIG_STATE).toString();
            }

            if ((stateOrCommand != null) && (!stateOrCommand.isEmpty())) {
                if (commandString != null || stateString != null) {
                    throw new IllegalArgumentException("Expire state/command for item " + item.getName()
                            + " is specified in both the value string and the configuration");
                }

                if (stateOrCommand.startsWith(COMMAND_PREFIX)) {
                    commandString = stateOrCommand.substring(COMMAND_PREFIX.length());
                } else {
                    if (stateOrCommand.startsWith(STATE_PREFIX)) {
                        stateOrCommand = stateOrCommand.substring(STATE_PREFIX.length());
                    }
                    stateString = stateOrCommand;
                }
            }

            if (commandString != null) {
                expireCommand = TypeParser.parseCommand(item.getAcceptedCommandTypes(), commandString);
                expireState = null;
                if (expireCommand == null) {
                    throw new IllegalArgumentException("The string '" + commandString
                            + "' does not represent a valid command for item " + item.getName());
                }
            } else if (stateString != null) {
                // default is to post state
                expireCommand = null;
                State state = TypeParser.parseState(item.getAcceptedDataTypes(), stateString);
                // do special handling to allow NULL and UNDEF as strings when being put in single quotes
                if (STRING_TYPE_NULL_HYPHEN.equals(state)) {
                    expireState = STRING_TYPE_NULL;
                } else if (STRING_TYPE_UNDEF_HYPHEN.equals(state)) {
                    expireState = STRING_TYPE_UNDEF;
                } else {
                    expireState = state;
                }

                if (expireState == null) {
                    throw new IllegalArgumentException("The string '" + stateString
                            + "' does not represent a valid state for item " + item.getName());
                }
            } else {
                // default is to post Undefined state
                expireCommand = null;
                expireState = UnDefType.UNDEF;
            }

            if (!CONFIG_KEYS.containsAll(configuration.keySet())) {
                Set<String> unknownKeys = new HashSet<String>(configuration.keySet());
                unknownKeys.removeAll(CONFIG_KEYS);
                throw new IllegalArgumentException(
                        "Expire configuration for item " + item.getName() + " contains unknown keys: " + unknownKeys);
            }
        }

        /**
         * Parse configuration value as primitive boolean. Supports parsing of String and Boolean values.
         *
         * @param configuration map of configuration keys and values
         * @param configKey configuration key to lookup configuration map
         * @return configuration value parsed as boolean. Defaults to false when configKey is not present in
         *         configuration
         */
        private boolean getBooleanConfigValue(Map<String, Object> configuration, String configKey) {
            boolean configValue;
            Object configValueObject = configuration.get(configKey);
            if (configValueObject instanceof String string) {
                configValue = Boolean.parseBoolean(string);
            } else if (configValueObject instanceof Boolean boolean1) {
                configValue = boolean1;
            } else {
                configValue = false;
            }
            return configValue;
        }

        @Override
        public String toString() {
            return "duration='" + durationString + "', s=" + duration.toSeconds() + ", state='" + expireState
                    + "', command='" + expireCommand + "', ignoreStateUpdates=" + ignoreStateUpdates
                    + ", ignoreCommands=" + ignoreCommands;
        }
    }
}
